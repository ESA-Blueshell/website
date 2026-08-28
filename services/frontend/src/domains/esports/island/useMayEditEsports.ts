import {computed, type ComputedRef} from "vue"
import {useStore} from "vuex"

/**
 * Whether the viewer may change esports.
 *
 * The one rule the api enforces is `hasPermission('Team', 'write')`, which is BOARD and
 * anything above it, and the login response carries inherited roles — so an admin arrives
 * holding BOARD and this needs to ask only the one question. Nothing here is a guard: a
 * refused request is still refused. It decides whether to offer the affordance at all,
 * because a page full of edit icons that answer 403 is worse than a page without them.
 */
export function useMayEditEsports(): ComputedRef<boolean> {
  const store = useStore()
  return computed<boolean>(() => store.getters.isBoard === true)
}
