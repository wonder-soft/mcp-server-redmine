package com.wonder_soft.mcp.redmine.domain

import munit.FunSuite
import io.circe.parser.*
import io.circe.syntax.*
import io.circe.generic.auto.*

class RedmineModelsTest extends FunSuite {

  test("RedmineAssignee should decode from JSON") {
    val json = """{"id": 1, "name": "John Doe"}"""
    val result = decode[RedmineAssignee](json)

    assert(result.isRight)
    val assignee = result.toOption.get
    assertEquals(assignee.id, 1L)
    assertEquals(assignee.name, "John Doe")
  }

  test("RedmineStatus should decode from JSON") {
    val json = """{"id": 1, "name": "New"}"""
    val result = decode[RedmineStatus](json)

    assert(result.isRight)
    val status = result.toOption.get
    assertEquals(status.id, 1L)
    assertEquals(status.name, "New")
  }

  test("RedmineProject should decode from JSON") {
    val json = """{"id": 1, "name": "Test Project"}"""
    val result = decode[RedmineProject](json)

    assert(result.isRight)
    val project = result.toOption.get
    assertEquals(project.id, 1L)
    assertEquals(project.name, "Test Project")
  }

  test("RedmineTracker should decode from JSON") {
    val json = """{"id": 1, "name": "Bug"}"""
    val result = decode[RedmineTracker](json)

    assert(result.isRight)
    val tracker = result.toOption.get
    assertEquals(tracker.id, 1L)
    assertEquals(tracker.name, "Bug")
  }

  test("RedmineIssueData should decode from JSON with all fields") {
    val json = """{
      "id": 123,
      "subject": "Test Issue",
      "description": "This is a test",
      "status": {"id": 1, "name": "New"},
      "project": {"id": 1, "name": "Test Project"},
      "assigned_to": {"id": 1, "name": "John Doe"},
      "tracker": {"id": 2, "name": "Feature"}
    }"""
    val result = decode[RedmineIssueData](json)

    assert(result.isRight, s"Decoding failed: ${result.left.toOption}")
    val issue = result.toOption.get
    assertEquals(issue.id, 123L)
    assertEquals(issue.subject, Some("Test Issue"))
    assertEquals(issue.description, Some("This is a test"))
    assertEquals(issue.status.name, "New")
    assertEquals(issue.project.name, "Test Project")
    assert(issue.assigned_to.isDefined)
    assertEquals(issue.assigned_to.get.name, "John Doe")
    assert(issue.tracker.isDefined)
    assertEquals(issue.tracker.get.id, 2L)
    assertEquals(issue.tracker.get.name, "Feature")
  }

  test("RedmineIssueData should decode from JSON without optional fields") {
    val json = """{
      "id": 123,
      "status": {"id": 1, "name": "New"},
      "project": {"id": 1, "name": "Test Project"}
    }"""
    val result = decode[RedmineIssueData](json)

    assert(result.isRight, s"Decoding failed: ${result.left.toOption}")
    val issue = result.toOption.get
    assertEquals(issue.id, 123L)
    assertEquals(issue.subject, None)
    assertEquals(issue.description, None)
    assertEquals(issue.assigned_to, None)
    assertEquals(issue.tracker, None)
  }

  test("RedmineTicketResponse should decode full issue response") {
    val json = """{
      "issue": {
        "id": 456,
        "subject": "Parent Ticket",
        "description": "Description here",
        "status": {"id": 2, "name": "In Progress"},
        "project": {"id": 10, "name": "Main Project"}
      }
    }"""
    val result = decode[RedmineTicketResponse](json)

    assert(result.isRight, s"Decoding failed: ${result.left.toOption}")
    val response = result.toOption.get
    assertEquals(response.issue.id, 456L)
    assertEquals(response.issue.subject, Some("Parent Ticket"))
  }

  test("RedmineChildTicketsResponse should decode list of issues") {
    val json = """{
      "issues": [
        {
          "id": 1,
          "subject": "Issue 1",
          "status": {"id": 1, "name": "New"},
          "project": {"id": 1, "name": "Project"}
        },
        {
          "id": 2,
          "subject": "Issue 2",
          "status": {"id": 2, "name": "In Progress"},
          "project": {"id": 1, "name": "Project"}
        }
      ],
      "total_count": 2,
      "offset": 0,
      "limit": 25
    }"""
    val result = decode[RedmineChildTicketsResponse](json)

    assert(result.isRight, s"Decoding failed: ${result.left.toOption}")
    val response = result.toOption.get
    assertEquals(response.issues.length, 2)
    assertEquals(response.total_count, 2)
    assertEquals(response.offset, 0)
    assertEquals(response.limit, 25)
  }

  test("RedmineUser should decode from JSON") {
    val json = """{
      "id": 1,
      "login": "jdoe",
      "firstname": "John",
      "lastname": "Doe",
      "mail": "jdoe@example.com",
      "status": 1
    }"""
    val result = decode[RedmineUser](json)

    assert(result.isRight, s"Decoding failed: ${result.left.toOption}")
    val user = result.toOption.get
    assertEquals(user.id, 1L)
    assertEquals(user.login, "jdoe")
    assertEquals(user.firstname, "John")
    assertEquals(user.lastname, "Doe")
    assertEquals(user.mail, Some("jdoe@example.com"))
    assertEquals(user.status, Some(1))
  }

  test("RedmineUsersResponse should decode list of users") {
    val json = """{
      "users": [
        {"id": 1, "login": "user1", "firstname": "First1", "lastname": "Last1"},
        {"id": 2, "login": "user2", "firstname": "First2", "lastname": "Last2"}
      ],
      "total_count": 2,
      "offset": 0,
      "limit": 25
    }"""
    val result = decode[RedmineUsersResponse](json)

    assert(result.isRight, s"Decoding failed: ${result.left.toOption}")
    val response = result.toOption.get
    assertEquals(response.users.length, 2)
    assertEquals(response.total_count, 2)
  }

  test("RedmineJournal should decode comment with details") {
    val json = """{
      "id": 1,
      "user": {"id": 1, "name": "John Doe"},
      "notes": "This is a comment",
      "created_on": "2024-01-15T10:30:00Z",
      "private_notes": false,
      "details": [
        {
          "property": "attr",
          "name": "status_id",
          "old_value": "1",
          "new_value": "2"
        }
      ]
    }"""
    val result = decode[RedmineJournal](json)

    assert(result.isRight, s"Decoding failed: ${result.left.toOption}")
    val journal = result.toOption.get
    assertEquals(journal.id, 1L)
    assertEquals(journal.user.name, "John Doe")
    assertEquals(journal.notes, Some("This is a comment"))
    assertEquals(journal.details.length, 1)
    assertEquals(journal.details.head.name, "status_id")
  }

  test("RedmineTicketWithJournalsResponse should decode issue with journals") {
    val json = """{
      "issue": {
        "id": 123,
        "subject": "Test Issue",
        "status": {"id": 1, "name": "New"},
        "project": {"id": 1, "name": "Project"},
        "journals": [
          {
            "id": 1,
            "user": {"id": 1, "name": "User"},
            "notes": "Comment 1",
            "created_on": "2024-01-15T10:00:00Z",
            "private_notes": false,
            "details": []
          }
        ]
      }
    }"""
    val result = decode[RedmineTicketWithJournalsResponse](json)

    assert(result.isRight, s"Decoding failed: ${result.left.toOption}")
    val response = result.toOption.get
    assertEquals(response.issue.id, 123L)
    assertEquals(response.issue.journals.length, 1)
    assertEquals(response.issue.journals.head.notes, Some("Comment 1"))
  }

  test("RedmineRelationData should decode relation") {
    val json = """{
      "id": 1,
      "issue_id": 100,
      "issue_to_id": 200,
      "relation_type": "relates"
    }"""
    val result = decode[RedmineRelationData](json)

    assert(result.isRight, s"Decoding failed: ${result.left.toOption}")
    val relation = result.toOption.get
    assertEquals(relation.id, 1L)
    assertEquals(relation.issue_id, Some(100L))
    assertEquals(relation.issue_to_id, Some(200L))
    assertEquals(relation.relation_type, "relates")
  }

  test("RedmineTicketCreateRequest should encode to JSON") {
    val request = RedmineTicketCreateRequest(
      subject = "New Ticket",
      description = Some("Description"),
      parent_issue_id = Some(100L),
      assigned_to_id = Some(1L),
      due_date = Some("2024-12-31")
    )
    val json = request.asJson

    assertEquals(json.hcursor.get[String]("subject").toOption, Some("New Ticket"))
    assertEquals(json.hcursor.get[String]("description").toOption, Some("Description"))
    assertEquals(json.hcursor.get[Long]("parent_issue_id").toOption, Some(100L))
    assertEquals(json.hcursor.get[Long]("assigned_to_id").toOption, Some(1L))
    assertEquals(json.hcursor.get[String]("due_date").toOption, Some("2024-12-31"))
  }

  test("RedmineTicketUpdateRequest should encode to JSON") {
    val request = RedmineTicketUpdateRequest(
      id = 123L,
      subject = Some("Updated Subject"),
      status_id = Some(2L)
    )
    val json = request.asJson

    assertEquals(json.hcursor.get[Long]("id").toOption, Some(123L))
    assertEquals(json.hcursor.get[String]("subject").toOption, Some("Updated Subject"))
    assertEquals(json.hcursor.get[Long]("status_id").toOption, Some(2L))
  }

  test("RedmineCreatedIssueData should decode created issue response") {
    val json = """{
      "id": 789,
      "subject": "Created Ticket",
      "description": "New description",
      "project": {"id": 1, "name": "Project"},
      "parent": {"id": 100}
    }"""
    val result = decode[RedmineCreatedIssueData](json)

    assert(result.isRight, s"Decoding failed: ${result.left.toOption}")
    val issue = result.toOption.get
    assertEquals(issue.id, 789L)
    assertEquals(issue.subject, "Created Ticket")
    assertEquals(issue.parent.map(_.id), Some(100L))
  }
}
