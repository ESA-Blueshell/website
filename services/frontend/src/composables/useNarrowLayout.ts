import {computed, type ComputedRef} from "vue"
import {useDisplay} from "vuetify"

/**
 * Whether the viewport is too narrow to read a table, at the width the member manager turns
 * its own table into a list. A page and the modals it launches must agree on this, or a table
 * opens on top of a list.
 */
export function useNarrowLayout(): {narrow: ComputedRef<boolean>} {
  const {lgAndUp} = useDisplay()
  return {narrow: computed(() => !lgAndUp.value)}
}
