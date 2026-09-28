import {computed, type ComputedRef} from "vue"
import {useStore} from "vuex"

/**
 * Whether the reader is on the board, which is every rule the api has for editing boards, esports,
 * games and every committee. The login response carries inherited roles, so an admin arrives
 * holding BOARD. Not a guard: a refused request is still refused, and this only decides which
 * affordances to offer.
 */
export function useIsBoard(): ComputedRef<boolean> {
  const store = useStore()
  return computed<boolean>(() => store.getters.isBoard === true)
}
