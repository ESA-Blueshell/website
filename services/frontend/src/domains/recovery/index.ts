/**
 * The recovery domain's public API: its own files import each other directly, and anything
 * outside it comes through here (frontend ADR-001).
 */
export {
  activateMember,
  activateUser,
  listPendingActivations,
  previewRecoveryMail,
  requestPasswordReset,
  resendActivation,
  resendRecoveryMail,
  type ResendResult,
  restoreDeletedUser,
  setNewPassword,
} from "./adapters/recovery"
export {TokenPurpose} from "@/services/api"
export type {
  MemberActivationRequest,
  PasswordResetRequest,
  RecoveryEmailPreviewResponse,
} from "@/services/api"
