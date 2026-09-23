import {nextTick, onBeforeUnmount, ref, watch, type Ref} from "vue"

/** Where an open panel hangs: under its field, or over it where there is no room below. */
export interface PanelBox {
  top: number
  left: number
  width: number
  above: boolean
}

/**
 * A panel that hangs flush from a field while it is open: as wide as the field, directly under
 * it, or over it where the window has no room below. It is drawn at the end of the document,
 * since an ancestor that scrolls or is cut would clip it, so it follows the field on scroll and
 * resize. A press outside the field closes it, and so does Escape; the panel stops its own
 * presses, so a press reaching the document came from outside both.
 */
export function useAnchoredPanel(
  anchor: Readonly<Ref<HTMLElement | null>>,
  open: Ref<boolean>,
  needs: number,
): Ref<PanelBox> {
  const box = ref<PanelBox>({top: 0, left: 0, width: 0, above: false})

  const place = () => {
    const at = (anchor.value as HTMLElement).getBoundingClientRect()
    const room = window.innerHeight - at.bottom
    const above = room < needs && at.top > room
    box.value = {top: above ? at.top : at.bottom, left: at.left, width: at.width, above}
  }

  const elsewhere = (event: Event) => {
    if (anchor.value?.contains(event.target as Element | null)) return
    open.value = false
  }

  const onEscape = (event: KeyboardEvent) => {
    if (event.key === "Escape") open.value = false
  }

  // Called through `document`: a method taken off it and called bare is refused.
  const watching = (on: boolean) => {
    if (on) {
      window.addEventListener("scroll", place, true)
      window.addEventListener("resize", place)
      document.addEventListener("pointerdown", elsewhere)
      document.addEventListener("keydown", onEscape)
      return
    }
    window.removeEventListener("scroll", place, true)
    window.removeEventListener("resize", place)
    document.removeEventListener("pointerdown", elsewhere)
    document.removeEventListener("keydown", onEscape)
  }

  watch(open, async (down) => {
    if (!down) {
      watching(false)
      return
    }
    place()
    await nextTick()
    place()
    watching(true)
  })

  onBeforeUnmount(() => watching(false))

  return box
}
