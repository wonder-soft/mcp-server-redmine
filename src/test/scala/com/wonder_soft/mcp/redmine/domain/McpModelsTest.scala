package com.wonder_soft.mcp.redmine.domain

import munit.FunSuite
import io.circe.parser.*
import io.circe.syntax.*
import io.circe.Json
import McpModels.given

class McpModelsTest extends FunSuite {

  test("JsonRpcId should decode null as None") {
    val json = "null"
    val result = decode[JsonRpcId](json)

    assert(result.isRight)
    assertEquals(result.toOption.get, None)
  }

  test("JsonRpcId should decode string id") {
    val json = """"request-1""""
    val result = decode[JsonRpcId](json)

    assert(result.isRight)
    assertEquals(result.toOption.get, Some(Left("request-1")))
  }

  test("JsonRpcId should decode numeric id") {
    val json = "123"
    val result = decode[JsonRpcId](json)

    assert(result.isRight)
    assertEquals(result.toOption.get, Some(Right(123L)))
  }

  test("JsonRpcId should encode None as null") {
    val id: JsonRpcId = None
    val json = id.asJson

    assertEquals(json, Json.Null)
  }

  test("JsonRpcId should encode string id") {
    val id: JsonRpcId = Some(Left("request-1"))
    val json = id.asJson

    assertEquals(json, Json.fromString("request-1"))
  }

  test("JsonRpcId should encode numeric id") {
    val id: JsonRpcId = Some(Right(123L))
    val json = id.asJson

    assertEquals(json, Json.fromLong(123L))
  }

  test("JsonRpcRequest should decode with string id") {
    val json = """{
      "jsonrpc": "2.0",
      "id": "request-1",
      "method": "tools/list",
      "params": {}
    }"""
    val result = decode[JsonRpcRequest](json)

    assert(result.isRight, s"Decoding failed: ${result.left.toOption}")
    val request = result.toOption.get
    assertEquals(request.jsonrpc, "2.0")
    assertEquals(request.id, Some(Left("request-1")))
    assertEquals(request.method, "tools/list")
  }

  test("JsonRpcRequest should decode with numeric id") {
    val json = """{
      "jsonrpc": "2.0",
      "id": 1,
      "method": "tools/call",
      "params": {"name": "get_redmine_ticket", "arguments": {"ticket_id": 123}}
    }"""
    val result = decode[JsonRpcRequest](json)

    assert(result.isRight, s"Decoding failed: ${result.left.toOption}")
    val request = result.toOption.get
    assertEquals(request.id, Some(Right(1L)))
    assertEquals(request.method, "tools/call")
  }

  test("JsonRpcRequest should decode notification (no id)") {
    val json = """{
      "jsonrpc": "2.0",
      "method": "notifications/initialized"
    }"""
    val result = decode[JsonRpcRequest](json)

    assert(result.isRight, s"Decoding failed: ${result.left.toOption}")
    val request = result.toOption.get
    assertEquals(request.id, None)
    assertEquals(request.method, "notifications/initialized")
  }

  test("JsonRpcRequest should decode with null id") {
    val json = """{
      "jsonrpc": "2.0",
      "id": null,
      "method": "initialize"
    }"""
    val result = decode[JsonRpcRequest](json)

    assert(result.isRight, s"Decoding failed: ${result.left.toOption}")
    val request = result.toOption.get
    assertEquals(request.id, None)
  }

  test("JsonRpcResponse should encode with result only") {
    val response = JsonRpcResponse(
      id = Some(Right(1L)),
      result = Some(Json.obj("status" -> Json.fromString("ok")))
    )
    val json = response.asJson

    assertEquals(json.hcursor.get[String]("jsonrpc").toOption, Some("2.0"))
    assertEquals(json.hcursor.get[Long]("id").toOption, Some(1L))
    assert(json.hcursor.downField("result").focus.isDefined)
    assert(json.hcursor.downField("error").focus.isEmpty)
  }

  test("JsonRpcResponse should encode with error only") {
    val response = JsonRpcResponse(
      id = Some(Left("err-1")),
      error = Some(JsonRpcError(
        code = -32600,
        message = "Invalid Request"
      ))
    )
    val json = response.asJson

    assertEquals(json.hcursor.get[String]("id").toOption, Some("err-1"))
    assert(json.hcursor.downField("error").focus.isDefined)
    assertEquals(json.hcursor.downField("error").get[Int]("code").toOption, Some(-32600))
    assert(json.hcursor.downField("result").focus.isEmpty)
  }

  test("JsonRpcError should encode with data") {
    val error = JsonRpcError(
      code = -32602,
      message = "Invalid params",
      data = Some(Json.obj("details" -> Json.fromString("Missing required field")))
    )
    val json = error.asJson

    assertEquals(json.hcursor.get[Int]("code").toOption, Some(-32602))
    assertEquals(json.hcursor.get[String]("message").toOption, Some("Invalid params"))
    assert(json.hcursor.downField("data").focus.isDefined)
  }

  test("McpTool should encode correctly") {
    val tool = McpTool(
      name = "get_redmine_ticket",
      description = "Get a Redmine ticket by ID",
      inputSchema = Json.obj(
        "type" -> Json.fromString("object"),
        "properties" -> Json.obj(
          "ticket_id" -> Json.obj(
            "type" -> Json.fromString("integer"),
            "description" -> Json.fromString("The ticket ID")
          )
        ),
        "required" -> Json.arr(Json.fromString("ticket_id"))
      )
    )
    val json = tool.asJson

    assertEquals(json.hcursor.get[String]("name").toOption, Some("get_redmine_ticket"))
    assertEquals(json.hcursor.get[String]("description").toOption, Some("Get a Redmine ticket by ID"))
    assert(json.hcursor.downField("inputSchema").focus.isDefined)
  }

  test("McpResource should encode correctly") {
    val resource = McpResource(
      uri = "redmine://tickets/123",
      name = "Ticket #123",
      description = "A Redmine ticket",
      mimeType = "application/json"
    )
    val json = resource.asJson

    assertEquals(json.hcursor.get[String]("uri").toOption, Some("redmine://tickets/123"))
    assertEquals(json.hcursor.get[String]("name").toOption, Some("Ticket #123"))
    assertEquals(json.hcursor.get[String]("mimeType").toOption, Some("application/json"))
  }

  test("JsonRpcRequest should handle params with nested objects") {
    val json = """{
      "jsonrpc": "2.0",
      "id": 2,
      "method": "tools/call",
      "params": {
        "name": "create_redmine_ticket",
        "arguments": {
          "subject": "New Feature",
          "description": "Add new feature",
          "parent_ticket_id": 100,
          "related_ticket_ids": [101, 102, 103]
        }
      }
    }"""
    val result = decode[JsonRpcRequest](json)

    assert(result.isRight, s"Decoding failed: ${result.left.toOption}")
    val request = result.toOption.get
    assertEquals(request.method, "tools/call")

    val params = request.params.get
    assertEquals(params.hcursor.get[String]("name").toOption, Some("create_redmine_ticket"))

    val arguments = params.hcursor.downField("arguments")
    assertEquals(arguments.get[String]("subject").toOption, Some("New Feature"))
    assertEquals(arguments.get[Long]("parent_ticket_id").toOption, Some(100L))
  }
}
