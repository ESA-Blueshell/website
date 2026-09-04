import {inject, provide, ref, type InjectionKey, type Ref} from "vue"

/**
 * Whether a pass is on: the band swipe says so, and anything inside it can wait for it.
 *
 * A band that opens a slice mid-pass animates a row's layout inside a subtree being translated
 * across the screen, twice over, since both stops are on the page for the length of a pass, and
 * a reader sees a slide that stutters. So the pass is stated where the bands can read it, and a
 * band settles once the page has stopped moving. Injected defaulting to "not travelling", so a
 * band outside a swipe behaves as it would alone.
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
