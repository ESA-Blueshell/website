/**
 * The contribution domain's public API: its own files import each other directly, and anything
 * outside it comes through here (frontend ADR-001). Re-exported by name rather than with
 * `export *`, because the list of names is the promise being made.
 */
export {
  ContributionEmailKind,
  type ContributionPeriodResponse,
  type CreateContributionPeriodRequest,
  type PeriodStanding,
  type UpdateContributionPeriodRequest,
} from "@/services/api"
export {reminderName, reminderRows, type ReminderRow} from "./reminders"
export {
  IncassoLeftOut,
  fetchIncassoFile,
  readIncassoPlan,
  readIncassoRun,
  saveSubmitted,
  startIncasso,
  type IncassoCandidate,
  type IncassoCollection,
  type IncassoRunSummary,
  type IncassoRunView,
} from "./adapters/incasso"
export {dayName, defaultStatementText, incassoFileName, leftOutGroups, leftOutHelp, leftOutLabels, maskedIban, renamedForIng} from "./incasso"
export {deletePeriod, listPeriods, readCurrentPeriod, readPeriodStanding, saveNewPeriod, savePeriod} from "./adapters/periods"
export {
  readOneEmail,
  readSelection,
  sendTheEmails,
  type ReadEmailQuery,
  type SendPaymentEmailsBody,
} from "./adapters/paymentEmails"
export {recordPaid, recordUnpaid, type BulkContributionCall} from "./adapters/contributions"
export {
  listMemberContributions,
  readFirstContribution,
  listPaidUserIds,
  readPeriodContributions,
  recordPayment,
  withdrawPayment,
  type FirstContribution,
  type MemberPeriodContribution,
  type PeriodContributionsView,
  type PeriodMember,
} from "./adapters/memberContributions"
export {contributionEmailLabels} from "./paymentEmail"
