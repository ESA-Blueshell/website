import {computed, type ComputedRef} from "vue"
import {useStore} from "vuex"

/**
 * Whether the viewer may change the association's history.
 *
 * The rule the api enforces is `hasPermission(..., 'Board', 'write')`, which is BOARD and
 * anything above it, and the login response carries inherited roles — so an admin arrives
 * holding BOARD and this needs to ask only the one question. The same question the esports
 * island asks, deliberately: one rule, asked per domain, so a page never reaches into
 * another domain to find out who is reading (frontend ADR-001).
 *
 * Nothing here is a guard: a refused request is still refused. It decides whether to offer
 * the affordance at all, because a page covered in pencils that answer 403 is worse than a
 * page without them — and a visitor is shown the history rather than the machinery.
 */
export function useMayEditBoards(): ComputedRef<boolean> {
  const store = useStore()
  return computed<boolean>(() => store.getters.isBoard === true)
}
