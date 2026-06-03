/**
 * Frontend-owned descriptions for every backend job type. The backend
 * intentionally ships only structural metadata (type, payload shape);
 * human-facing strings live here so the admin UI is the single source
 * of truth for what the operator sees.
 *
 * Keep job types in sync with the `JobDefinition` objects in
 * `services/api/src/main/kotlin/net/blueshell/api/shared/job/`. If a row
 * appears in the catalog with no entry here, the UI falls back to a
 * humanised version of the dot-separated type string.
 */

export type JobCatalogEntry = {
  /** Short title shown in lists, dropdowns and rows (e.g. "Sync contact"). */
  title: string
  /** Deprecated but still registered for compatibility with existing queued/manual jobs. */
  legacy?: boolean
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
      "Emails one member a reminder that their contribution for the given period is " +
      "still outstanding. Idempotent: re-running just sends another reminder.",
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
      "Walks every contribution period and makes sure its 'contribution paid' cohort " +
      "and matching rule exist locally. Does not touch external systems on its own — " +
      "subsequent user re-evaluations push any resulting changes outward. Safe to run.",
  },
  "cohort.reconcile-all-users": {
    title: "Re-evaluate every user's cohorts",
    description:
      "Fans out one Re-evaluate-user-cohorts job per active user. Each child job " +
      "recomputes that user's cohort memberships from current facts (role, committee " +
      "membership, contribution payments, newsletter opt-in) and enqueues list " +
      "reconciliation for any cohort whose desired membership changed.",
  },
  "cohort.evaluate-user": {
    title: "Re-evaluate one user's cohorts",
    description:
      "Recomputes one user's desired cohort memberships locally, then enqueues " +
      "cohort-level list reconciliation for touched external mappings. Does not call " +
      "external systems directly.",
  },
  "cohort.remove-external-member": {
    title: "Remove from external list",
    description:
      "Removes one external member from this mapping's external target " +
      "(e.g. a Brevo list). Used to clean up drift detected by the drift inspector.",
  },
  "cohort.reconcile-list": {
    title: "Reconcile list",
    description:
      "Fetches the full external member list for one cohort mapping, updates the " +
      "membership ledger, and enqueues ADD or contact-sync follow-ups for missing " +
      "desired members. Extras are recorded for admin remediation.",
  },
  "cohort.delete-external-target": {
    title: "Delete external target",
    description:
      "Deletes one external target (e.g. a Brevo list) on its system. Enqueued when " +
      "switching a cohort to a different target with \"delete previous\" set; the " +
      "adapter treats an already-gone target as success.",
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
