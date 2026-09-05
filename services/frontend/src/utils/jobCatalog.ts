/**
 * Frontend-owned descriptions for every backend job type. The backend
 * intentionally ships only structural metadata (type, payload shape);
 * human-facing strings live here so the admin UI is the single source
 * of truth for what the operator sees.
 *
 * Every `JobDefinition` object the api declares needs an entry here:
 * `tests/unit/utils/jobCatalog.test.ts` reads those objects out of the Kotlin
 * sources and fails on either side's extras. Without an entry the UI still
 * shows the row, humanising the dot-separated type and saying nothing else.
 */

export type JobCatalogEntry = {
  /** Short title shown in lists, dropdowns and rows (e.g. "Sync contact"). */
  title: string
  /**
   * Plain-English paragraph: what does the job do, which subsystem does
   * it touch, and is it safe to re-run? Shown in the trigger dialog and
   * as a tooltip / expanded-row caption on JobManager rows.
   */
  description: string
}

export const JOB_CATALOG: Record<string, JobCatalogEntry> = {
  "email.recovery": {
    title: "Send recovery email",
    description:
      "Emails an account-recovery link to one user, carrying the supplied token. " +
      "Used by both the forgotten-password and member-activation flows.",
  },
  "email.event-signup": {
    title: "Send event sign-up email",
    description:
      "Sends the confirmation email for one event sign-up to the signer, including " +
      "their guest-access link. Triggered automatically after a sign-up; safe to re-run.",
  },
  "email.contribution-reminder": {
    title: "Send contribution reminder",
    description:
      "Emails one member paying by transfer the ask recorded against them: what they owe " +
      "for the period, why that amount applies and the date it is due by. Re-running sends " +
      "the ask again, which is what chasing a member looks like.",
  },
  "email.joining-contribution": {
    title: "Send joining contribution ask",
    description:
      "Emails a member who just joined the contribution ask made in the same breath as " +
      "their signup, quoting the fee type, the amount and the due date on the record. " +
      "The same record a contribution reminder sends, worded as a welcome rather than a " +
      "chase. Re-running sends the ask again.",
  },
  "email.incasso-notification": {
    title: "Send incasso notification",
    description:
      "Emails a member paying by direct debit what will be taken and on what date, quoting " +
      "the fee type and amount on the notification. It asks for nothing — the debit follows " +
      "either way. Re-running sends a second notification for the same debit.",
  },

  "contact.sync-all": {
    title: "Sync all contacts",
    description:
      "Walks every active user and enqueues a per-user contact sync against every " +
      "configured external contact system (today: Listmonk, Brevo). Use this to " +
      "recover from drift after a config change or a downstream outage.",
  },
  "contact.sync": {
    title: "Sync contact",
    description:
      "Pushes one user's current profile (name, email, opt-ins) to every configured " +
      "external contact system. Idempotent — a no-op if remote state already matches.",
  },
  "contact.remove": {
    title: "Remove contact",
    description:
      "Removes one user from every configured external contact system. Runs as part " +
      "of the user-deletion flow; idempotent when the remote contact is already gone.",
  },

  "calendar.sync-event": {
    title: "Sync calendar event",
    description:
      "Pushes one Blueshell event to the shared Google Calendar (create / update / " +
      "delete depending on its current state). Triggered by the event lifecycle " +
      "listener and safe to re-run manually.",
  },

  "cohort.membership-sync": {
    title: "Sync cohort membership",
    description:
      "Pushes one (user, cohort) pair to the cohort's external system — Brevo list, " +
      "Discord role, Google group, etc. The `intent` field decides whether the user is " +
      "added or removed there. Idempotent and runs against a single external system.",
  },
  "cohort.reconcile-contribution-periods": {
    title: "Reconcile contribution-period cohorts",
    description:
      "Walks every contribution period and makes sure all three of its cohorts — " +
      "contribution paid, members, and active members — exist locally. Does not touch " +
      "external systems on its own; subsequent user re-evaluations push any resulting " +
      "changes outward. Safe to run.",
  },
  "cohort.reconcile-all-users": {
    title: "Re-evaluate every user's cohorts",
    description:
      "Fans out one Re-evaluate-user-cohorts job per active user. Each child job " +
      "recomputes that user's cohort memberships from current facts (role, committee " +
      "membership, contribution payments, newsletter opt-in) and enqueues a per-member " +
      "membership-sync job for any cohort the user joins or leaves.",
  },
  "cohort.evaluate-user": {
    title: "Re-evaluate one user's cohorts",
    description:
      "Recomputes one user's desired cohort memberships locally, then enqueues a " +
      "per-member membership-sync job (add or remove) for each cohort the user joins " +
      "or leaves. Does not call external systems directly.",
  },
  "cohort.remove-external-member": {
    title: "Remove from external list",
    description:
      "Removes one external member from this mapping's external target " +
      "(e.g. a Brevo list). Used to clean up drift detected by the drift inspector.",
  },
  "cohort.reconcile-list": {
    title: "Verify cohort",
    description:
      "Fetches the full external member list for one cohort and verifies the ledger " +
      "against it: confirms members present, demotes ones that vanished, records " +
      "strangers, and enqueues ADD or contact-sync follow-ups for missing desired " +
      "members. One external call per run; extras are recorded for admin remediation.",
  },
  "cohort.delete-external-target": {
    title: "Delete external target",
    description:
      "Deletes one external target (e.g. a Brevo list) on its system. Enqueued when " +
      "switching a cohort to a different target with \"delete previous\" set; the " +
      "adapter treats an already-gone target as success.",
  },
  "cohort.inbound-reconcile-apply": {
    title: "Apply inbound reconcile",
    description:
      "Writes the strangers an admin picked out of one cohort's inbound-reconcile preview " +
      "into that cohort, as members. Each pick is applied in its own transaction and reports " +
      "its own outcome, so a run that half fails can be re-run for the rest.",
  },
  "cohort.materialize-target": {
    title: "Materialize cohort target",
    description:
      "Answers with the external target a cohort already has, and fails when it has none. " +
      "Creating a target is an operator's own action now, so nothing enqueues this any " +
      "more; it stays registered for rows queued before that changed.",
  },
}

export const humanizeJobType = (jobType: string): string =>
  jobType
    .replace(/[._-]+/g, " ")
    .trim()
    .split(/\s+/)
    .filter(Boolean)
    .map((token) => token.charAt(0).toUpperCase() + token.slice(1).toLowerCase())
    .join(" ")

export const jobCatalogEntry = (jobType: string): JobCatalogEntry => {
  const entry = JOB_CATALOG[jobType]
  if (entry) return entry
  return {title: humanizeJobType(jobType), description: ""}
}
