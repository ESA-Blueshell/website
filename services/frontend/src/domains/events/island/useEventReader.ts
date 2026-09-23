import {computed, type Ref, ref, watch} from "vue"
import {useStore} from "vuex"
import {DateTime} from "luxon"
import type {GuestSessionData, StoredLogin} from "@/plugins/store"
import {$handleNetworkError} from "@/plugins/handleNetworkError"
import {listCommittees, listMyCommittees} from "@/domains/committees"
import {type EventSignUpResponse, listOwnSignUps, listSignUpsByAccessToken} from ".."

export type CommitteeOption = {id: number, name: string}

/**
 * Who is reading an events page, as far as the page cares: their own sign-ups, and the
 * committees whose events they may manage (every committee, for the board).
 *
 * A guest is known by the access token their sign-up mail carried, which a page may hand in
 * from its own address. Both are read again whenever the reader logs in or out.
 */
export function useEventReader(linkedToken: Ref<string | null> = ref(null)): {
  signUps: Ref<EventSignUpResponse[]>
  committees: Ref<CommitteeOption[]>
} {
  const store = useStore()
  const signUps = ref<EventSignUpResponse[]>([])
  const committees = ref<CommitteeOption[]>([])

  const isLoggedIn = computed<boolean>(() => store.getters.isLoggedIn)
  const isBoard = computed<boolean>(() => store.getters.isBoard)
  const login = computed<StoredLogin | undefined>(() => store.getters.getLogin)
  const guestToken = computed<string | null>(() =>
    (store.getters.getGuestData as GuestSessionData | null)?.accessToken ?? linkedToken.value)

  const startOfToday = DateTime.now().startOf("day").toISO()!

  async function readSignUps() {
    try {
      if (isLoggedIn.value && login.value?.userId != null) {
        signUps.value = await listOwnSignUps(login.value.userId, startOfToday)
      } else if (guestToken.value) {
        const found = await listSignUpsByAccessToken(guestToken.value)
        signUps.value = found
        const firstGuest = found[0]?.guest
        if (firstGuest != null) {
          store.commit("saveGuestData", {...firstGuest, accessToken: guestToken.value} satisfies GuestSessionData)
        }
      } else {
        signUps.value = []
      }
    } catch (error) {
      $handleNetworkError(error)
    }
  }

  async function readCommittees() {
    try {
      if (!isLoggedIn.value) {
        committees.value = []
        return
      }
      const read = isBoard.value ? await listCommittees() : await listMyCommittees()
      committees.value = (read as unknown[])
        .map((committee) => {
          const value = committee as Record<string, unknown>
          const id = typeof value.id === "number" ? value.id : null
          const name = typeof value.name === "string" ? value.name : null
          return id == null || name == null ? null : {id, name}
        })
        .filter((committee): committee is CommitteeOption => committee != null)
    } catch (error) {
      $handleNetworkError(error)
    }
  }

  watch([isLoggedIn, login, guestToken], () => {
    void readSignUps()
    void readCommittees()
  }, {immediate: true})

  return {signUps, committees}
}
