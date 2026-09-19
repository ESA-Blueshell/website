import {onScopeDispose, readonly, ref, type Ref} from "vue"

/** The width the bar can no longer hold its own entries at, and hands them to the drawer. */
const NARROW = "(max-width: 1279px)"

/**
 * Whether the window is too narrow for the bar to carry its entries.
 *
 * Asked in script rather than answered in CSS, because a `display: none` nav is still in the
 * document: every label in the bar would have a second copy nobody can see, and anything that
 * looks a page up by its text — a screen reader's rotor, a find-in-page, a test — reads the copy
 * first. The drawer is drawn on the same terms, and for the same reason.
 */
export function useNarrow(): Readonly<Ref<boolean>> {
  const narrow = ref(matches())

  if (typeof window !== "undefined" && typeof window.matchMedia === "function") {
    const media = window.matchMedia(NARROW)
    const onChange = (event: MediaQueryListEvent) => {
      narrow.value = event.matches
    }
    media.addEventListener("change", onChange)
    onScopeDispose(() => media.removeEventListener("change", onChange))
  }

  return readonly(narrow)
}

function matches(): boolean {
  if (typeof window === "undefined" || typeof window.matchMedia !== "function") return false
  return window.matchMedia(NARROW).matches
}
