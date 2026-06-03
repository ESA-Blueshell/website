/**
 * Cohort domain adapter — the only file in this domain that imports
 * from @/services/api (per frontend ADR-002). Everything else imports
 * from this module or from ../types.
 */
import {
  getDrift,
  linkUser,
  listSystems,
  reconcile,
  removeExternal,
} from "@/services/api"
import type { DriftReport, ExternalUserConflict, TargetSystem } from "../types"
import type { ExtraRow } from "../types"

// ── Systems ───────────────────────────────────────────────────────────────────

export async function fetchSystems(): Promise<TargetSystem[]> {
  const res = await listSystems()
  return (res.data ?? []) as TargetSystem[]
}

// ── Drift ─────────────────────────────────────────────────────────────────────

export async function fetchDrift(subjectId: number, system: TargetSystem): Promise<DriftReport> {
  const res = await getDrift({ path: { id: subjectId }, query: { system } })
  const raw = res.data!
  return {
    cohortId: raw.cohortId,
    system: raw.system as TargetSystem,
    externalCohortId: raw.externalCohortId ?? null,
    extras: (raw.extras ?? []).map(toExtraRow),
    missing: (raw.missing ?? []).map((m) => ({
      userId: m.userId,
      hasExternalMapping: m.hasExternalMapping,
    })),
    lastReconciledAt: raw.lastReconciledAt ?? null,
  }
}

// ── Reconcile ──────────────────────────────────────────────────────────────────

export async function triggerReconcile(subjectId: number, system: TargetSystem): Promise<number | null> {
  const res = await reconcile({ path: { id: subjectId }, query: { system } })
  return res.data?.jobId ?? null
}

function toExtraRow(raw: {
  kind: string
  externalUserId: string
  label?: string
  userId?: number
  fullName?: string
  email?: string
  softDeleted?: boolean
}): ExtraRow {
  if (raw.kind === "KNOWN_LOCAL_USER" && raw.userId != null) {
    return {
      kind: "KNOWN_LOCAL_USER",
      externalUserId: raw.externalUserId,
      label: raw.label ?? null,
      userId: raw.userId,
      fullName: raw.fullName ?? null,
      email: raw.email ?? null,
      softDeleted: raw.softDeleted ?? false,
    }
  }
  return {
    kind: "UNKNOWN_EXTERNAL",
    externalUserId: raw.externalUserId,
    label: raw.label ?? null,
  }
}

// ── Remove external ───────────────────────────────────────────────────────────

export async function removeExternalMember(
  subjectId: number,
  cohortId: number,
  externalUserId: string,
): Promise<number | null> {
  const res = await removeExternal({
    path: { id: subjectId },
    body: { cohortId, externalUserId },
  })
  return res.data?.jobId ?? null
}

// ── Link user ─────────────────────────────────────────────────────────────────

export type LinkUserResult = { type: "ok" } | { type: "conflict"; conflict: ExternalUserConflict }

export async function linkUserToExternal(
  subjectId: number,
  userId: number,
  system: TargetSystem,
  externalUserId: string,
): Promise<LinkUserResult> {
  try {
    await linkUser({
      path: { id: subjectId },
      body: { userId, system, externalUserId },
    })
    return { type: "ok" }
  } catch (err: unknown) {
    const resp = (err as { response?: { status?: number; data?: Record<string, unknown> } })?.response
    if (resp?.status === 409 && resp.data) {
      const d = resp.data
      return {
        type: "conflict",
        conflict: {
          existingUserId: d["existingUserId"] as number,
          system: d["system"] as string,
          existingUserFullName: (d["existingUserFullName"] as string | null) ?? null,
        },
      }
    }
    throw err
  }
}
