import type {SecurityEventResponse} from "@/services/api"

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
}

/** One line of the security log, in words: what happened, and who did it when it was not the person. */
export function describeSecurityEvent(event: SecurityEventResponse): string {
  const what = KINDS[event.kind] ?? event.kind
  if (event.actorKind === "OPERATOR") return `${what}, by an operator`
  return event.actorName ? `${what}, by ${event.actorName}` : what
}
