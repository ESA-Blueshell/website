/**
 * The auth domain's public API: its own files import each other directly, and anything outside
 * it comes through here (frontend ADR-001).
 */
export {answerChallenge, reenrol, signIn, stepUp, type CodeResult, type SignInResult} from "./adapters/auth"
export {
  answerOffer,
  askToMoveEmail,
  confirmNewEmail,
  confirmTwoFactorCode,
  endEverySignIn,
  endOneSignIn,
  finishTwoFactorSetUp,
  forgetEveryTrustedBrowser,
  forgetOneTrustedBrowser,
  listSignIns,
  listTrustedBrowsers,
  lockAccount,
  newBackupCodes,
  readAccountStanding,
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
export {describeSecurityEvent} from "./securityEvents"
export {default as AccountSecurityDialog} from "./components/AccountSecurityDialog.vue"
export {default as BackupCodes} from "./components/BackupCodes.vue"
export {default as StepUpDialog} from "./components/StepUpDialog.vue"
export {default as TwoFactorSetUp} from "./components/TwoFactorSetUp.vue"
export {reasonFor as accountSecurityReason} from "./refusals"
export type {
  AccountStandingResponse,
  LoginResponse,
  SecurityEventResponse,
  SignInResponse,
  TrustedBrowserResponse,
  TwoFactorStanding,
} from "@/services/api"
