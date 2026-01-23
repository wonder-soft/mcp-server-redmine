package redmine.mcp.adapter

import sttp.client4.*
import sttp.client4.circe.*
import io.circe.generic.auto.*
import io.circe.syntax.*
import redmine.mcp.domain.*
import redmine.mcp.util.Logger

class RedmineApiAdapter {
  private val redmineEndpoint = sys.env.getOrElse("REDMINE_ENDPOINT", "")
  private val redmineApiKey = sys.env.getOrElse("REDMINE_API_KEY", "")
  private val redmineProjectId = sys.env.getOrElse("REDMINE_PROJECT_IDENTIFIER", "")

  def getTicket(ticketId: Long): Either[String, RedmineTicket] = {
    if (redmineEndpoint.isEmpty) {
      Left("REDMINE_ENDPOINT environment variable is not set")
    } else if (redmineApiKey.isEmpty) {
      Left("REDMINE_API_KEY environment variable is not set")
    } else {
      val backend = DefaultSyncBackend()

      val request = basicRequest
        .get(uri"$redmineEndpoint/issues/$ticketId.json")
        .header("X-Redmine-API-Key", redmineApiKey)
        .header("Content-Type", "application/json")
        .response(asJson[RedmineTicketResponse])

      try {
        val response = backend.send(request)
        response.body match {
          case Right(ticketResponse) =>
            val issue = ticketResponse.issue
            Right(RedmineTicket(
              id = issue.id,
              title = issue.subject.getOrElse("No title"),
              description = issue.description,
              assignee = issue.assigned_to
            ))
          case Left(error) => Left(s"API Error: $error")
        }
      } catch {
        case ex: Exception => Left(s"Request failed: ${ex.getMessage}")
      }
    }
  }

  def createTicket(request: RedmineTicketCreateRequest): Either[String, RedmineCreatedIssueData] = {
    if (redmineEndpoint.isEmpty) {
      Left("REDMINE_ENDPOINT environment variable is not set")
    } else if (redmineApiKey.isEmpty) {
      Left("REDMINE_API_KEY environment variable is not set")
    } else if (redmineProjectId.isEmpty) {
      Left("REDMINE_PROJECT_IDENTIFIER environment variable is not set")
    } else {
      val backend = DefaultSyncBackend()

      val issueData = Map(
        "project_id" -> redmineProjectId,
        "subject" -> request.subject,
        "description" -> request.description.getOrElse("")
      ) ++
        request.parent_issue_id.map("parent_issue_id" -> _.toString).toMap ++
        request.assigned_to_id.map("assigned_to_id" -> _.toString).toMap ++
        request.tracker_id.map("tracker_id" -> _.toString).toMap ++
        request.due_date.map("due_date" -> _).toMap

      val payload = Map("issue" -> issueData)

      val httpRequest = basicRequest
        .post(uri"$redmineEndpoint/issues.json")
        .header("X-Redmine-API-Key", redmineApiKey)
        .header("Content-Type", "application/json")
        .body(payload.asJson.noSpaces)
        .response(asJson[RedmineTicketCreateResponse])

      try {
        val response = backend.send(httpRequest)
        response.body match {
          case Right(createResponse: RedmineTicketCreateResponse) =>
            request.related_ticket_ids.foreach { relatedIds =>
              relatedIds.foreach { relatedId =>
                createRelation(createResponse.issue.id, relatedId) match {
                  case Left(error) => Logger.warn(s"Failed to create relation to ticket $relatedId: $error")
                  case Right(_) => Logger.debug(s"Successfully created relation to ticket $relatedId")
                }
              }
            }
            Right(createResponse.issue)
          case Left(error) => Left(s"API Error: $error")
        }
      } catch {
        case ex: Exception => Left(s"Request failed: ${ex.getMessage}")
      }
    }
  }

  def updateTicket(request: RedmineTicketUpdateRequest, clearAssignee: Boolean = false): Either[String, RedmineTicket] = {
    if (redmineEndpoint.isEmpty) {
      Left("REDMINE_ENDPOINT environment variable is not set")
    } else if (redmineApiKey.isEmpty) {
      Left("REDMINE_API_KEY environment variable is not set")
    } else {
      val backend = DefaultSyncBackend()

      val baseIssueData = Map.empty[String, String] ++
        request.subject.map("subject" -> _) ++
        request.description.map("description" -> _) ++
        request.status_id.map("status_id" -> _.toString) ++
        request.assigned_to_id.map("assigned_to_id" -> _.toString) ++
        request.tracker_id.map("tracker_id" -> _.toString) ++
        request.parent_issue_id.map("parent_issue_id" -> _.toString) ++
        request.due_date.map("due_date" -> _)

      // If clearAssignee is true, set assigned_to_id to empty string to clear the assignee
      val issueData = if (clearAssignee) {
        baseIssueData + ("assigned_to_id" -> "")
      } else {
        baseIssueData
      }

      val payload = Map("issue" -> issueData)

      val httpRequest = basicRequest
        .put(uri"$redmineEndpoint/issues/${request.id}.json")
        .header("X-Redmine-API-Key", redmineApiKey)
        .header("Content-Type", "application/json")
        .body(payload.asJson.noSpaces)
        .response(asString)

      try {
        val response = backend.send(httpRequest)
        response.body match {
          case Right(_) =>
            request.related_ticket_ids.foreach { relatedIds =>
              relatedIds.foreach { relatedId =>
                createRelation(request.id, relatedId) match {
                  case Left(error) => Logger.warn(s"Failed to create relation to ticket $relatedId: $error")
                  case Right(_) => Logger.debug(s"Successfully created relation to ticket $relatedId")
                }
              }
            }
            getTicket(request.id)
          case Left(error) => Left(s"API Error: $error")
        }
      } catch {
        case ex: Exception => Left(s"Request failed: ${ex.getMessage}")
      }
    }
  }

  def getChildTickets(parentId: Long, limit: Int = 100, offset: Int = 0, assignedToId: Option[Long] = None): Either[String, RedmineChildTicketsResponse] = {
    if (redmineEndpoint.isEmpty) {
      Left("REDMINE_ENDPOINT environment variable is not set")
    } else if (redmineApiKey.isEmpty) {
      Left("REDMINE_API_KEY environment variable is not set")
    } else {
      val backend = DefaultSyncBackend()

      val baseUri = uri"$redmineEndpoint/issues.json?parent_id=$parentId&status_id=*&limit=$limit&offset=$offset"
      val requestUri = assignedToId match {
        case Some(assigneeId) => uri"$redmineEndpoint/issues.json?parent_id=$parentId&status_id=*&assigned_to_id=$assigneeId&limit=$limit&offset=$offset"
        case None => baseUri
      }

      val request = basicRequest
        .get(requestUri)
        .header("X-Redmine-API-Key", redmineApiKey)
        .header("Content-Type", "application/json")
        .response(asJson[RedmineChildTicketsResponse])

      try {
        val response = backend.send(request)
        response.body match {
          case Right(childTicketsResponse) => Right(childTicketsResponse)
          case Left(error) => Left(s"API Error: $error")
        }
      } catch {
        case ex: Exception => Left(s"Request failed: ${ex.getMessage}")
      }
    }
  }

  def searchTicketsByTitle(query: String, limit: Int = 100, offset: Int = 0): Either[String, RedmineSearchResponse] = {
    if (redmineEndpoint.isEmpty) {
      Left("REDMINE_ENDPOINT environment variable is not set")
    } else if (redmineApiKey.isEmpty) {
      Left("REDMINE_API_KEY environment variable is not set")
    } else {
      val backend = DefaultSyncBackend()

      val request = basicRequest
        .get(uri"$redmineEndpoint/search.xml?q=$query&titles_only=1&issues=1&limit=$limit&offset=$offset")
        .header("X-Redmine-API-Key", redmineApiKey)
        .header("Content-Type", "application/xml")
        .response(asString)

      try {
        val response = backend.send(request)
        response.body match {
          case Right(xmlString) => parseSearchXml(xmlString, limit, offset)
          case Left(error) => Left(s"API Error: $error")
        }
      } catch {
        case ex: Exception => Left(s"Request failed: ${ex.getMessage}")
      }
    }
  }

  def getRelations(issueId: Long): Either[String, List[RedmineRelationData]] = {
    if (redmineEndpoint.isEmpty) {
      Left("REDMINE_ENDPOINT environment variable is not set")
    } else if (redmineApiKey.isEmpty) {
      Left("REDMINE_API_KEY environment variable is not set")
    } else {
      val backend = DefaultSyncBackend()

      val httpRequest = basicRequest
        .get(uri"$redmineEndpoint/issues/$issueId/relations.json")
        .header("X-Redmine-API-Key", redmineApiKey)
        .header("Content-Type", "application/json")
        .response(asJson[RedmineRelationsListResponse])

      try {
        val response = backend.send(httpRequest)
        response.body match {
          case Right(relationsResponse) => Right(relationsResponse.relations)
          case Left(error) => Left(s"API Error: $error")
        }
      } catch {
        case ex: Exception => Left(s"Request failed: ${ex.getMessage}")
      }
    }
  }

  def createRelation(fromIssueId: Long, toIssueId: Long, relationType: String = "relates"): Either[String, RedmineRelationData] = {
    if (redmineEndpoint.isEmpty) {
      Left("REDMINE_ENDPOINT environment variable is not set")
    } else if (redmineApiKey.isEmpty) {
      Left("REDMINE_API_KEY environment variable is not set")
    } else {
      val backend = DefaultSyncBackend()

      val relationData = RedmineRelationRequest(
        issue_to_id = toIssueId,
        relation_type = relationType
      )

      val payload = Map("relation" -> relationData)

      val httpRequest = basicRequest
        .post(uri"$redmineEndpoint/issues/$fromIssueId/relations.json")
        .header("X-Redmine-API-Key", redmineApiKey)
        .header("Content-Type", "application/json")
        .body(payload.asJson.noSpaces)
        .response(asJson[RedmineRelationResponse])

      try {
        val response = backend.send(httpRequest)
        response.body match {
          case Right(relationResponse) => Right(relationResponse.relation)
          case Left(error) => Left(s"API Error: $error")
        }
      } catch {
        case ex: Exception => Left(s"Request failed: ${ex.getMessage}")
      }
    }
  }

  def addComment(ticketId: Long, comment: String, isPrivate: Boolean = false): Either[String, String] = {
    if (redmineEndpoint.isEmpty) {
      Left("REDMINE_ENDPOINT environment variable is not set")
    } else if (redmineApiKey.isEmpty) {
      Left("REDMINE_API_KEY environment variable is not set")
    } else {
      val backend = DefaultSyncBackend()

      val issueData = Map(
        "notes" -> comment
      ) ++ (if (isPrivate) Map("private_notes" -> "true") else Map.empty)

      val payload = Map("issue" -> issueData)

      val httpRequest = basicRequest
        .put(uri"$redmineEndpoint/issues/$ticketId.json")
        .header("X-Redmine-API-Key", redmineApiKey)
        .header("Content-Type", "application/json")
        .body(payload.asJson.noSpaces)
        .response(asString)

      try {
        val response = backend.send(httpRequest)
        response.body match {
          case Right(_) => Right(s"Comment added successfully to ticket #$ticketId")
          case Left(error) => Left(s"API Error: $error")
        }
      } catch {
        case ex: Exception => Left(s"Request failed: ${ex.getMessage}")
      }
    }
  }

  def getComments(ticketId: Long): Either[String, List[RedmineJournal]] = {
    if (redmineEndpoint.isEmpty) {
      Left("REDMINE_ENDPOINT environment variable is not set")
    } else if (redmineApiKey.isEmpty) {
      Left("REDMINE_API_KEY environment variable is not set")
    } else {
      val backend = DefaultSyncBackend()

      val request = basicRequest
        .get(uri"$redmineEndpoint/issues/$ticketId.json?include=journals")
        .header("X-Redmine-API-Key", redmineApiKey)
        .header("Content-Type", "application/json")
        .response(asJson[RedmineTicketWithJournalsResponse])

      try {
        val response = backend.send(request)
        response.body match {
          case Right(ticketResponse) =>
            // Filter journals to only include those with notes (actual comments)
            val comments = ticketResponse.issue.journals.filter(_.notes.exists(_.nonEmpty))
            Right(comments)
          case Left(error) => Left(s"API Error: $error")
        }
      } catch {
        case ex: Exception => Left(s"Request failed: ${ex.getMessage}")
      }
    }
  }

  def getUsers(name: Option[String] = None, limit: Int = 100, offset: Int = 0): Either[String, RedmineUsersResponse] = {
    if (redmineEndpoint.isEmpty) {
      Left("REDMINE_ENDPOINT environment variable is not set")
    } else if (redmineApiKey.isEmpty) {
      Left("REDMINE_API_KEY environment variable is not set")
    } else {
      val backend = DefaultSyncBackend()

      val baseUri = uri"$redmineEndpoint/users.json?status=1&limit=$limit&offset=$offset"
      val requestUri = name match {
        case Some(n) if n.nonEmpty => uri"$redmineEndpoint/users.json?status=1&name=$n&limit=$limit&offset=$offset"
        case _ => baseUri
      }

      val request = basicRequest
        .get(requestUri)
        .header("X-Redmine-API-Key", redmineApiKey)
        .header("Content-Type", "application/json")
        .response(asJson[RedmineUsersResponse])

      try {
        val response = backend.send(request)
        response.body match {
          case Right(usersResponse) => Right(usersResponse)
          case Left(error) => Left(s"API Error: $error")
        }
      } catch {
        case ex: Exception => Left(s"Request failed: ${ex.getMessage}")
      }
    }
  }

  private def parseSearchXml(xmlString: String, limit: Int, offset: Int): Either[String, RedmineSearchResponse] = {
    try {
      val totalCountRegex = """total_count="(\d+)"""".r
      val offsetRegex = """offset="(\d+)"""".r
      val limitRegex = """limit="(\d+)"""".r

      val totalCount = totalCountRegex.findFirstMatchIn(xmlString).map(_.group(1).toInt).getOrElse(0)
      val actualOffset = offsetRegex.findFirstMatchIn(xmlString).map(_.group(1).toInt).getOrElse(offset)
      val actualLimit = limitRegex.findFirstMatchIn(xmlString).map(_.group(1).toInt).getOrElse(limit)

      val resultBlockPattern = """(?s)<result\b[^>]*>(.*?)</result>""".r

      val idPattern = """<id>(\d+)</id>""".r
      val typePattern = """<type>([^<]*)</type>""".r
      val titlePattern = """<title>([^<]*)</title>""".r
      val urlPattern = """<url>([^<]*)</url>""".r
      val descriptionPattern = """<description>([^<]*)</description>""".r

      val blocks = resultBlockPattern.findAllMatchIn(xmlString).toList

      val results = blocks.flatMap { m =>
        val blockContent = m.group(1)

        val idOpt = idPattern.findFirstMatchIn(blockContent).map(_.group(1).toLong)
        val typeOpt = typePattern.findFirstMatchIn(blockContent).map(_.group(1))

        idOpt.map { id =>
          val resultType = typeOpt.getOrElse("")
          val title = titlePattern.findFirstMatchIn(blockContent).map(_.group(1)).getOrElse("")
          val url = urlPattern.findFirstMatchIn(blockContent).map(_.group(1)).getOrElse("")
          val description = descriptionPattern.findFirstMatchIn(blockContent).map(_.group(1)).filter(_.nonEmpty)

          RedmineSearchResult(id, title, description, url, resultType)
        }
      }

      Right(RedmineSearchResponse(results, totalCount, actualOffset, actualLimit))
    } catch {
      case ex: Exception => Left(s"XML parsing failed: ${ex.getMessage}")
    }
  }
}
