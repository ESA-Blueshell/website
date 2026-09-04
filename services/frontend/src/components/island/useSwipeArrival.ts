import {computed, ref, watch, type ComputedRef, type Ref} from "vue"
import type {StripArrival} from "./stripAxis"

/**
 * The bookkeeping a page owes a committed gesture.
 *
 * A page has to remember one thing: that the stop now arriving was asked for by a gesture rather
 * than by a node, a shared link or the back button. Three things hang off that memory — whether the
 * line travels or is simply there, whether the url is pushed or replaced, and whether a band
 * holding a stop it will never get has been told so. Island machinery rather than domain knowledge,
 * so it sits beside the band and the strip and nothing here knows what a stop is (frontend
 * ADR-001). A stop is a number, where the band takes either: a board is identified by its number
 * and a season by its id.
 */
export interface SwipeArrival {
  /** How the stop being read arrived, which is what the strip is told. */
  arrival: ComputedRef<StripArrival>
  /** The stop a gesture is still waiting on, or nothing once the page has refused it. */
  pending: ComputedRef<number | null>
  /**
   * The stop a gesture asked for that this page could not show, which is what the band is told.
   *
   * Only a page that fetches its stop can ever name one. See the `asked` ref in the band swipe
   * for what a band does with it, and what goes wrong for a band that is never told.
   */
  refused: Ref<number | null>
  /**
   * The stop a gesture asked for, until the page moves on from it.
   *
   * Read by a page whose url has to answer a finger differently from a hit on a node: a swipe is
   * a navigation like any other and the back button has to return the way the finger came, which
   * a replaced entry cannot do.
   */
  asked: ComputedRef<number | null>
  /** A committed gesture answered: the stop noted, and then asked of the page. */
  travelTo: (stop: string | number) => Promise<void>
}

export interface Swiping {
  /** The stop the url names, however it got there. */
  inRoute: () => number | null
  /**
   * The stop the strip is drawn on, which is the one *asked* for rather than the one arrived at.
   *
   * The line follows the stop the visitor chose and travels the moment it changes. Waiting for
   * the answer would have it jump before the band moved and then not move at all.
   */
  following: () => number | null
  /**
   * What a committed gesture asks of the page: go to this stop, and say whether you got there.
   *
   * Whether it arrived rather than whether the read succeeded, because those are not the same
   * question — the sdk hands a refusal back as a body rather than throwing, so a read that failed
   * and a stop that answered about something else look alike from here, and what a gesture is
   * waiting on is an arrival either way. A page holding every stop already answers true.
   */
  reach: (stop: number) => boolean | Promise<boolean>
}

export function useSwipeArrival({inRoute, following, reach}: Swiping): SwipeArrival {
  const swipedTo = ref<number | null>(null)
  const refused = ref<number | null>(null)

  const travelTo = async (stop: string | number) => {
    const id = Number(stop)
    refused.value = null
    swipedTo.value = id
    if (await reach(id)) return
    // Only where this is still the gesture being waited on. A gesture superseded by a quicker one
    // has had its answer dropped by the page's own sequence guard, and naming its stop here would
    // leave the mark set on a stop nobody is going to.
    if (swipedTo.value === id) refused.value = id
  }

  const arrival = computed<StripArrival>(() =>
    (swipedTo.value != null && swipedTo.value === following() ? "gesture" : "elsewhere"))

  // Any navigation that is not the one the gesture asked for spends the mark, so a stop reached by
  // a finger once is not travelled to for ever after when a link or the back button names it again.
  // The stop rather than a flag for the same reason the strip's own claim on a click is an id: a
  // page that declined to follow one gesture must not be left swallowing whatever arrives next.
  watch(inRoute, (stop) => {
    if (stop !== swipedTo.value) swipedTo.value = null
  })

  /**
   * The stop a committed gesture is still waiting on, which is the stop held in front of the
   * reader — and nothing once the page has said it is not coming.
   *
   * Not the same question as `asked`. `asked` is the last stop a gesture named and it outlives
   * the journey: the route watcher above only spends it when some *other* stop arrives, so after
   * an arrival it still names the stop now drawn, which is harmless because the two agree. After
   * a **refusal** they do not: the track has sprung home, what the reader sees is the stop still
   * drawn, and a further gesture measured from the refused one steps past the stop in front of
   * them — which silently stopped a refused stop from ever being asked for again.
   */
  const pending = computed(() => (refused.value == null ? swipedTo.value : null))

  return {arrival, refused, pending, asked: computed(() => swipedTo.value), travelTo}
}
