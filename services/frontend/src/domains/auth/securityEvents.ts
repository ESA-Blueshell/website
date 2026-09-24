import {DateTime} from "luxon"
import {SecurityActorKind, type SecurityEventResponse} from "@/services/api"

const KINDS: Record<SecurityEventResponse["kind"], string> = {
  SIGNED_IN: "Signed in",
  NEW_BROWSER: "Signed in from a browser not used before",
  SIGN_IN_REUSED: "A sign-in was ended because an old copy of its cookie was used",
  SIGN_IN_BROWSER_CHANGED: "A sign-in was ended because it turned up in another browser",
  CODE_LIMIT_REACHED: "Ten wrong two-factor codes",
  PASSWORD_RESET: "Password reset by email",
  PASSWORD_CHANGED: "Password changed",
  EMAIL_CHANGE_REQUESTED: "Asked to move to another email address",
  EMAIL_CHANGED: "Email address changed",
  EMAIL_CHANGED_BY_BOARD: "Email address changed",
  TWO_FACTOR_ON: "Two-factor turned on",
  TWO_FACTOR_OFF: "Two-factor turned off",
  TWO_FACTOR_REPLACED: "Authenticator app replaced",
  BACKUP_CODES_REGENERATED: "New backup codes made",
  BACKUP_CODE_USED: "Backup code used",
  TRUSTED_BROWSER_ADDED: "Browser trusted",
  TWO_FACTOR_RESET: "Two-factor reset",
  REENROLLED: "Signed in with the re-enrolment link",
  ACCOUNT_LOCKED: "Account locked",
  BREAK_GLASS: "Break-glass command used",
  ACCOUNT_UNLOCKED: "Account unlocked",
  SIGNED_OUT_EVERYWHERE: "Signed out everywhere",
  SIGNED_OUT_ELSEWHERE: "Signed out everywhere else",
  ROLES_CHANGED: "Roles changed",
}

/** One line of the security log, in words: what happened, and who did it when it was not the person. */
export function describeSecurityEvent(event: SecurityEventResponse): string {
  const what = KINDS[event.kind] ?? event.kind
  if (event.actorKind === SecurityActorKind.OPERATOR) return `${what}, by an operator`
  return event.actorName ? `${what}, by ${event.actorName}` : what
}

/** Fewer backup codes than this left, and the person is asked to make new ones. */
export const LOW_BACKUP_CODES = 4

export const formatSecurityTime = (iso: string): string => DateTime.fromISO(iso).toLocaleString(DateTime.DATETIME_MED)

export const describeBrowser = (browser: string, platform: string): string => `${browser} on ${platform}`

/** When and where an event happened, and the reason given for it where there is one. */
export function describeSecurityEventContext(event: SecurityEventResponse): string {
  const where = event.browser && event.platform ? `, ${describeBrowser(event.browser, event.platform)}` : ""
  const why = event.note ? ` · ${event.note}` : ""
  return `${formatSecurityTime(event.occurredAt)}${where}${why}`
}
