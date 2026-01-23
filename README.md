# mcp-server-redmine

> A lightweight MCP (Model Context Protocol) server for Redmine, powered by Scala 3 + scala-cli

![Scala](https://img.shields.io/badge/Scala-3.3.7_LTS-DC322F?logo=scala)
![License](https://img.shields.io/badge/License-MIT-blue.svg)
![MCP](https://img.shields.io/badge/MCP-2025--06--18-green)

## Overview

This is a Model Context Protocol (MCP) server that provides AI assistants (like Claude) with the ability to interact with Redmine project management systems. It allows reading, creating, and updating tickets, managing comments, and searching for issues.

## Features

- **Ticket Management**
  - Get ticket details
  - Create new tickets
  - Update existing tickets
  - Change ticket status (single or bulk)

- **Child Tickets**
  - Get child tickets of a parent ticket
  - Filter by assignee

- **Search**
  - Search tickets by title
  - Pagination support

- **Comments**
  - Add comments to tickets
  - Get ticket comments (journals)
  - Support for private comments

- **Users**
  - Search users by name
  - Get user list

- **Relations**
  - Get related tickets
  - Create ticket relations

## Requirements

- [scala-cli](https://scala-cli.virtuslab.org/) (recommended) or Scala 3.3+
- Redmine 4.0+ with REST API enabled
- Redmine API key with appropriate permissions

## Installation

### Using scala-cli (Recommended)

```bash
git clone https://github.com/your-org/mcp-server-redmine.git
cd mcp-server-redmine
scala-cli run .
```

### Building a standalone JAR

```bash
scala-cli package . -o mcp-server-redmine.jar --assembly
java -jar mcp-server-redmine.jar
```

### Using pre-built binary (Recommended for production)

Download the pre-built native binary from the [Releases](https://github.com/wonder-soft/mcp-server-redmine/releases) page:

- `mcp-server-redmine-macos-arm64` - macOS Apple Silicon (M1/M2/M3)
- `mcp-server-redmine-windows-x64.exe` - Windows x64

```bash
# macOS
chmod +x mcp-server-redmine-macos-arm64
./mcp-server-redmine-macos-arm64

# Windows
mcp-server-redmine-windows-x64.exe
```

> **Note (macOS):** If you see a security warning like "cannot be opened because the developer cannot be verified", run:
> ```bash
> xattr -d com.apple.quarantine mcp-server-redmine-macos-arm64
> ```

### Building a native binary locally

Requires GraalVM (automatically downloaded by scala-cli):

```bash
scala-cli --power package . --native-image -o mcp-server-redmine
./mcp-server-redmine
```

## Configuration

Set the following environment variables:

| Variable | Required | Description |
|----------|----------|-------------|
| `REDMINE_ENDPOINT` | Yes | Redmine server URL (e.g., `https://redmine.example.com`) |
| `REDMINE_API_KEY` | Yes | Redmine API key |
| `REDMINE_PROJECT_IDENTIFIER` | Yes* | Project identifier for creating tickets |
| `MCP_PORT` | No | Server port (default: `8080`) |
| `LOG_LEVEL` | No | Log level: `DEBUG`, `INFO`, `WARN`, `ERROR` (default: `INFO`) |

*Required only for creating new tickets.

### Getting a Redmine API Key

1. Log in to your Redmine instance
2. Go to **My account** (top right menu)
3. Click **Show** under **API access key** in the right sidebar
4. Copy the API key

## Usage

### Starting the Server

```bash
# Set environment variables
export REDMINE_ENDPOINT="https://redmine.example.com"
export REDMINE_API_KEY="your-api-key"
export REDMINE_PROJECT_IDENTIFIER="your-project"

# Start the server
scala-cli run .
```

### MCP Endpoints

- **SSE Endpoint**: `http://localhost:8080/sse`
- **Message Endpoint**: `http://localhost:8080/message`

### Configuring with Claude Desktop

Add the following to your Claude Desktop configuration (`claude_desktop_config.json`):

**Using pre-built binary (Recommended):**

```json
{
  "mcpServers": {
    "redmine": {
      "command": "/path/to/mcp-server-redmine-macos-arm64",
      "env": {
        "REDMINE_ENDPOINT": "https://redmine.example.com",
        "REDMINE_API_KEY": "your-api-key",
        "REDMINE_PROJECT_IDENTIFIER": "your-project"
      }
    }
  }
}
```

**Using scala-cli:**

```json
{
  "mcpServers": {
    "redmine": {
      "command": "scala-cli",
      "args": ["run", "/path/to/mcp-server-redmine"],
      "env": {
        "REDMINE_ENDPOINT": "https://redmine.example.com",
        "REDMINE_API_KEY": "your-api-key",
        "REDMINE_PROJECT_IDENTIFIER": "your-project"
      }
    }
  }
}
```

## Available Tools

| Tool | Description |
|------|-------------|
| `get_redmine_ticket` | Get ticket information by ID |
| `create_redmine_ticket` | Create a new ticket |
| `update_redmine_ticket` | Update an existing ticket |
| `get_redmine_child_tickets` | Get child tickets of a parent |
| `search_redmine_tickets` | Search tickets by title |
| `add_redmine_comment` | Add a comment to a ticket |
| `change_redmine_ticket_status` | Change ticket status |
| `change_bulk_redmine_ticket_status` | Change status of multiple tickets |
| `get_redmine_users` | Search/list Redmine users |
| `get_redmine_comments` | Get comments of a ticket |
| `get_redmine_relations` | Get related tickets |

## Development

### Project Structure

```
mcp-server-redmine/
├── project.scala                 # scala-cli build configuration
├── src/main/scala/redmine/mcp/
│   ├── Main.scala               # Entry point
│   ├── domain/
│   │   ├── McpModels.scala      # MCP protocol models
│   │   └── RedmineModels.scala  # Redmine API models
│   ├── adapter/
│   │   ├── McpServerAdapter.scala    # MCP server implementation
│   │   └── RedmineApiAdapter.scala   # Redmine API client
│   └── usecase/
│       └── RedmineUsecase.scala      # Business logic
├── README.md
├── LICENSE
└── CONTRIBUTING.md
```

### Running Tests

```bash
scala-cli test .
```

### Building

```bash
scala-cli compile .
```

## Contributing

Please see [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## Acknowledgments

- [Anthropic](https://www.anthropic.com/) for the Model Context Protocol specification
- [Redmine](https://www.redmine.org/) for the project management platform
- [scala-cli](https://scala-cli.virtuslab.org/) for the build tool
