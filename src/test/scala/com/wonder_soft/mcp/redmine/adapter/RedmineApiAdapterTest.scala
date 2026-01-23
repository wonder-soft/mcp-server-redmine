package com.wonder_soft.mcp.redmine.adapter

import munit.FunSuite

class RedmineApiAdapterTest extends FunSuite {

  val testEndpoint = "http://redmine.example.com"
  val testApiKey = "test-api-key"
  val testProjectId = "test-project"

  test("getTicket should return error when endpoint is empty") {
    val adapter = new RedmineApiAdapter(
      endpoint = "",
      apiKey = testApiKey,
      projectId = testProjectId
    )
    val result = adapter.getTicket(123L)

    assert(result.isLeft)
    assertEquals(result.left.toOption.get, "REDMINE_ENDPOINT environment variable is not set")
  }

  test("getTicket should return error when API key is empty") {
    val adapter = new RedmineApiAdapter(
      endpoint = testEndpoint,
      apiKey = "",
      projectId = testProjectId
    )
    val result = adapter.getTicket(123L)

    assert(result.isLeft)
    assertEquals(result.left.toOption.get, "REDMINE_API_KEY environment variable is not set")
  }

  test("createTicket should return error when endpoint is empty") {
    val adapter = new RedmineApiAdapter(
      endpoint = "",
      apiKey = testApiKey,
      projectId = testProjectId
    )
    val result = adapter.createTicket(
      com.wonder_soft.mcp.redmine.domain.RedmineTicketCreateRequest(subject = "Test")
    )

    assert(result.isLeft)
    assertEquals(result.left.toOption.get, "REDMINE_ENDPOINT environment variable is not set")
  }

  test("createTicket should return error when API key is empty") {
    val adapter = new RedmineApiAdapter(
      endpoint = testEndpoint,
      apiKey = "",
      projectId = testProjectId
    )
    val result = adapter.createTicket(
      com.wonder_soft.mcp.redmine.domain.RedmineTicketCreateRequest(subject = "Test")
    )

    assert(result.isLeft)
    assertEquals(result.left.toOption.get, "REDMINE_API_KEY environment variable is not set")
  }

  test("createTicket should return error when project ID is empty") {
    val adapter = new RedmineApiAdapter(
      endpoint = testEndpoint,
      apiKey = testApiKey,
      projectId = ""
    )
    val result = adapter.createTicket(
      com.wonder_soft.mcp.redmine.domain.RedmineTicketCreateRequest(subject = "Test")
    )

    assert(result.isLeft)
    assertEquals(result.left.toOption.get, "REDMINE_PROJECT_IDENTIFIER environment variable is not set")
  }

  test("updateTicket should return error when endpoint is empty") {
    val adapter = new RedmineApiAdapter(
      endpoint = "",
      apiKey = testApiKey,
      projectId = testProjectId
    )
    val result = adapter.updateTicket(
      com.wonder_soft.mcp.redmine.domain.RedmineTicketUpdateRequest(id = 123L)
    )

    assert(result.isLeft)
    assertEquals(result.left.toOption.get, "REDMINE_ENDPOINT environment variable is not set")
  }

  test("updateTicket should return error when API key is empty") {
    val adapter = new RedmineApiAdapter(
      endpoint = testEndpoint,
      apiKey = "",
      projectId = testProjectId
    )
    val result = adapter.updateTicket(
      com.wonder_soft.mcp.redmine.domain.RedmineTicketUpdateRequest(id = 123L)
    )

    assert(result.isLeft)
    assertEquals(result.left.toOption.get, "REDMINE_API_KEY environment variable is not set")
  }

  test("getChildTickets should return error when endpoint is empty") {
    val adapter = new RedmineApiAdapter(
      endpoint = "",
      apiKey = testApiKey,
      projectId = testProjectId
    )
    val result = adapter.getChildTickets(100L)

    assert(result.isLeft)
    assertEquals(result.left.toOption.get, "REDMINE_ENDPOINT environment variable is not set")
  }

  test("getChildTickets should return error when API key is empty") {
    val adapter = new RedmineApiAdapter(
      endpoint = testEndpoint,
      apiKey = "",
      projectId = testProjectId
    )
    val result = adapter.getChildTickets(100L)

    assert(result.isLeft)
    assertEquals(result.left.toOption.get, "REDMINE_API_KEY environment variable is not set")
  }

  test("searchTicketsByTitle should return error when endpoint is empty") {
    val adapter = new RedmineApiAdapter(
      endpoint = "",
      apiKey = testApiKey,
      projectId = testProjectId
    )
    val result = adapter.searchTicketsByTitle("test")

    assert(result.isLeft)
    assertEquals(result.left.toOption.get, "REDMINE_ENDPOINT environment variable is not set")
  }

  test("searchTicketsByTitle should return error when API key is empty") {
    val adapter = new RedmineApiAdapter(
      endpoint = testEndpoint,
      apiKey = "",
      projectId = testProjectId
    )
    val result = adapter.searchTicketsByTitle("test")

    assert(result.isLeft)
    assertEquals(result.left.toOption.get, "REDMINE_API_KEY environment variable is not set")
  }

  test("getRelations should return error when endpoint is empty") {
    val adapter = new RedmineApiAdapter(
      endpoint = "",
      apiKey = testApiKey,
      projectId = testProjectId
    )
    val result = adapter.getRelations(100L)

    assert(result.isLeft)
    assertEquals(result.left.toOption.get, "REDMINE_ENDPOINT environment variable is not set")
  }

  test("getRelations should return error when API key is empty") {
    val adapter = new RedmineApiAdapter(
      endpoint = testEndpoint,
      apiKey = "",
      projectId = testProjectId
    )
    val result = adapter.getRelations(100L)

    assert(result.isLeft)
    assertEquals(result.left.toOption.get, "REDMINE_API_KEY environment variable is not set")
  }

  test("createRelation should return error when endpoint is empty") {
    val adapter = new RedmineApiAdapter(
      endpoint = "",
      apiKey = testApiKey,
      projectId = testProjectId
    )
    val result = adapter.createRelation(100L, 200L)

    assert(result.isLeft)
    assertEquals(result.left.toOption.get, "REDMINE_ENDPOINT environment variable is not set")
  }

  test("createRelation should return error when API key is empty") {
    val adapter = new RedmineApiAdapter(
      endpoint = testEndpoint,
      apiKey = "",
      projectId = testProjectId
    )
    val result = adapter.createRelation(100L, 200L)

    assert(result.isLeft)
    assertEquals(result.left.toOption.get, "REDMINE_API_KEY environment variable is not set")
  }

  test("addComment should return error when endpoint is empty") {
    val adapter = new RedmineApiAdapter(
      endpoint = "",
      apiKey = testApiKey,
      projectId = testProjectId
    )
    val result = adapter.addComment(123L, "comment")

    assert(result.isLeft)
    assertEquals(result.left.toOption.get, "REDMINE_ENDPOINT environment variable is not set")
  }

  test("addComment should return error when API key is empty") {
    val adapter = new RedmineApiAdapter(
      endpoint = testEndpoint,
      apiKey = "",
      projectId = testProjectId
    )
    val result = adapter.addComment(123L, "comment")

    assert(result.isLeft)
    assertEquals(result.left.toOption.get, "REDMINE_API_KEY environment variable is not set")
  }

  test("getComments should return error when endpoint is empty") {
    val adapter = new RedmineApiAdapter(
      endpoint = "",
      apiKey = testApiKey,
      projectId = testProjectId
    )
    val result = adapter.getComments(123L)

    assert(result.isLeft)
    assertEquals(result.left.toOption.get, "REDMINE_ENDPOINT environment variable is not set")
  }

  test("getComments should return error when API key is empty") {
    val adapter = new RedmineApiAdapter(
      endpoint = testEndpoint,
      apiKey = "",
      projectId = testProjectId
    )
    val result = adapter.getComments(123L)

    assert(result.isLeft)
    assertEquals(result.left.toOption.get, "REDMINE_API_KEY environment variable is not set")
  }

  test("getUsers should return error when endpoint is empty") {
    val adapter = new RedmineApiAdapter(
      endpoint = "",
      apiKey = testApiKey,
      projectId = testProjectId
    )
    val result = adapter.getUsers()

    assert(result.isLeft)
    assertEquals(result.left.toOption.get, "REDMINE_ENDPOINT environment variable is not set")
  }

  test("getUsers should return error when API key is empty") {
    val adapter = new RedmineApiAdapter(
      endpoint = testEndpoint,
      apiKey = "",
      projectId = testProjectId
    )
    val result = adapter.getUsers()

    assert(result.isLeft)
    assertEquals(result.left.toOption.get, "REDMINE_API_KEY environment variable is not set")
  }
}
