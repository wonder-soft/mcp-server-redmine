package redmine.mcp.usecase

import redmine.mcp.adapter.RedmineApiAdapter
import redmine.mcp.domain.*

class RedmineUsecase {
  val adapter = new RedmineApiAdapter()

  def createTicket(
    subject: String,
    description: Option[String],
    parentTicketId: Option[Long],
    assignedToId: Option[Long] = None,
    relatedTicketIds: Option[List[Long]] = None,
    dueDate: Option[String] = None
  ): Either[String, RedmineCreatedIssueData] = {
    val request = RedmineTicketCreateRequest(
      subject = subject,
      description = description,
      parent_issue_id = parentTicketId,
      assigned_to_id = assignedToId,
      tracker_id = None,
      related_ticket_ids = relatedTicketIds,
      due_date = dueDate
    )
    adapter.createTicket(request)
  }

  def updateTicket(
    id: Long,
    subject: Option[String],
    description: Option[String],
    statusId: Option[Long],
    assignedToId: Option[Long],
    relatedTicketIds: Option[List[Long]] = None,
    parentIssueId: Option[Long] = None,
    dueDate: Option[String] = None,
    clearAssignee: Boolean = false
  ): Either[String, RedmineTicket] = {
    val request = RedmineTicketUpdateRequest(
      id = id,
      subject = subject,
      description = description,
      status_id = statusId,
      assigned_to_id = assignedToId,
      tracker_id = None,
      parent_issue_id = parentIssueId,
      related_ticket_ids = relatedTicketIds,
      due_date = dueDate
    )
    adapter.updateTicket(request, clearAssignee)
  }

  def addComment(ticketId: Long, comment: String, isPrivate: Boolean = false): Either[String, String] = {
    adapter.addComment(ticketId, comment, isPrivate)
  }

  def getComments(ticketId: Long): Either[String, List[RedmineJournal]] = {
    adapter.getComments(ticketId)
  }
}
