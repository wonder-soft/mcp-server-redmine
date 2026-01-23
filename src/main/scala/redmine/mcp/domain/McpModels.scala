package redmine.mcp.domain

import io.circe.{Decoder, Encoder, Json}
import io.circe.generic.semiauto.*

// JSON-RPC specification allows id to be string, number, or null
type JsonRpcId = Option[Either[String, Long]]

case class JsonRpcRequest(
  jsonrpc: String = "2.0",
  id: JsonRpcId,
  method: String,
  params: Option[io.circe.Json]
)

case class JsonRpcResponse(
  jsonrpc: String = "2.0",
  id: JsonRpcId,
  result: Option[io.circe.Json] = None,
  error: Option[JsonRpcError] = None
)

case class JsonRpcError(
  code: Int,
  message: String,
  data: Option[io.circe.Json] = None
)

case class McpTool(
  name: String,
  description: String,
  inputSchema: io.circe.Json
)

case class McpResource(
  uri: String,
  name: String,
  description: String,
  mimeType: String
)

object McpModels {
  // Custom ID decoder/encoder
  implicit val jsonRpcIdDecoder: Decoder[JsonRpcId] = Decoder.instance { cursor =>
    cursor.value.asNull match {
      case Some(_) => Right(None)
      case None =>
        cursor.as[String].map(s => Some(Left(s)))
          .orElse(cursor.as[Long].map(n => Some(Right(n))))
    }
  }

  implicit val jsonRpcIdEncoder: Encoder[JsonRpcId] = Encoder.instance {
    case None => Json.Null
    case Some(Left(s)) => Json.fromString(s)
    case Some(Right(n)) => Json.fromLong(n)
  }

  // Custom JsonRpcResponse encoder - exclude error when None
  implicit val jsonRpcResponseEncoder: Encoder[JsonRpcResponse] = Encoder.instance { response =>
    val baseFields = List(
      "jsonrpc" -> Json.fromString(response.jsonrpc),
      "id" -> jsonRpcIdEncoder(response.id)
    )

    val resultFields = response.result.map("result" -> _).toList
    val errorFields = response.error.map("error" -> jsonRpcErrorEncoder(_)).toList

    Json.obj((baseFields ++ resultFields ++ errorFields)*)
  }

  // Custom JsonRpcRequest decoder - id field is optional (null/undefined for notifications)
  implicit val jsonRpcRequestDecoder: Decoder[JsonRpcRequest] = Decoder.instance { cursor =>
    for {
      jsonrpc <- cursor.downField("jsonrpc").as[String].orElse(Right("2.0"))
      method <- cursor.downField("method").as[String]
      params <- cursor.downField("params").as[Option[Json]]
      id <- cursor.downField("id").as[JsonRpcId].orElse(Right(None))
    } yield JsonRpcRequest(jsonrpc, id, method, params)
  }
  implicit val jsonRpcRequestEncoder: Encoder[JsonRpcRequest] = deriveEncoder
  implicit val jsonRpcErrorEncoder: Encoder[JsonRpcError] = deriveEncoder
  implicit val mcpToolEncoder: Encoder[McpTool] = deriveEncoder
  implicit val mcpResourceEncoder: Encoder[McpResource] = deriveEncoder
}
