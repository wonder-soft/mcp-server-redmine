package com.wonder_soft.mcp.redmine.adapter

import io.circe.syntax.*
import io.circe.parser.*
import com.sun.net.httpserver.{HttpServer, HttpHandler, HttpExchange}
import java.net.InetSocketAddress
import java.io.{InputStream, OutputStream}
import scala.io.Source
import java.util.UUID
import scala.collection.mutable
import java.util.concurrent.{ConcurrentHashMap, LinkedBlockingQueue, TimeUnit}

import com.wonder_soft.mcp.redmine.domain.*
import com.wonder_soft.mcp.redmine.domain.McpModels.*
import com.wonder_soft.mcp.redmine.usecase.RedmineUsecase
import com.wonder_soft.mcp.redmine.util.Logger

// Session data: stores the response queue for sending responses via SSE
case class McpSession(
  responseQueue: LinkedBlockingQueue[JsonRpcResponse],
  createdAt: Long,
  var active: Boolean = true
)

class McpServerAdapter extends HttpHandler {
  private val redmineUsecase = new RedmineUsecase()
  // Sessions map: sessionId -> McpSession with response queue
  private val sessions = new ConcurrentHashMap[String, McpSession]()

  private def setCorsHeaders(exchange: HttpExchange): Unit = {
    val headers = exchange.getResponseHeaders
    headers.set("Access-Control-Allow-Origin", "*")
    headers.set("Access-Control-Allow-Methods", "GET, POST, DELETE, OPTIONS")
    headers.set("Access-Control-Allow-Headers", "Content-Type, Accept, Authorization, Mcp-Session-Id")
    headers.set("Access-Control-Expose-Headers", "Mcp-Session-Id")
    headers.set("Access-Control-Max-Age", "86400")
  }

  // Parse query parameters from URI
  private def parseQueryParams(query: String): Map[String, String] = {
    Option(query).map { q =>
      q.split("&").flatMap { param =>
        param.split("=", 2) match {
          case Array(key, value) => Some(key -> java.net.URLDecoder.decode(value, "UTF-8"))
          case Array(key) => Some(key -> "")
          case _ => None
        }
      }.toMap
    }.getOrElse(Map.empty)
  }

  def handle(exchange: HttpExchange): Unit = {
    val method = exchange.getRequestMethod
    val uri = exchange.getRequestURI
    val path = uri.getPath
    val query = uri.getQuery
    val queryParams = parseQueryParams(query)
    val acceptHeader = Option(exchange.getRequestHeaders.getFirst("Accept")).getOrElse("")

    Logger.debug(s"$method $path?$query (Accept: $acceptHeader)")

    try {
      setCorsHeaders(exchange)

      val isSsePath = path == "/sse"
      val isMessagePath = path == "/mcp" || path == "/message"

      if (method == "OPTIONS") {
        exchange.sendResponseHeaders(204, -1)
        exchange.getResponseBody.close()
      } else if (method == "GET" && isSsePath) {
        // SSE endpoint - establishes connection and sends endpoint event
        handleSseConnection(exchange)
      } else if (method == "POST" && isMessagePath) {
        // Message endpoint - receives JSON-RPC requests, sends response via SSE
        val sessionId = queryParams.get("sessionId")
        handlePostRequest(exchange, sessionId)
      } else if (method == "DELETE" && (isSsePath || isMessagePath)) {
        // Session termination
        handleSessionDelete(exchange)
      } else {
        exchange.sendResponseHeaders(404, 0)
        exchange.getResponseBody.close()
      }
    } catch {
      case e: Exception =>
        Logger.error("Request handling failed", e)
        try {
          val errorResponse = JsonRpcResponse(
            id = None,
            error = Some(JsonRpcError(-32603, s"Internal error: ${e.getMessage}"))
          )
          sendJsonResponse(exchange, errorResponse)
        } catch {
          case _: Exception => // ignore
        }
    }
  }

  private def handleSseConnection(exchange: HttpExchange): Unit = {
    val sessionId = UUID.randomUUID().toString
    val responseQueue = new LinkedBlockingQueue[JsonRpcResponse](100)
    val session = McpSession(responseQueue, System.currentTimeMillis())
    sessions.put(sessionId, session)

    val headers = exchange.getResponseHeaders
    headers.set("Content-Type", "text/event-stream")
    headers.set("Cache-Control", "no-cache, no-store, must-revalidate")
    headers.set("Connection", "keep-alive")
    headers.set("Mcp-Session-Id", sessionId)

    try {
      exchange.sendResponseHeaders(200, 0)
      val outputStream = exchange.getResponseBody

      // Send initial endpoint event with POST URL (including sessionId)
      val host = Option(exchange.getRequestHeaders.getFirst("Host")).getOrElse("localhost:8080")
      val endpointUrl = s"http://$host/message?sessionId=$sessionId"
      val endpointEvent = s"event: endpoint\ndata: $endpointUrl\n\n"
      outputStream.write(endpointEvent.getBytes("UTF-8"))
      outputStream.flush()

      Logger.info(s"SSE connection established: $sessionId, endpoint: $endpointUrl")

      // Main loop: poll response queue and send responses via SSE
      var lastHeartbeat = System.currentTimeMillis()
      try {
        while (session.active) {
          // Poll with timeout to allow periodic heartbeats
          val response = responseQueue.poll(5, TimeUnit.SECONDS)

          if (response != null) {
            // Send response as SSE message event
            val responseJson = response.asJson.noSpaces
            val sseEvent = s"event: message\ndata: $responseJson\n\n"
            outputStream.write(sseEvent.getBytes("UTF-8"))
            outputStream.flush()
            Logger.debug(s"SSE response sent: $responseJson")
          }

          // Send heartbeat every 15 seconds
          val now = System.currentTimeMillis()
          if (now - lastHeartbeat > 15000) {
            outputStream.write(": heartbeat\n\n".getBytes("UTF-8"))
            outputStream.flush()
            lastHeartbeat = now
          }
        }
      } catch {
        case e: java.io.IOException =>
          Logger.info(s"SSE connection closed: ${e.getMessage}")
        case _: InterruptedException =>
          Logger.info(s"SSE connection interrupted: $sessionId")
      } finally {
        session.active = false
        sessions.remove(sessionId)
        try { outputStream.close() } catch { case _: Exception => }
        Logger.info(s"Session removed: $sessionId")
      }
    } catch {
      case e: java.io.IOException =>
        Logger.error(s"SSE connection failed: ${e.getMessage}")
        sessions.remove(sessionId)
    }
  }

  private def handlePostRequest(exchange: HttpExchange, sessionIdOpt: Option[String]): Unit = {
    val inputStream = exchange.getRequestBody
    val body = Source.fromInputStream(inputStream).mkString
    inputStream.close()

    Logger.debug(s"Request body: $body (sessionId: ${sessionIdOpt.getOrElse("none")})")

    sessionIdOpt match {
      case Some(sessionId) =>
        // SSE transport: send response via SSE channel, return 202 Accepted
        val sessionOpt = Option(sessions.get(sessionId))
        sessionOpt match {
          case Some(session) if session.active =>
            val responseOpt = handleJsonRpcRequest(body)
            responseOpt.foreach { response =>
              // Put response in the session's queue for SSE delivery
              if (!session.responseQueue.offer(response, 5, TimeUnit.SECONDS)) {
                Logger.warn(s"Response queue full for session $sessionId")
              }
            }
            // Return 202 Accepted immediately (response sent via SSE)
            exchange.sendResponseHeaders(202, -1)
            exchange.getResponseBody.close()
            Logger.debug("Request accepted, response queued for SSE delivery")

          case _ =>
            // Session not found or inactive
            Logger.warn(s"Session not found: $sessionId")
            exchange.sendResponseHeaders(404, 0)
            val out = exchange.getResponseBody
            out.write("Session not found".getBytes("UTF-8"))
            out.close()
        }

      case None =>
        // No sessionId - use direct JSON response (for simple HTTP clients like curl)
        val responseOpt = handleJsonRpcRequest(body)
        responseOpt match {
          case Some(response) => sendJsonResponse(exchange, response)
          case None =>
            // Notification - no response needed
            exchange.sendResponseHeaders(202, -1)
            exchange.getResponseBody.close()
            Logger.debug("Notification acknowledged (no response)")
        }
    }
  }

  private def handleSessionDelete(exchange: HttpExchange): Unit = {
    val sessionId = Option(exchange.getRequestHeaders.getFirst("Mcp-Session-Id"))
    sessionId.foreach { id =>
      val session = sessions.get(id)
      if (session != null) {
        session.active = false
        sessions.remove(id)
        Logger.info(s"Session deleted: $id")
      }
    }
    exchange.sendResponseHeaders(204, -1)
    exchange.getResponseBody.close()
  }

  private def sendJsonResponse(exchange: HttpExchange, response: JsonRpcResponse): Unit = {
    val headers = exchange.getResponseHeaders
    headers.set("Content-Type", "application/json")

    val responseJson = response.asJson.noSpaces
    val bytes = responseJson.getBytes("UTF-8")

    Logger.debug(s"Response (JSON): $responseJson")

    try {
      exchange.sendResponseHeaders(200, bytes.length)
      val outputStream = exchange.getResponseBody
      outputStream.write(bytes)
      outputStream.flush()
      outputStream.close()
    } catch {
      case e: java.io.IOException =>
        Logger.warn(s"JSON response failed (client disconnected): ${e.getMessage}")
    }
  }

  private def handleJsonRpcRequest(body: String): Option[JsonRpcResponse] = {
    decode[JsonRpcRequest](body) match {
      case Right(request) => processRequest(request)
      case Left(error) => Some(JsonRpcResponse(
        id = None,
        error = Some(JsonRpcError(-32700, s"Parse error: ${error.getMessage}"))
      ))
    }
  }

  private def processRequest(request: JsonRpcRequest): Option[JsonRpcResponse] = {
    // Check if this is a notification (no id) - notifications don't expect responses
    val isNotification = request.id.isEmpty

    request.method match {
      case "tools/list" => Some(listTools(request.id))
      case "tools/call" => Some(callTool(request.id, request.params))
      case "initialize" => Some(initialize(request.id))
      case "notifications/initialized" =>
        Logger.debug("Client initialized notification received")
        None // Notifications don't get responses
      case method if method.startsWith("notifications/") =>
        Logger.debug(s"Notification received: $method")
        None // All notifications don't get responses
      case _ if isNotification =>
        Logger.debug(s"Unknown notification: ${request.method}")
        None // Unknown notifications are silently ignored
      case _ => Some(JsonRpcResponse(
        id = request.id,
        error = Some(JsonRpcError(-32601, s"Method not found: ${request.method}"))
      ))
    }
  }

  private def initialize(id: JsonRpcId): JsonRpcResponse = {
    val result = Map(
      "protocolVersion" -> "2025-06-18".asJson,
      "capabilities" -> Map(
        "tools" -> Map[String, String]().asJson
      ).asJson,
      "serverInfo" -> Map(
        "name" -> "redmine-mcp-server".asJson,
        "version" -> "1.0.0".asJson
      ).asJson
    ).asJson

    JsonRpcResponse(id = id, result = Some(result))
  }

  private def listTools(id: JsonRpcId): JsonRpcResponse = {
    val tools = List(
      McpTool(
        name = "get_redmine_ticket",
        description = "Get Redmine ticket information",
        inputSchema = Map(
          "type" -> "object".asJson,
          "properties" -> Map(
            "ticketId" -> Map(
              "type" -> "number".asJson,
              "description" -> "Ticket ID".asJson
            ).asJson
          ).asJson,
          "required" -> List("ticketId").asJson
        ).asJson
      ),
      McpTool(
        name = "create_redmine_ticket",
        description = "Create a new Redmine ticket",
        inputSchema = Map(
          "type" -> "object".asJson,
          "properties" -> Map(
            "subject" -> Map("type" -> "string".asJson, "description" -> "Ticket title".asJson).asJson,
            "description" -> Map("type" -> "string".asJson, "description" -> "Ticket description".asJson).asJson,
            "parentTicketId" -> Map("type" -> "number".asJson, "description" -> "Parent ticket ID".asJson).asJson,
            "assignedToId" -> Map("type" -> "number".asJson, "description" -> "Assignee user ID".asJson).asJson,
            "relatedTicketIds" -> Map("type" -> "array".asJson, "items" -> Map("type" -> "number".asJson).asJson, "description" -> "Related ticket IDs".asJson).asJson
          ).asJson,
          "required" -> List("subject").asJson
        ).asJson
      ),
      McpTool(
        name = "update_redmine_ticket",
        description = "Update a Redmine ticket",
        inputSchema = Map(
          "type" -> "object".asJson,
          "properties" -> Map(
            "id" -> Map("type" -> "number".asJson, "description" -> "Ticket ID".asJson).asJson,
            "subject" -> Map("type" -> "string".asJson, "description" -> "Ticket title".asJson).asJson,
            "description" -> Map("type" -> "string".asJson, "description" -> "Ticket description".asJson).asJson,
            "statusId" -> Map("type" -> "number".asJson, "description" -> "Status ID".asJson).asJson,
            "assignedToId" -> Map("type" -> "number".asJson, "description" -> "Assignee user ID".asJson).asJson,
            "clearAssignee" -> Map("type" -> "boolean".asJson, "description" -> "Set to true to clear the assignee (default: false)".asJson).asJson,
            "relatedTicketIds" -> Map("type" -> "array".asJson, "items" -> Map("type" -> "number".asJson).asJson, "description" -> "Related ticket IDs".asJson).asJson,
            "dueDate" -> Map("type" -> "string".asJson, "description" -> "Due date (YYYY-MM-DD format)".asJson).asJson,
            "trackerId" -> Map("type" -> "number".asJson, "description" -> "Tracker ID".asJson).asJson
          ).asJson,
          "required" -> List("id").asJson
        ).asJson
      ),
      McpTool(
        name = "get_redmine_child_tickets",
        description = "Get child tickets of a Redmine parent ticket",
        inputSchema = Map(
          "type" -> "object".asJson,
          "properties" -> Map(
            "parentId" -> Map("type" -> "number".asJson, "description" -> "Parent ticket ID".asJson).asJson,
            "assignedToId" -> Map("type" -> "number".asJson, "description" -> "Filter by assignee user ID".asJson).asJson,
            "limit" -> Map("type" -> "number".asJson, "description" -> "Maximum number of results (default: 100)".asJson).asJson,
            "offset" -> Map("type" -> "number".asJson, "description" -> "Offset for pagination (default: 0)".asJson).asJson
          ).asJson,
          "required" -> List("parentId").asJson
        ).asJson
      ),
      McpTool(
        name = "search_redmine_tickets",
        description = "Search Redmine tickets by title",
        inputSchema = Map(
          "type" -> "object".asJson,
          "properties" -> Map(
            "query" -> Map("type" -> "string".asJson, "description" -> "Search query".asJson).asJson,
            "limit" -> Map("type" -> "number".asJson, "description" -> "Maximum number of results (default: 100)".asJson).asJson,
            "offset" -> Map("type" -> "number".asJson, "description" -> "Offset for pagination (default: 0)".asJson).asJson
          ).asJson,
          "required" -> List("query").asJson
        ).asJson
      ),
      McpTool(
        name = "add_redmine_comment",
        description = "Add a comment to a Redmine ticket",
        inputSchema = Map(
          "type" -> "object".asJson,
          "properties" -> Map(
            "ticketId" -> Map("type" -> "number".asJson, "description" -> "Ticket ID".asJson).asJson,
            "comment" -> Map("type" -> "string".asJson, "description" -> "Comment content".asJson).asJson,
            "isPrivate" -> Map("type" -> "boolean".asJson, "description" -> "Whether the comment is private (default: false)".asJson).asJson
          ).asJson,
          "required" -> List("ticketId", "comment").asJson
        ).asJson
      ),
      McpTool(
        name = "change_redmine_ticket_status",
        description = "Change the status of a Redmine ticket",
        inputSchema = Map(
          "type" -> "object".asJson,
          "properties" -> Map(
            "ticketId" -> Map("type" -> "number".asJson, "description" -> "Ticket ID".asJson).asJson,
            "statusId" -> Map("type" -> "number".asJson, "description" -> "New status ID".asJson).asJson
          ).asJson,
          "required" -> List("ticketId", "statusId").asJson
        ).asJson
      ),
      McpTool(
        name = "change_bulk_redmine_ticket_status",
        description = "Change the status of multiple Redmine tickets at once",
        inputSchema = Map(
          "type" -> "object".asJson,
          "properties" -> Map(
            "ticketIds" -> Map("type" -> "array".asJson, "items" -> Map("type" -> "number".asJson).asJson, "description" -> "List of ticket IDs".asJson).asJson,
            "statusId" -> Map("type" -> "number".asJson, "description" -> "New status ID".asJson).asJson
          ).asJson,
          "required" -> List("ticketIds", "statusId").asJson
        ).asJson
      ),
      McpTool(
        name = "change_redmine_ticket_tracker",
        description = "Change the tracker of a Redmine ticket",
        inputSchema = Map(
          "type" -> "object".asJson,
          "properties" -> Map(
            "ticketId" -> Map("type" -> "number".asJson, "description" -> "Ticket ID".asJson).asJson,
            "trackerId" -> Map("type" -> "number".asJson, "description" -> "New tracker ID".asJson).asJson
          ).asJson,
          "required" -> List("ticketId", "trackerId").asJson
        ).asJson
      ),
      McpTool(
        name = "get_redmine_users",
        description = "Search Redmine users",
        inputSchema = Map(
          "type" -> "object".asJson,
          "properties" -> Map(
            "name" -> Map("type" -> "string".asJson, "description" -> "Search by user name (partial match)".asJson).asJson,
            "limit" -> Map("type" -> "number".asJson, "description" -> "Maximum number of results (default: 100)".asJson).asJson,
            "offset" -> Map("type" -> "number".asJson, "description" -> "Offset for pagination (default: 0)".asJson).asJson
          ).asJson,
          "required" -> List[String]().asJson
        ).asJson
      ),
      McpTool(
        name = "get_redmine_comments",
        description = "Get comments (journals) of a Redmine ticket",
        inputSchema = Map(
          "type" -> "object".asJson,
          "properties" -> Map(
            "ticketId" -> Map("type" -> "number".asJson, "description" -> "Ticket ID".asJson).asJson
          ).asJson,
          "required" -> List("ticketId").asJson
        ).asJson
      ),
      McpTool(
        name = "get_redmine_relations",
        description = "Get related tickets of a Redmine ticket",
        inputSchema = Map(
          "type" -> "object".asJson,
          "properties" -> Map(
            "ticketId" -> Map("type" -> "number".asJson, "description" -> "Ticket ID".asJson).asJson
          ).asJson,
          "required" -> List("ticketId").asJson
        ).asJson
      )
    )

    JsonRpcResponse(id = id, result = Some(Map("tools" -> tools).asJson))
  }

  private def callTool(id: JsonRpcId, params: Option[io.circe.Json]): JsonRpcResponse = {
    params.flatMap(_.hcursor.downField("name").as[String].toOption) match {
      case Some("get_redmine_ticket") => handleGetTicket(id, params)
      case Some("create_redmine_ticket") => handleCreateTicket(id, params)
      case Some("update_redmine_ticket") => handleUpdateTicket(id, params)
      case Some("get_redmine_child_tickets") => handleGetChildTickets(id, params)
      case Some("search_redmine_tickets") => handleSearchTickets(id, params)
      case Some("add_redmine_comment") => handleAddComment(id, params)
      case Some("change_redmine_ticket_status") => handleChangeTicketStatus(id, params)
      case Some("change_bulk_redmine_ticket_status") => handleChangeBulkTicketStatus(id, params)
      case Some("change_redmine_ticket_tracker") => handleChangeTicketTracker(id, params)
      case Some("get_redmine_users") => handleGetUsers(id, params)
      case Some("get_redmine_comments") => handleGetComments(id, params)
      case Some("get_redmine_relations") => handleGetRelations(id, params)
      case _ => JsonRpcResponse(
        id = id,
        error = Some(JsonRpcError(-32602, "Invalid tool name"))
      )
    }
  }

  private def handleGetTicket(id: JsonRpcId, params: Option[io.circe.Json]): JsonRpcResponse = {
    val ticketIdOpt = params.flatMap(_.hcursor.downField("arguments").downField("ticketId").as[Long].toOption)

    ticketIdOpt match {
      case Some(ticketId) =>
        redmineUsecase.adapter.getTicket(ticketId) match {
          case Right(ticket) =>
            val assigneeInfo = ticket.assignee.map(a => s"Assignee: ${a.name} (ID: ${a.id})").getOrElse("Assignee: Not assigned")
            val trackerInfo = ticket.tracker.map(t => s"Tracker: ${t.name} (ID: ${t.id})").getOrElse("Tracker: Unknown")
            JsonRpcResponse(
              id = id,
              result = Some(Map(
                "content" -> List(Map(
                  "type" -> "text".asJson,
                  "text" -> s"Ticket #${ticket.id}: ${ticket.title}\n${trackerInfo}\n${assigneeInfo}\nDescription: ${ticket.description.getOrElse("None")}".asJson
                )).asJson
              ).asJson)
            )
          case Left(error) => JsonRpcResponse(
            id = id,
            error = Some(JsonRpcError(-32603, s"Redmine API error: $error"))
          )
        }
      case None => JsonRpcResponse(
        id = id,
        error = Some(JsonRpcError(-32602, "Missing ticketId parameter"))
      )
    }
  }

  private def handleCreateTicket(id: JsonRpcId, params: Option[io.circe.Json]): JsonRpcResponse = {
    val cursor = params.map(_.hcursor.downField("arguments")).getOrElse(io.circe.HCursor.fromJson(io.circe.Json.Null))

    val subject = cursor.downField("subject").as[String].toOption
    val description = cursor.downField("description").as[String].toOption
    val parentTicketId = cursor.downField("parentTicketId").as[Long].toOption
    val assignedToId = cursor.downField("assignedToId").as[Long].toOption
    val relatedTicketIds = cursor.downField("relatedTicketIds").as[List[Long]].toOption

    subject match {
      case Some(subjectValue) =>
        redmineUsecase.createTicket(subjectValue, description, parentTicketId, assignedToId, relatedTicketIds) match {
          case Right(createdTicket) => JsonRpcResponse(
            id = id,
            result = Some(Map(
              "content" -> List(Map(
                "type" -> "text".asJson,
                "text" -> s"Ticket created successfully: #${createdTicket.id} - ${createdTicket.subject}".asJson
              )).asJson
            ).asJson)
          )
          case Left(error) => JsonRpcResponse(
            id = id,
            error = Some(JsonRpcError(-32603, s"Ticket creation failed: $error"))
          )
        }
      case None => JsonRpcResponse(
        id = id,
        error = Some(JsonRpcError(-32602, "Missing subject parameter"))
      )
    }
  }

  private def handleUpdateTicket(id: JsonRpcId, params: Option[io.circe.Json]): JsonRpcResponse = {
    val cursor = params.map(_.hcursor.downField("arguments")).getOrElse(io.circe.HCursor.fromJson(io.circe.Json.Null))

    val ticketId = cursor.downField("id").as[Long].toOption
    val subject = cursor.downField("subject").as[String].toOption
    val description = cursor.downField("description").as[String].toOption
    val statusId = cursor.downField("statusId").as[Long].toOption
    val assignedToId = cursor.downField("assignedToId").as[Long].toOption
    val clearAssignee = cursor.downField("clearAssignee").as[Boolean].toOption.getOrElse(false)
    val relatedTicketIds = cursor.downField("relatedTicketIds").as[List[Long]].toOption
    val dueDate = cursor.downField("dueDate").as[String].toOption
    val trackerId = cursor.downField("trackerId").as[Long].toOption

    ticketId match {
      case Some(ticketIdValue) =>
        redmineUsecase.updateTicket(ticketIdValue, subject, description, statusId, assignedToId, relatedTicketIds, None, dueDate, clearAssignee, trackerId) match {
          case Right(updatedTicket) => JsonRpcResponse(
            id = id,
            result = Some(Map(
              "content" -> List(Map(
                "type" -> "text".asJson,
                "text" -> s"Ticket updated successfully: #${updatedTicket.id} - ${updatedTicket.title}".asJson
              )).asJson
            ).asJson)
          )
          case Left(error) => JsonRpcResponse(
            id = id,
            error = Some(JsonRpcError(-32603, s"Ticket update failed: $error"))
          )
        }
      case None => JsonRpcResponse(
        id = id,
        error = Some(JsonRpcError(-32602, "Missing id parameter"))
      )
    }
  }

  private def handleGetChildTickets(id: JsonRpcId, params: Option[io.circe.Json]): JsonRpcResponse = {
    val cursor = params.map(_.hcursor.downField("arguments")).getOrElse(io.circe.HCursor.fromJson(io.circe.Json.Null))

    val parentId = cursor.downField("parentId").as[Long].toOption
    val assignedToId = cursor.downField("assignedToId").as[Long].toOption
    val limit = cursor.downField("limit").as[Int].toOption.getOrElse(100)
    val offset = cursor.downField("offset").as[Int].toOption.getOrElse(0)

    parentId match {
      case Some(parentIdValue) =>
        redmineUsecase.adapter.getChildTickets(parentIdValue, limit, offset, assignedToId) match {
          case Right(response) =>
            val assigneeInfo = assignedToId.map(id => s" (Assignee ID: $id)").getOrElse("")
            val formattedOutput = if (response.issues.isEmpty) {
              s"No child tickets found for parent ticket #${parentIdValue}${assigneeInfo}.\nTotal: ${response.total_count}, Offset: ${response.offset}, Limit: ${response.limit}"
            } else {
              val ticketList = response.issues.map { issue =>
                val trackerStr = issue.tracker.map(t => s" {${t.name}}").getOrElse("")
                s"#${issue.id}: ${issue.subject.getOrElse("No title")} (${issue.status.name})${trackerStr} [${issue.project.name}]"
              }.mkString("\n")

              s"Child tickets of parent #${parentIdValue}${assigneeInfo} (${response.issues.length} of ${response.total_count}, offset: ${response.offset}):\n$ticketList"
            }

            JsonRpcResponse(
              id = id,
              result = Some(Map(
                "content" -> List(Map(
                  "type" -> "text".asJson,
                  "text" -> formattedOutput.asJson
                )).asJson
              ).asJson)
            )
          case Left(error) => JsonRpcResponse(
            id = id,
            error = Some(JsonRpcError(-32603, s"Child tickets retrieval failed: $error"))
          )
        }
      case None => JsonRpcResponse(
        id = id,
        error = Some(JsonRpcError(-32602, "Missing parentId parameter"))
      )
    }
  }

  private def handleSearchTickets(id: JsonRpcId, params: Option[io.circe.Json]): JsonRpcResponse = {
    val cursor = params.map(_.hcursor.downField("arguments")).getOrElse(io.circe.HCursor.fromJson(io.circe.Json.Null))

    val query = cursor.downField("query").as[String].toOption
    val limit = cursor.downField("limit").as[Int].toOption.getOrElse(100)
    val offset = cursor.downField("offset").as[Int].toOption.getOrElse(0)

    query match {
      case Some(queryValue) if queryValue.trim.nonEmpty =>
        redmineUsecase.adapter.searchTicketsByTitle(queryValue, limit, offset) match {
          case Right(response) =>
            val formattedOutput = if (response.results.isEmpty) {
              s"No tickets found containing '$queryValue'.\nTotal: ${response.total_count}, Offset: ${response.offset}, Limit: ${response.limit}"
            } else {
              val ticketList = response.results.map { result =>
                s"#${result.id}: ${result.title} (${result.`type`})"
              }.mkString("\n")

              s"Search results for '$queryValue' (${response.results.length} of ${response.total_count}, offset: ${response.offset}):\n$ticketList"
            }

            JsonRpcResponse(
              id = id,
              result = Some(Map(
                "content" -> List(Map(
                  "type" -> "text".asJson,
                  "text" -> formattedOutput.asJson
                )).asJson
              ).asJson)
            )
          case Left(error) => JsonRpcResponse(
            id = id,
            error = Some(JsonRpcError(-32603, s"Search failed: $error"))
          )
        }
      case _ => JsonRpcResponse(
        id = id,
        error = Some(JsonRpcError(-32602, "Missing or empty query parameter"))
      )
    }
  }

  private def handleAddComment(id: JsonRpcId, params: Option[io.circe.Json]): JsonRpcResponse = {
    val cursor = params.map(_.hcursor.downField("arguments")).getOrElse(io.circe.HCursor.fromJson(io.circe.Json.Null))

    val ticketId = cursor.downField("ticketId").as[Long].toOption
    val comment = cursor.downField("comment").as[String].toOption
    val isPrivate = cursor.downField("isPrivate").as[Boolean].toOption.getOrElse(false)

    (ticketId, comment) match {
      case (Some(ticketIdValue), Some(commentValue)) =>
        redmineUsecase.addComment(ticketIdValue, commentValue, isPrivate) match {
          case Right(message) => JsonRpcResponse(
            id = id,
            result = Some(Map(
              "content" -> List(Map(
                "type" -> "text".asJson,
                "text" -> message.asJson
              )).asJson
            ).asJson)
          )
          case Left(error) => JsonRpcResponse(
            id = id,
            error = Some(JsonRpcError(-32603, s"Comment addition failed: $error"))
          )
        }
      case _ => JsonRpcResponse(
        id = id,
        error = Some(JsonRpcError(-32602, "Missing ticketId or comment parameter"))
      )
    }
  }

  private def handleChangeTicketStatus(id: JsonRpcId, params: Option[io.circe.Json]): JsonRpcResponse = {
    val cursor = params.map(_.hcursor.downField("arguments")).getOrElse(io.circe.HCursor.fromJson(io.circe.Json.Null))

    val ticketId = cursor.downField("ticketId").as[Long].toOption
    val statusId = cursor.downField("statusId").as[Long].toOption

    (ticketId, statusId) match {
      case (Some(ticketIdValue), Some(statusIdValue)) =>
        redmineUsecase.updateTicket(ticketIdValue, None, None, Some(statusIdValue), None) match {
          case Right(ticket) => JsonRpcResponse(
            id = id,
            result = Some(Map(
              "content" -> List(Map(
                "type" -> "text".asJson,
                "text" -> s"Status changed successfully for ticket #${ticket.id}: ${ticket.title}".asJson
              )).asJson
            ).asJson)
          )
          case Left(error) => JsonRpcResponse(
            id = id,
            error = Some(JsonRpcError(-32603, s"Status change failed: $error"))
          )
        }
      case _ => JsonRpcResponse(
        id = id,
        error = Some(JsonRpcError(-32602, "Missing ticketId or statusId parameter"))
      )
    }
  }

  private def handleChangeBulkTicketStatus(id: JsonRpcId, params: Option[io.circe.Json]): JsonRpcResponse = {
    val cursor = params.map(_.hcursor.downField("arguments")).getOrElse(io.circe.HCursor.fromJson(io.circe.Json.Null))

    val ticketIds = cursor.downField("ticketIds").as[List[Long]].toOption
    val statusId = cursor.downField("statusId").as[Long].toOption

    (ticketIds, statusId) match {
      case (Some(ticketIdsValue), Some(statusIdValue)) =>
        val results = ticketIdsValue.map { ticketId =>
          redmineUsecase.updateTicket(ticketId, None, None, Some(statusIdValue), None) match {
            case Right(ticket) => Some(s"OK #${ticket.id}: ${ticket.title}")
            case Left(error) => Some(s"NG #${ticketId}: $error")
          }
        }
        val successCount = results.count(_.exists(_.startsWith("OK")))
        val totalCount = ticketIdsValue.length
        val resultText = results.flatten.mkString("\n")

        JsonRpcResponse(
          id = id,
          result = Some(Map(
            "content" -> List(Map(
              "type" -> "text".asJson,
              "text" -> s"Bulk status change results (${successCount}/${totalCount} succeeded):\n$resultText".asJson
            )).asJson
          ).asJson)
        )
      case _ => JsonRpcResponse(
        id = id,
        error = Some(JsonRpcError(-32602, "Missing ticketIds or statusId parameter"))
      )
    }
  }

  private def handleChangeTicketTracker(id: JsonRpcId, params: Option[io.circe.Json]): JsonRpcResponse = {
    val cursor = params.map(_.hcursor.downField("arguments")).getOrElse(io.circe.HCursor.fromJson(io.circe.Json.Null))

    val ticketId = cursor.downField("ticketId").as[Long].toOption
    val trackerId = cursor.downField("trackerId").as[Long].toOption

    (ticketId, trackerId) match {
      case (Some(ticketIdValue), Some(trackerIdValue)) =>
        redmineUsecase.updateTicket(ticketIdValue, None, None, None, None, trackerId = Some(trackerIdValue)) match {
          case Right(ticket) => JsonRpcResponse(
            id = id,
            result = Some(Map(
              "content" -> List(Map(
                "type" -> "text".asJson,
                "text" -> s"Tracker changed successfully for ticket #${ticket.id}: ${ticket.title}".asJson
              )).asJson
            ).asJson)
          )
          case Left(error) => JsonRpcResponse(
            id = id,
            error = Some(JsonRpcError(-32603, s"Tracker change failed: $error"))
          )
        }
      case _ => JsonRpcResponse(
        id = id,
        error = Some(JsonRpcError(-32602, "Missing ticketId or trackerId parameter"))
      )
    }
  }

  private def handleGetUsers(id: JsonRpcId, params: Option[io.circe.Json]): JsonRpcResponse = {
    val cursor = params.map(_.hcursor.downField("arguments")).getOrElse(io.circe.HCursor.fromJson(io.circe.Json.Null))

    val name = cursor.downField("name").as[String].toOption
    val limit = cursor.downField("limit").as[Int].toOption.getOrElse(100)
    val offset = cursor.downField("offset").as[Int].toOption.getOrElse(0)

    redmineUsecase.adapter.getUsers(name, limit, offset) match {
      case Right(response) =>
        val formattedOutput = if (response.users.isEmpty) {
          val searchInfo = name.map(n => s"containing '$n'").getOrElse("")
          s"No users found ${searchInfo}.\nTotal: ${response.total_count}, Offset: ${response.offset}, Limit: ${response.limit}"
        } else {
          val userList = response.users.map { user =>
            s"ID: ${user.id} - ${user.lastname} ${user.firstname} (${user.login})${user.mail.map(m => s" <$m>").getOrElse("")}"
          }.mkString("\n")

          val searchInfo = name.map(n => s"Search results for '$n'").getOrElse("User list")
          s"$searchInfo (${response.users.length} of ${response.total_count}, offset: ${response.offset}):\n$userList"
        }

        JsonRpcResponse(
          id = id,
          result = Some(Map(
            "content" -> List(Map(
              "type" -> "text".asJson,
              "text" -> formattedOutput.asJson
            )).asJson
          ).asJson)
        )
      case Left(error) => JsonRpcResponse(
        id = id,
        error = Some(JsonRpcError(-32603, s"Users retrieval failed: $error"))
      )
    }
  }

  private def handleGetComments(id: JsonRpcId, params: Option[io.circe.Json]): JsonRpcResponse = {
    val ticketIdOpt = params.flatMap(_.hcursor.downField("arguments").downField("ticketId").as[Long].toOption)

    ticketIdOpt match {
      case Some(ticketId) =>
        redmineUsecase.getComments(ticketId) match {
          case Right(comments) =>
            val formattedOutput = if (comments.isEmpty) {
              s"No comments found for ticket #${ticketId}"
            } else {
              val commentList = comments.map { journal =>
                val notes = journal.notes.getOrElse("")
                val privateTag = if (journal.private_notes) " [Private]" else ""
                s"[${journal.created_on}] ${journal.user.name}${privateTag}:\n${notes}"
              }.mkString("\n---\n")

              s"Comments for ticket #${ticketId} (${comments.length} comments):\n---\n$commentList"
            }

            JsonRpcResponse(
              id = id,
              result = Some(Map(
                "content" -> List(Map(
                  "type" -> "text".asJson,
                  "text" -> formattedOutput.asJson
                )).asJson
              ).asJson)
            )
          case Left(error) => JsonRpcResponse(
            id = id,
            error = Some(JsonRpcError(-32603, s"Comments retrieval failed: $error"))
          )
        }
      case None => JsonRpcResponse(
        id = id,
        error = Some(JsonRpcError(-32602, "Missing ticketId parameter"))
      )
    }
  }

  private def handleGetRelations(id: JsonRpcId, params: Option[io.circe.Json]): JsonRpcResponse = {
    val ticketIdOpt = params.flatMap(_.hcursor.downField("arguments").downField("ticketId").as[Long].toOption)

    ticketIdOpt match {
      case Some(ticketId) =>
        redmineUsecase.adapter.getRelations(ticketId) match {
          case Right(relations) =>
            val formattedOutput = if (relations.isEmpty) {
              s"No related tickets found for ticket #${ticketId}"
            } else {
              val relationList = relations.map { rel =>
                val fromId = rel.issue_id.map(id => s"#$id").getOrElse("N/A")
                val toId = rel.issue_to_id.map(id => s"#$id").getOrElse("N/A")
                s"${fromId} ${rel.relation_type} ${toId}"
              }.mkString("\n")

              s"Related tickets for ticket #${ticketId} (${relations.length} relations):\n$relationList"
            }

            JsonRpcResponse(
              id = id,
              result = Some(Map(
                "content" -> List(Map(
                  "type" -> "text".asJson,
                  "text" -> formattedOutput.asJson
                )).asJson
              ).asJson)
            )
          case Left(error) => JsonRpcResponse(
            id = id,
            error = Some(JsonRpcError(-32603, s"Relations retrieval failed: $error"))
          )
        }
      case None => JsonRpcResponse(
        id = id,
        error = Some(JsonRpcError(-32602, "Missing ticketId parameter"))
      )
    }
  }
}
