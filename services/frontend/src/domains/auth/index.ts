/**
 * The auth domain's public API: its own files import each other directly, and anything outside
 * it comes through here (frontend ADR-001).
 */
export {answerChallenge, reenrol, signIn, signOut, stepUp, type CodeResult, type SignInResult} from "./adapters/auth"
export {
  answerOffer,
  askToMoveEmail,
  confirmNewEmail,
  confirmTwoFactorCode,
  endEverySignIn,
  endOtherSignIns,
  endOneSignIn,
  finishTwoFactorSetUp,
  forgetEveryTrustedBrowser,
  forgetOneTrustedBrowser,
  listSignIns,
  listTrustedBrowsers,
  lockAccount,
  newBackupCodes,
  readAccountStanding,
  readEmailAddress,
  readMySecurityLog,
  readSecurityLogOf,
  readTwoFactor,
  removeTwoFactor,
  resendReenrolment,
  resetTwoFactorOf,
  savePassword,
  startTwoFactorSetUp,
  unlockAccount,
  type Written,
} from "./adapters/accountSecurity"
export {
  describeBrowser,
  describeSecurityEvent,
  describeSecurityEventContext,
  formatSecurityClock,
  formatSecurityDay,
  formatSecurityMoment,
  formatSecurityTime,
  lastChangeIn,
  BACKUP_CODES_ISSUED,
  LOW_BACKUP_CODES,
  sayCount,
  securityEventParts,
  securityLogByDay,
} from "./securityEvents"
export {SECURITY_CRUMB, SECURITY_PAGES} from "./securityPages"
export {useStepUp} from "./composables/useStepUp"
export {default as AccountSecurityDialog} from "./components/AccountSecurityDialog.vue"
export {default as BackupCodes} from "./components/BackupCodes.vue"
export {default as BackupCodesBanner} from "./components/BackupCodesBanner.vue"
export {default as SecurityGlyph} from "./components/SecurityGlyph.vue"
export {default as StepUpDialog} from "./components/StepUpDialog.vue"
export {default as TwoFactorSetUp} from "./components/TwoFactorSetUp.vue"
export {needsStepUp, reasonFor as accountSecurityReason} from "./refusals"
export type {
  AccountStandingResponse,
  EmailAddressResponse,
  LoginResponse,
  SecurityEventResponse,
  SignInResponse,
  TrustedBrowserResponse,
  TwoFactorStanding,
} from "@/services/api"
