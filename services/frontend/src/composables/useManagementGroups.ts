import {computed, type ComputedRef} from "vue"
import {useStore} from "vuex"
import {type ManagementGroup, managementFor} from "@/components/management/managementNav"

/** The Management groups and pages the reader may open. */
export function useManagementGroups(): ComputedRef<ManagementGroup[]> {
  const store = useStore()
  return computed(() => managementFor({board: store.getters.isBoard === true, admin: store.getters.isAdmin === true}))
}
