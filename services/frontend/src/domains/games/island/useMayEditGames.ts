import {computed, type ComputedRef} from "vue"
import {useStore} from "vuex"

/**
 * Whether the viewer may add, edit, archive or remove games: the board and anything above it,
 * the rule the api enforces. Not a guard; it decides whether to offer the buttons at all.
 */
export function useMayEditGames(): ComputedRef<boolean> {
  const store = useStore()
  return computed<boolean>(() => store.getters.isBoard === true)
}
