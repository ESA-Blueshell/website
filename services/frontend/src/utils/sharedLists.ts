import {ref, type Ref} from "vue"

/** What a write asks of everything it may have made stale. */
const afterWrites: Array<() => Promise<unknown> | void> = []

/**
 * A list the whole site reads once and shares: the games, the seasons, the committees.
 *
 * `read` asks the api the first time and hands every later caller the same answer. The list
 * signs itself up for [refreshSharedLists], so an editor that wrote something never has to know
 * which lists showed it.
 */
export function sharedList<T>(load: () => Promise<T[]>): {
  records: Ref<T[]>
  read: () => Promise<T[]>
  refresh: () => Promise<T[]>
  forget: () => void
} {
  const records = ref<T[]>([]) as Ref<T[]>
  let asked: Promise<T[]> | null = null
  const refresh = async () => {
    records.value = await load()
    return records.value
  }
  const read = () => (asked ??= refresh())
  // A list nobody has read yet has nothing stale in it, and reads itself when first asked.
  afterWrites.push(() => (asked ? (asked = refresh()) : undefined))
  return {
    records,
    read,
    refresh,
    forget: () => {
      records.value = []
      asked = null
    },
  }
}

/** Signs up a cache that is not a list, such as one keyed by game, to be dropped after a write. */
export function forgetAfterWrites(forget: () => void): void {
  afterWrites.push(forget)
}

/**
 * Brings every shared list and cache up to date after a write.
 *
 * The one call an editor makes once something is saved, whatever it wrote: a game, a season, a
 * team fielded somewhere new. The site bar's menus read the same lists as the pages, so they move
 * with it rather than waiting for a reload.
 */
export async function refreshSharedLists(): Promise<void> {
  await Promise.all(afterWrites.map(one => one()))
}
