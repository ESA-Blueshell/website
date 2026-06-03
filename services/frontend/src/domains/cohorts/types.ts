import type { DriftReport as ApiDriftReport } from "@/services/api"

// Enum values sourced from the generated SDK — never hard-coded here.
export type TargetSystem = ApiDriftReport["system"]

// ── ExtraRow ─────────────────────────────────────────────────────────────────

export type KnownLocalUserExtra = {
  kind: "KNOWN_LOCAL_USER"
  externalUserId: string
  label: string | null
  userId: number
  fullName: string | null
  email: string | null
  softDeleted: boolean
}

export type UnknownExternalExtra = {
  kind: "UNKNOWN_EXTERNAL"
  externalUserId: string
  label: string | null
}

export type ExtraRow = KnownLocalUserExtra | UnknownExternalExtra

// ── MissingRow ────────────────────────────────────────────────────────────────

export type MissingRow = {
  userId: number
  hasExternalMapping: boolean
}

// ── DriftReport ───────────────────────────────────────────────────────────────

export type DriftReport = {
  cohortId: number
  system: TargetSystem
  externalCohortId: string | null
  extras: ExtraRow[]
  missing: MissingRow[]
  lastReconciledAt: string | null
}

// ── Conflict ──────────────────────────────────────────────────────────────────

export type ExternalUserConflict = {
  existingUserId: number
  system: string
  existingUserFullName: string | null
}

// ── Target (PR B) ─────────────────────────────────────────────────────────────

export type TargetRef = {
  externalId: string
  label: string
  memberCount: number | null
  folder: string | null
}
