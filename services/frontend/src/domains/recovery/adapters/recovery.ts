/**
 * Recovery domain adapter — the only file in this domain that imports from `@/services/api`
 * (frontend ADR-002).
 */
import {pendingActivations, type TokenPurpose} from "@/services/api"

/**
 * Which activation each account that has not been activated is waiting for, so the manager
 * offers the one that applies rather than both and a guess. Empty where the api would not say.
 */
export async function listPendingActivations(): Promise<Record<number, TokenPurpose>> {
  const {data} = await pendingActivations()
  return Object.fromEntries((data?.activations ?? []).map(one => [one.userId, one.purpose]))
}
