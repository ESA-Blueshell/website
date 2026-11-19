import {computed, type ComputedRef} from "vue"
import {useStore} from "vuex"
import {useIsBoard} from "@/composables/useIsBoard"

/**
 * What the reader may do with a vote: a signed-in member casts a ballot, and the board starts one,
 * moves its dates and reads its standings. Not a guard; the api enforces the same rule, and this
 * only decides what the page offers.
 */
export function useVoteRights(): {
  signedIn: ComputedRef<boolean>
  isMember: ComputedRef<boolean>
  isBoard: ComputedRef<boolean>
} {
  const store = useStore()
  return {
    signedIn: computed<boolean>(() => store.getters.isLoggedIn === true),
    isMember: computed<boolean>(() => store.getters.isMember === true),
    isBoard: useIsBoard(),
  }
}
