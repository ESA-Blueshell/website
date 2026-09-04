import {computed, onMounted, ref, type ComputedRef, type Ref} from "vue"
import {loadAssociationNumbers, type AssociationNumbers} from "../adapters/association"
import {associationFigures, type Figure} from "../numbers"

/**
 * The association's numbers, as figures a band can draw at once.
 *
 * There is never a moment with nothing to show: the figures start on the published floors and
 * the counted ones replace them where they land, so the band keeps its height and a reader who
 * arrives mid-read is not looking at a pulsing box on the page that is meant to sell them
 * something. A read that fails leaves the floors standing, which is why nothing here reports an
 * error: the page has already said something true.
 */
export function useAssociationNumbers(): {
  figures: ComputedRef<Figure[]>
  /** True once counted numbers are in hand, for a test that wants to wait for them. */
  counted: Ref<boolean>
} {
  const numbers = ref<AssociationNumbers | null>(null)
  const counted = ref(false)

  onMounted(async () => {
    const answer = await loadAssociationNumbers()
    if (!answer) return
    numbers.value = answer
    counted.value = true
  })

  return {figures: computed(() => associationFigures(numbers.value)), counted}
}
