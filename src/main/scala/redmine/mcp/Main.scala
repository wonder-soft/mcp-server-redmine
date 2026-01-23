package redmine.mcp

import com.sun.net.httpserver.{HttpServer, HttpHandler, HttpExchange}
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import redmine.mcp.adapter.McpServerAdapter
import redmine.mcp.util.Logger

object Main {
  def main(args: Array[String]): Unit = {
    val port = sys.env.getOrElse("MCP_PORT", "8080").toInt

    Logger.info(s"Starting Redmine MCP Server on port $port...")
    Logger.info("Required environment variables:")
    Logger.info("  - REDMINE_ENDPOINT: Redmine server URL (e.g., https://redmine.example.com)")
    Logger.info("  - REDMINE_API_KEY: Redmine API key")
    Logger.info("  - REDMINE_PROJECT_IDENTIFIER: Redmine project identifier (for creating tickets)")

    val mcpAdapter = new McpServerAdapter()
    val server = HttpServer.create(new InetSocketAddress(port), 0)

    // MCP endpoints
    server.createContext("/sse", mcpAdapter)
    server.createContext("/message", mcpAdapter)
    server.createContext("/mcp", mcpAdapter)

    // OAuth discovery endpoint - returns 404 to indicate no OAuth required
    server.createContext("/.well-known", new HttpHandler {
      def handle(exchange: HttpExchange): Unit = {
        exchange.getResponseHeaders.set("Access-Control-Allow-Origin", "*")
        exchange.sendResponseHeaders(404, -1)
        exchange.getResponseBody.close()
      }
    })

    // Root endpoint
    server.createContext("/", new HttpHandler {
      def handle(exchange: HttpExchange): Unit = {
        val path = exchange.getRequestURI.getPath
        if (path == "/" || path.isEmpty) {
          val response = s"""{"status":"ok","mcp_endpoint":"/mcp"}"""
          exchange.getResponseHeaders.set("Content-Type", "application/json")
          exchange.getResponseHeaders.set("Access-Control-Allow-Origin", "*")
          exchange.sendResponseHeaders(200, response.length)
          val out = exchange.getResponseBody
          out.write(response.getBytes)
          out.close()
        } else {
          exchange.sendResponseHeaders(404, -1)
          exchange.getResponseBody.close()
        }
      }
    })

    server.setExecutor(Executors.newCachedThreadPool())
    server.start()

    Logger.info("MCP Server started successfully!")
    Logger.info(s"  SSE endpoint: http://localhost:$port/sse")
    Logger.info(s"  Message endpoint: http://localhost:$port/message")
    Logger.info("Press Ctrl+C to stop the server.")

    // Keep the server running
    while (true) {
      Thread.sleep(1000)
    }
  }
}
