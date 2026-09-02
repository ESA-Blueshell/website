import {inject, provide, ref, type InjectionKey, type Ref} from "vue"

/**
 * Whether a pass is on: the band swipe says so, and anything inside it can wait for it.
 *
 * A band that opens a slice while the page is travelling animates a row's layout inside a
 * subtree that is being translated across the screen, and does it twice over, since both the
 * board leaving and the board arriving are on the page for the length of a pass. What a reader
 * sees is a slide that stutters. So the pass is stated where the bands can read it, and a band
 * settles on the slice it opens once the page has stopped moving.
 *
 * Injected with a default of "not travelling", so a band outside a swipe (a game's own page,
 * a test mounting one on its own) behaves exactly as it did.
 */
const TRAVELLING: InjectionKey<Ref<boolean>> = Symbol("island:travelling")

/** Said by the swipe, once, for whatever it is carrying. */
export function provideTravelling(): Ref<boolean> {
  const travelling = ref(false)
  provide(TRAVELLING, travelling)
  return travelling
}

/** Read by a band that has something to hold until the page has settled. */
export function useTravelling(): Ref<boolean> {
  return inject(TRAVELLING, ref(false))
}
