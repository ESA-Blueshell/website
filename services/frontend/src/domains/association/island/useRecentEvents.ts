import {onMounted, ref, type Ref} from "vue"
import {loadRecentEventsWithArt} from "../adapters/association"
import {eventSlices, SAMPLED, type EventSlice} from "../events"

/**
 * The recent events with art, as slices a band can draw at once.
 *
 * Empty until the read lands, and empty again where it fails or where too few events qualify:
 * the band is drawn only on slices, so there is never a moment with a short band or a row of
 * grey boxes on a page that is meant to sell something. Nothing here reports an error, because
 * a page with no events on it is a page that simply makes its case without them.
 */
export function useRecentEvents(): {slices: Ref<EventSlice[]>} {
  const slices = ref<EventSlice[]>([])

  onMounted(async () => {
    slices.value = eventSlices(await loadRecentEventsWithArt(SAMPLED))
  })

  return {slices}
}
