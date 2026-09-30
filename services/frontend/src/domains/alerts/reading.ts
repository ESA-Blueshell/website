import {type Alert, AlertKind} from "./adapters/alerts"

const counted = (count: number, one: string, many: string): string => `${count} ${count === 1 ? one : many}`

/** What an alert says, composed here from what the api sends. */
export function alertTitle(alert: Alert): string {
  const subject = alert.subjectLabel ?? "A list"
  switch (alert.kind) {
    case AlertKind.TARGET_DRIFT:
      return `${subject} is out of step with Brevo: ${counted(alert.count, "person differs", "people differ")}`
    case AlertKind.COHORT_WITHOUT_LIST:
      return `${alert.subjectLabel ?? "A cohort"} has no Brevo list`
    case AlertKind.EMAIL_FAILED:
      return `${counted(alert.count, "email", "emails")} failed or bounced this month`
    case AlertKind.JOB_DEAD:
      return `${counted(alert.count, "job is", "jobs are")} dead`
    case AlertKind.EXCEPTION_OPEN:
      return `${counted(alert.count, "exception is", "exceptions are")} open`
    case AlertKind.ROLE_AWAITING_TWO_FACTOR:
      return `@${alert.subjectLabel}'s granted role waits on two-factor`
  }
}

/** Where an alert is dealt with. */
export function alertLink(alert: Alert): string {
  switch (alert.kind) {
    case AlertKind.TARGET_DRIFT:
    case AlertKind.COHORT_WITHOUT_LIST:
      return alert.subjectId == null ? "/management/platforms/brevo/lists" : `/management/platforms/brevo/cohort/${alert.subjectId}`
    case AlertKind.EMAIL_FAILED:
      return "/management/mail/sent"
    case AlertKind.JOB_DEAD:
      return "/management/jobs?status=DEAD"
    case AlertKind.EXCEPTION_OPEN:
      return "/management/exceptions"
    case AlertKind.ROLE_AWAITING_TWO_FACTOR:
      return `/management/users?search=${encodeURIComponent(alert.subjectLabel ?? "")}`
  }
}
