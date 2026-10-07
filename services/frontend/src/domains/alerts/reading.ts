import {type Alert, AlertKind} from "./adapters/alerts"

const counted = (count: number, one: string, many: string): string => `${count} ${count === 1 ? one : many}`

/** What an alert says, composed here from what the api sends. */
export function alertTitle(alert: Alert): string {
  const subject = alert.subjectLabel ?? "A list"
  switch (alert.kind) {
    case AlertKind.TARGET_DRIFT:
      return `${subject} is out of sync with Brevo: ${counted(alert.count, "person differs", "people differ")}`
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
    case AlertKind.DISCORD_BOT_PERMISSIONS:
      return `The Discord bot lacks ${counted(alert.count, "permission", "permissions")}: ${alert.subjectLabel}`
    case AlertKind.BREVO_FOLDERS_SHARE_NAME:
      return `Brevo has folders that share a name: ${alert.subjectLabel}`
    case AlertKind.DISCORD_ROLES_ABOVE_BOT:
      return `${counted(alert.count, "linked role sits", "linked roles sit")} above the bot's role: ${alert.subjectLabel}`
    case AlertKind.DISCORD_CHANNELS_BEYOND_BOT:
      return `The bot cannot keep who ${counted(alert.count, "channel", "channels")} ${alert.count === 1 ? "is" : "are"} open to: ${alert.subjectLabel}`
  }
}

/** An alert as a short row: what is wrong, what it is about, and the page it comes from. */
export function alertRow(alert: Alert): {name: string; meta: string; from: string} {
  switch (alert.kind) {
    case AlertKind.TARGET_DRIFT:
      return {name: "A list is out of sync", meta: `${alert.subjectLabel ?? "A list"} · ${counted(alert.count, "person differs", "people differ")}`, from: "Brevo"}
    case AlertKind.COHORT_WITHOUT_LIST:
      return {name: "A Brevo list is missing", meta: alert.subjectLabel ?? "A cohort", from: "Brevo"}
    case AlertKind.EMAIL_FAILED:
      return {name: `${counted(alert.count, "email", "emails")} failed or bounced`, meta: "This month", from: "Sent"}
    case AlertKind.JOB_DEAD:
      return {name: `${counted(alert.count, "job is", "jobs are")} dead`, meta: "After every retry", from: "Jobs"}
    case AlertKind.EXCEPTION_OPEN:
      return {name: `${counted(alert.count, "exception is", "exceptions are")} open`, meta: "Not resolved yet", from: "Exceptions"}
    case AlertKind.ROLE_AWAITING_TWO_FACTOR:
      return {name: "A role waits on two-factor", meta: `@${alert.subjectLabel}`, from: "Users"}
    case AlertKind.DISCORD_BOT_PERMISSIONS:
      return {name: `The bot lacks ${counted(alert.count, "permission", "permissions")}`, meta: alert.subjectLabel ?? "", from: "Discord"}
    case AlertKind.BREVO_FOLDERS_SHARE_NAME:
      return {name: "Folders share a name", meta: alert.subjectLabel ?? "", from: "Brevo"}
    case AlertKind.DISCORD_ROLES_ABOVE_BOT:
      return {name: `${counted(alert.count, "role sits", "roles sit")} above the bot`, meta: alert.subjectLabel ?? "", from: "Discord"}
    case AlertKind.DISCORD_CHANNELS_BEYOND_BOT:
      return {name: `The bot cannot keep ${counted(alert.count, "channel", "channels")}`, meta: alert.subjectLabel ?? "", from: "Discord"}
  }
}

/** Where an alert is dealt with. */
export function alertLink(alert: Alert): string {
  switch (alert.kind) {
    case AlertKind.BREVO_FOLDERS_SHARE_NAME:
      return "/management/platforms/brevo"
    case AlertKind.TARGET_DRIFT:
    case AlertKind.COHORT_WITHOUT_LIST:
      return alert.subjectId == null ? "/management/platforms/brevo" : `/management/platforms/brevo/cohort/${alert.subjectId}`
    case AlertKind.EMAIL_FAILED:
      return "/management/mail/sent"
    case AlertKind.JOB_DEAD:
      return "/management/jobs?status=DEAD"
    case AlertKind.EXCEPTION_OPEN:
      return "/management/exceptions"
    case AlertKind.ROLE_AWAITING_TWO_FACTOR:
      return `/management/users/${alert.subjectId}`
    case AlertKind.DISCORD_BOT_PERMISSIONS:
    case AlertKind.DISCORD_ROLES_ABOVE_BOT:
    case AlertKind.DISCORD_CHANNELS_BEYOND_BOT:
      return "/management/platforms/discord/bot"
  }
}
