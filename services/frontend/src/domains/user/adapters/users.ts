/**
 * User domain adapter — the only file in this domain that imports from @/services/api
 * (per frontend ADR-002). Everything else imports from here.
 */
import {findUsers} from "@/services/api"

/** A member as the thing attaching them needs to name them: who they are, and how to tell two apart. */
export interface Member {
  id: number
  name: string
  email: string | null
}

/**
 * The members something can be attached to.
 *
 * Asked for once and filtered where it is used, the way the rest of the site's member pickers
 * work. Attaching a roster entry or a board seat is rare enough that a search round trip per
 * keystroke would buy nothing.
 */
export async function loadMembers(): Promise<Member[]> {
  const res = await findUsers({query: {size: 500}})
  return (res.data?.content ?? [])
    .filter(user => user.id != null)
    .map(user => ({
      id: user.id as number,
      name: user.fullName ?? user.email ?? `Member ${user.id}`,
      email: user.email ?? null,
    }))
    .sort((a, b) => a.name.localeCompare(b.name))
}
