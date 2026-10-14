import {type Addressee, AddresseeKind} from "@/services/api"

/** One picker key per addressee, such as ROLE:BOARD, so cohorts, roles and people share one list. */
export const addresseeKey = (one: Addressee): string => `${one.kind}:${one.id}`

/** The addressee a picker key names, or nothing for a key it did not make. */
export function addresseeOf(key: string): Addressee | null {
  const at = key.indexOf(":")
  const kind = key.slice(0, at) as AddresseeKind
  return at > 0 && Object.values(AddresseeKind).includes(kind) ? {kind, id: key.slice(at + 1)} : null
}
