import {computed, type ComputedRef, ref, watch} from "vue"
import {useStore} from "vuex"
import {listMyCommittees} from "../adapters/committees"

/**
 * What the viewer may do with committees: the board adds, archives and edits all of them, and a
 * committee's own members edit its page and add its events. Not a guard; the api enforces the
 * same rule, and this only decides which buttons to offer.
 */
export function useCommitteeRights(): {
  isBoard: ComputedRef<boolean>
  sitsOn: (committeeId: number) => boolean
} {
  const store = useStore()
  const isBoard = computed<boolean>(() => store.getters.isBoard === true)
  const isLoggedIn = computed<boolean>(() => store.getters.isLoggedIn === true)
  const mine = ref<Set<number>>(new Set())

  watch(isLoggedIn, async loggedIn => {
    if (!loggedIn) {
      mine.value = new Set()
      return
    }
    try {
      // The board is answered with every committee here, which is what it may edit anyway.
      mine.value = new Set((await listMyCommittees()).map(committee => committee.id))
    } catch {
      mine.value = new Set()
    }
  }, {immediate: true})

  return {isBoard, sitsOn: committeeId => mine.value.has(committeeId)}
}
