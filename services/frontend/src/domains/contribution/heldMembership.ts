import {readonly, ref, watch, type Ref} from "vue"
import {type OwnContributions, readOwnContributions} from "./adapters/ownContributions"

/* Shared by every caller: the site bar and the account tabs both ask on every page, and one read
   per signed-in person answers them all. */
const held = ref(false)
let readFor: number | null = null

async function readHeld(userId: number | null): Promise<void> {
  if (userId === readFor) return
  readFor = userId
  held.value = false
  if (userId == null) return
  const own = await readOwnContributions()
  if (readFor !== userId) return
  held.value = own?.held === true
  // A read that failed is asked again by the next page that wants it.
  if (!own) readFor = null
}

/** Whether the signed-in person holds or held a membership, which is when the account pages offer Payment details. */
export function useHeldMembership(userId: () => number | null): Readonly<Ref<boolean>> {
  watch(userId, id => void readHeld(id), {immediate: true})
  return readonly(held)
}

/** A fresh read of the reader's contributions, so a membership started since the first read shows. */
export function noteHeld(own: OwnContributions): void {
  held.value = own.held
}
