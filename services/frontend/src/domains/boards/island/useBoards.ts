import {computed, onMounted, ref, type ComputedRef, type Ref} from "vue"
import {$handleNetworkError} from "@/plugins/handleNetworkError"
import {loadBoards, type Board} from "../adapters/boards"
import {boardInOffice} from "../standing"

/**
 * The boards the association has had, read once for the page that shows them.
 *
 * One read answers everything the page asks: the strip is every board, the board being shown is
 * one of them, and which is in office follows from their dates rather than from a second
 * question. Held per page rather than in the module, because there is one page and asking again
 * is what showing a correction is.
 */
export function useBoards(): {
  /** Every board, newest first, which is the order the adapter answers in. */
  boards: Ref<Board[]>
  loading: Ref<boolean>
  /** The board running the association, or the newest one that has, where none is sitting. */
  inOffice: ComputedRef<Board | null>
  refresh: () => Promise<void>
} {
  const boards = ref<Board[]>([])
  const loading = ref(true)

  const refresh = async () => {
    loading.value = true
    try {
      boards.value = await loadBoards()
    } catch (error) {
      $handleNetworkError(error)
    } finally {
      loading.value = false
    }
  }

  onMounted(refresh)

  return {
    boards,
    loading,
    inOffice: computed(() => boardInOffice(boards.value)),
    refresh,
  }
}
