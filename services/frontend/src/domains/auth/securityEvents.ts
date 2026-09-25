import {DateTime} from "luxon"
import {SecurityActorKind, SecurityEventKind, type SecurityEventResponse} from "@/services/api"

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

/** What happened, and who did it when it was not the person. */
export function securityEventParts(event: SecurityEventResponse): {what: string, who: string} {
  const what = KINDS[event.kind] ?? event.kind
  if (event.actorKind === SecurityActorKind.OPERATOR) return {what, who: "by an operator"}
  return {what, who: event.actorName ? `by ${event.actorName}` : ""}
}

/** One line of the security log, in words. */
export function describeSecurityEvent(event: SecurityEventResponse): string {
  const {what, who} = securityEventParts(event)
  return who ? `${what}, ${who}` : what
}

/** Fewer backup codes than this left, and the person is asked to make new ones. */
export const LOW_BACKUP_CODES = 4

export const formatSecurityTime = (iso: string): string => DateTime.fromISO(iso).toLocaleString(DateTime.DATETIME_MED)

export const formatSecurityDay = (iso: string): string => DateTime.fromISO(iso).toFormat("ccc d LLL")

export const formatSecurityClock = (iso: string): string => DateTime.fromISO(iso).toFormat("HH:mm")

/** A moment as somebody says it: today or yesterday at a time, or the day further back. */
export function formatSecurityMoment(iso: string, now = DateTime.now()): string {
  const at = DateTime.fromISO(iso)
  if (at.hasSame(now, "day")) return `today at ${at.toFormat("HH:mm")}`
  if (at.hasSame(now.minus({days: 1}), "day")) return `yesterday at ${at.toFormat("HH:mm")}`
  return at.toFormat("ccc d LLL")
}

/** The log in days, newest first, each named as a reader would say it. */
export function securityLogByDay(events: SecurityEventResponse[], now = DateTime.now()) {
  const days: {key: string, name: string, events: SecurityEventResponse[]}[] = []
  for (const event of events) {
    const at = DateTime.fromISO(event.occurredAt)
    const key = at.toISODate()!
    const last = days.at(-1)
    if (last?.key === key) last.events.push(event)
    else days.push({key, name: dayName(at, now), events: [event]})
  }
  return days
}

function dayName(at: DateTime, now: DateTime): string {
  if (at.hasSame(now, "day")) return "Today"
  if (at.hasSame(now.minus({days: 1}), "day")) return "Yesterday"
  return at.toFormat("ccc d LLL")
}

const signingIn = (kind: SecurityEventKind) => kind === SecurityEventKind.SIGNED_IN || kind === SecurityEventKind.NEW_BROWSER

/** The newest entry that changed something, rather than somebody signing in. */
export const lastChangeIn = (events: SecurityEventResponse[]): SecurityEventResponse | undefined =>
  events.find(event => !signingIn(event.kind))

export const describeBrowser = (browser: string, platform: string): string => `${browser} on ${platform}`

/** When and where an event happened, and the reason given for it where there is one. */
export function describeSecurityEventContext(event: SecurityEventResponse): string {
  const where = event.browser && event.platform ? `, ${describeBrowser(event.browser, event.platform)}` : ""
  const why = event.note ? ` · ${event.note}` : ""
  return `${formatSecurityTime(event.occurredAt)}${where}${why}`
}
