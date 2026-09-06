/**
 * The contribution domain's public API: its own files import each other directly, and anything
 * outside it comes through here (frontend ADR-001). Re-exported by name rather than with
 * `export *`, because the list of names is the promise being made.
 */
export {ContributionEmailKind, type ContributionPeriodResponse} from "@/services/api"
export {
  readOneEmail,
  readSelection,
  sendTheEmails,
  type ReadEmailQuery,
  type SendPaymentEmailsBody,
} from "./adapters/paymentEmails"
export {recordPaid, recordUnpaid, type BulkContributionCall} from "./adapters/contributions"
export {
  changedFeeTypes,
  changedKinds,
  contributionEmailItems,
  contributionEmailLabels,
  countByKind,
  forcedUserIds,
  isReCharged,
  isSelectable,
  isSwitched,
  kindFor,
  lastAskedOn,
  lastSentOfKind,
  PERIOD_OVERHANG_MONTHS,
  paymentDateProblem,
  periodDateWindow,
  reChargedDescription,
  reapplyChoices,
  seedChoices,
  seedSendTo,
  summarise,
  switchedDescription,
  switchedNote,
  toBulkRow,
  toBulkRows,
  willSend,
  type FlaggedMember,
  type PaymentEmailChoices,
  type PaymentEmailSummary,
} from "./paymentEmail"
