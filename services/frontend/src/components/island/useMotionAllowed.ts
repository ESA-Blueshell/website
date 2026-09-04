import {computed, onScopeDispose, ref, type ComputedRef, type Ref} from "vue"

const QUERY = "(prefers-reduced-motion: reduce)"

/** How much a transition may take once the visitor's preference is applied. */
const REDUCED_CEILING_S = 0.12

export interface MotionPolicy {
  /** True when the visitor has asked for reduced motion. */
  reduced: Ref<boolean>
  /** Decorative movement — parallax, drift, tilt, counters. False when reduced. */
  decorative: ComputedRef<boolean>
  /** Movement that explains a change. Always allowed, shortened when reduced. */
  explanatory: ComputedRef<boolean>
  /** Clamps a duration in seconds to what the preference allows. */
  duration: (seconds: number) => number
}

/**
 * Whether this visitor gets motion, and which kind.
 *
 * The island reduces rather than removes: movement that only decorates — parallax, drift, tilt,
 * a number counting up — triggers the discomfort the preference asks about and switches off,
 * while movement that explains something stays, since removing it leaves the visitor to work out
 * what changed. Both answers come from one query, so a page can never end up half-animated.
 */
export function useMotionAllowed(): MotionPolicy {
  const reduced = ref(prefersReduced())

  if (typeof window !== "undefined" && typeof window.matchMedia === "function") {
    const media = window.matchMedia(QUERY)
    const onChange = (event: MediaQueryListEvent) => {
      reduced.value = event.matches
    }
    media.addEventListener("change", onChange)
    onScopeDispose(() => media.removeEventListener("change", onChange))
  }

  return {
    reduced,
    decorative: computed(() => !reduced.value),
    explanatory: computed(() => true),
    duration: (seconds: number) => (reduced.value ? Math.min(seconds, REDUCED_CEILING_S) : seconds),
  }
}

function prefersReduced(): boolean {
  if (typeof window === "undefined" || typeof window.matchMedia !== "function") return false
  return window.matchMedia(QUERY).matches
}
