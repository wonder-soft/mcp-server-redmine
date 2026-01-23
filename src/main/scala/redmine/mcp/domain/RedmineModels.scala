package redmine.mcp.domain

import io.circe.{Encoder, Decoder}
import io.circe.generic.semiauto.*

case class RedmineAssignee(
  id: Long,
  name: String
)

case class RedmineTicket(
  id: Long,
  title: String,
  description: Option[String] = None,
  assignee: Option[RedmineAssignee] = None
)

case class RedmineTicketResponse(
  issue: RedmineIssueData
)

case class RedmineIssueData(
  id: Long,
  subject: Option[String],
  description: Option[String],
  status: RedmineStatus,
  project: RedmineProject,
  assigned_to: Option[RedmineAssignee] = None
)

case class RedmineStatus(
  id: Long,
  name: String
)

case class RedmineProject(
  id: Long,
  name: String
)

case class RedmineTicketCreateRequest(
  subject: String,
  description: Option[String] = None,
  parent_issue_id: Option[Long] = None,
  assigned_to_id: Option[Long] = None,
  tracker_id: Option[Long] = None,
  related_ticket_ids: Option[List[Long]] = None,
  due_date: Option[String] = None
)

case class RedmineTicketCreateResponse(
  issue: RedmineCreatedIssueData
)

case class RedmineCreatedIssueData(
  id: Long,
  subject: String,
  description: Option[String],
  project: RedmineProject,
  parent: Option[RedmineParentIssue] = None
)

case class RedmineParentIssue(
  id: Long
)

case class RedmineTicketUpdateRequest(
  id: Long,
  subject: Option[String] = None,
  description: Option[String] = None,
  status_id: Option[Long] = None,
  assigned_to_id: Option[Long] = None,
  tracker_id: Option[Long] = None,
  parent_issue_id: Option[Long] = None,
  related_ticket_ids: Option[List[Long]] = None,
  due_date: Option[String] = None
)

case class RedmineChildTicketsResponse(
  issues: List[RedmineIssueData],
  total_count: Int,
  offset: Int,
  limit: Int
)

case class RedmineSearchResponse(
  results: List[RedmineSearchResult],
  total_count: Int,
  offset: Int,
  limit: Int
)

case class RedmineSearchResult(
  id: Long,
  title: String,
  description: Option[String],
  url: String,
  `type`: String
)

case class RedmineRelationRequest(
  issue_to_id: Long,
  relation_type: String = "relates"
)

case class RedmineRelationResponse(
  relation: RedmineRelationData
)

case class RedmineRelationData(
  id: Long,
  issue_id: Option[Long],
  issue_to_id: Option[Long],
  relation_type: String
)

case class RedmineRelationsListResponse(
  relations: List[RedmineRelationData]
)

case class RedmineUser(
  id: Long,
  login: String,
  firstname: String,
  lastname: String,
  mail: Option[String] = None,
  status: Option[Int] = None
)

case class RedmineUsersResponse(
  users: List[RedmineUser],
  total_count: Int,
  offset: Int,
  limit: Int
)

// Redmine Journal (comments/history) models
case class RedmineJournalUser(
  id: Long,
  name: String
)

case class RedmineJournalDetail(
  property: String,
  name: String,
  old_value: Option[String] = None,
  new_value: Option[String] = None
)

case class RedmineJournal(
  id: Long,
  user: RedmineJournalUser,
  notes: Option[String] = None,
  created_on: String,
  private_notes: Boolean = false,
  details: List[RedmineJournalDetail] = List.empty
)

case class RedmineIssueWithJournals(
  id: Long,
  subject: Option[String] = None,
  description: Option[String] = None,
  status: RedmineStatus,
  project: RedmineProject,
  journals: List[RedmineJournal] = List.empty
)

case class RedmineTicketWithJournalsResponse(
  issue: RedmineIssueWithJournals
)

// Circe decoders
object RedmineAssignee {
  implicit val decoder: Decoder[RedmineAssignee] = deriveDecoder[RedmineAssignee]
}

object RedmineIssueData {
  implicit val decoder: Decoder[RedmineIssueData] = deriveDecoder[RedmineIssueData]
}

object RedmineJournalUser {
  implicit val decoder: Decoder[RedmineJournalUser] = deriveDecoder[RedmineJournalUser]
}

object RedmineJournalDetail {
  implicit val decoder: Decoder[RedmineJournalDetail] = deriveDecoder[RedmineJournalDetail]
}

object RedmineJournal {
  implicit val decoder: Decoder[RedmineJournal] = deriveDecoder[RedmineJournal]
}

object RedmineStatus {
  implicit val decoder: Decoder[RedmineStatus] = deriveDecoder[RedmineStatus]
}

object RedmineProject {
  implicit val decoder: Decoder[RedmineProject] = deriveDecoder[RedmineProject]
}

object RedmineIssueWithJournals {
  implicit val decoder: Decoder[RedmineIssueWithJournals] = deriveDecoder[RedmineIssueWithJournals]
}

object RedmineTicketWithJournalsResponse {
  implicit val decoder: Decoder[RedmineTicketWithJournalsResponse] = deriveDecoder[RedmineTicketWithJournalsResponse]
}
