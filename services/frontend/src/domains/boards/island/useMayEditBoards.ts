import {computed, type ComputedRef} from "vue"
import {useStore} from "vuex"

/**
 * Whether the viewer may change the association's history.
 *
 * The api enforces `hasPermission(..., 'Board', 'write')`, and the login response carries inherited
 * roles, so an admin arrives holding BOARD and this asks only the one question — the same question
 * the esports island asks, per domain, so a page never reaches into another to find out who is
 * reading (frontend ADR-001). Not a guard: a refused request is still refused. It decides whether
 * to offer the affordance, a page covered in pencils that answer 403 being worse than a page
 * without them.
 */
export function useMayEditBoards(): ComputedRef<boolean> {
  const store = useStore()
  return computed<boolean>(() => store.getters.isBoard === true)
}
