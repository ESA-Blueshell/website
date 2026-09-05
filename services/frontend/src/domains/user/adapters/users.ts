/**
 * User domain adapter — the only file in this domain that imports from @/services/api
 * (per frontend ADR-002). Everything else imports from here.
 */
import {findUsers} from "@/services/api"

/**
 * An account here, as the thing attaching one needs to name it: who it belongs to, and how to
 * tell two of them apart. Named for the account rather than for the member, because what
 * attaches one is a board membership or a roster entry, which is a member of its own.
 */
export interface MemberAccount {
  id: number
  name: string
  email: string | null
}

/**
 * The accounts something can be attached to, or nothing at all where the api would not say.
 *
 * Asked for once and filtered where it is used, the way the rest of the site's member pickers
 * work. Attaching a roster entry or a board membership is rare enough that a search round trip per
 * keystroke would buy nothing.
 *
 * A list that could not be read is not an empty one, and a picker has to tell them apart: read as
 * emptiness, a refused request tells a board member that nobody here has an account.
 */
export async function loadMemberAccounts(): Promise<MemberAccount[] | null> {
  const res = await findUsers({query: {size: 500}})
  if (res.error || !res.data?.content) return null
  return res.data.content
    .filter(user => user.id != null)
    .map(user => ({
      id: user.id as number,
      name: user.fullName ?? user.email ?? `Member ${user.id}`,
      email: user.email ?? null,
    }))
    .sort((a, b) => a.name.localeCompare(b.name))
}
