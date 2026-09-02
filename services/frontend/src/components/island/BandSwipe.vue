<script lang="ts" setup>
import {computed, nextTick, onBeforeUnmount, ref, watch} from "vue"
import {AnimatePresence, Motion} from "motion-v"
import {provideTravelling} from "./bandTravel"
import {useMotionAllowed} from "./useMotionAllowed"

defineOptions({name: "BandSwipe"})

/** Which way one stop lies from another along a strip: back down it, or on up it. */
export type BandDirection = "past" | "future" | "same"

const props = withDefaults(defineProps<{
  /**
   * The stop whose contents are shown.
   *
   * The stop that has *arrived*, not the one that was clicked. The strip answers a click at
   * once; the band waits, and then moves once. A band half way across the screen while a
   * request is still in flight is a worse answer than a band that has not set off yet.
   */
  stop: string | number | null
  /**
   * Which way this change travels, which the domain works out.
   *
   * Which of two stops is later is knowledge about what the stops are, which the island does
   * not have: it draws the pass, the way the strip takes stops it cannot order for itself.
   */
  direction?: BandDirection
  testid?: string
}>(), {direction: "same", testid: "band-swipe"})

const motion = useMotionAllowed()

/**
 * How long one pass takes, and on what curve.
 *
 * The curve is the island's own, the same one the entrance and a slice opening use. The
 * duration is longer than either of them on purpose: this is not a control answering a click,
 * which wants to be quick, but a page's worth of content travelling the width of the window,
 * and at the speed the rest of the island moves it was over before it read as movement at all.
 */
const TRAVEL_S = 0.85
const EASE = [0.22, 1, 0.36, 1] as const
const EASE_CSS = "cubic-bezier(0.22, 1, 0.36, 1)"

/**
 * How this visitor travels between stops.
 *
 * A band the width of the window crossing it is exactly the large-area movement the reduced
 * motion preference is about, and making it brief does not make it smaller, so under that
 * preference the two cross over rather than travel. The change is still explained, which is
 * why it is not simply switched off.
 */
const mode = computed<"slide" | "fade">(() => (motion.reduced.value ? "fade" : "slide"))

/**
 * Whether a pass is on, for whatever is being carried to read.
 *
 * A band that opens a slice mid-pass animates a row's layout inside a subtree that is being
 * translated, twice over, since both stops are on the page. The bands wait for this instead.
 */
const travelling = provideTravelling()

/** The pass, seen off after the time it takes, since nothing else reports its end. */
let settling: ReturnType<typeof setTimeout> | null = null

const travel = (going: boolean) => {
  if (settling) clearTimeout(settling)
  travelling.value = going
  if (!going) return
  settling = setTimeout(() => {
    travelling.value = false
    settling = null
  }, motion.duration(TRAVEL_S) * 1000 + 60)
}

onBeforeUnmount(() => {
  if (settling) clearTimeout(settling)
})

const shell = ref<HTMLElement | null>(null)
/**
 * The contents that arrived last.
 *
 * Recorded through a function that ignores the unmount, because for the length of a pass both
 * the stop leaving and the stop arriving are on the page and a plain ref would answer with
 * whichever of them Vue happened to touch last.
 */
const arriving = ref<HTMLElement | null>(null)
const takeArriving = (el: unknown) => {
  if (el) arriving.value = el as HTMLElement
}

/**
 * Where the contents come in from and go out to, as a share of their own width.
 *
 * Oldest sits left on the strip, so travelling back sends what is on screen off to the right
 * and brings the older stop in from the left. Forward is the mirror of it.
 */
const offset = (edge: "in" | "out"): string => {
  if (mode.value === "fade" || props.direction === "same") return "0%"
  const back = props.direction === "past"
  return (edge === "in") === back ? "-100%" : "100%"
}

/**
 * How long this pass takes.
 *
 * Nothing at all for the first stop to arrive: it has nowhere to have come from, so it does not
 * travel, and the page plays its own entrance instead as it always has.
 */
const crossing = () => ({
  duration: props.direction === "same" ? 0 : motion.duration(TRAVEL_S),
  ease: EASE,
})

/**
 * Written as variants, and every one of them a function, because of what a pass actually is:
 * two children on the page at once, one of them already gone from the template.
 *
 * Vue does not re-render the child that is leaving, since it is no longer in the parent's
 * output, so whatever was bound to it is the value it had when it *arrived*, which is a pass
 * old. Read that way the stop leaving would take the direction of the stop before it and the
 * duration it was given then, which for the first pass is no duration at all: the band would
 * jump aside while the new one slid in, and the two would only agree by luck. A function is
 * asked at the moment the animation starts, so both halves of a pass read the same answer.
 */
const variants = {
  arriving: () => ({
    x: offset("in"),
    opacity: mode.value === "fade" ? 0 : 1,
  }),
  settled: () => ({
    x: "0%",
    opacity: 1,
    transition: crossing(),
  }),
  leaving: () => ({
    x: offset("out"),
    opacity: mode.value === "fade" ? 0 : 1,
    transition: crossing(),
  }),
}

const frame = () => new Promise<void>(resolve => requestAnimationFrame(() => resolve()))

/** Under this much, a difference in height is not a difference worth a frame of layout. */
const HAIR = 8

let sizing: Animation | null = null

/**
 * The stop on its way out stops being a stop and becomes a picture of one.
 *
 * For the length of a pass the page holds two of everything, two bands and two of every button
 * on them. Left as they are, both are in the tab order and both are read out, so a visitor
 * tabbing mid-pass lands in a stop that is leaving, and anything looking a slice up by name
 * finds two and cannot say which it meant.
 *
 * `inert` takes it out of the tab order and off the pointer; `aria-hidden` takes it out of what
 * is read; and its names go with it, because a name is for addressing a thing and this is no
 * longer a thing to address. It is on screen only until it has finished leaving.
 */
const ghost = (el: HTMLElement) => {
  el.setAttribute("aria-hidden", "true")
  el.inert = true
  el.removeAttribute("data-testid")
  el.querySelectorAll<HTMLElement>("[data-testid]").forEach(one => one.removeAttribute("data-testid"))
}

/**
 * Sees the stop leaving off, and carries the height from the one to the other.
 *
 * Side by side the two are the same height and the height half of this shows nothing. Stacked
 * they are not, one stop's contents being twice another's, and the contents leaving are taken
 * out of the flow the moment they start to travel, so without it the page below would jump to
 * the new height at the start of a pass and sit there while it played out.
 */
const carry = async (travelling: boolean) => {
  const el = shell.value
  if (!el) return
  sizing?.cancel()
  const from = el.offsetHeight
  // Only while a stop is actually travelling. The stop is answered afresh every time the page
  // re-asks about it, an edit saved, and holding the height through those would animate the
  // band growing to fit an editor that had just opened, which is a change the visitor made and
  // can already see.
  if (travelling) el.style.height = `${from}px`

  await nextTick()
  // Straight after the swap, before the browser has had a chance to paint either of them, so
  // there is no moment in which the page shows two of the same slice under the same name.
  // Only ever with two on the page: with one, the one is the live one.
  const live = arriving.value
  if (el.children.length > 1) {
    Array.from(el.children).forEach(child => {
      if (live && !child.contains(live)) ghost(child as HTMLElement)
    })
  }
  if (!travelling) return

  await frame()
  const to = live?.offsetHeight ?? 0
  // Animating a height is a layout of everything inside it on every frame, and inside it are
  // two whole bands: worth it to carry a real difference, never worth it to carry a rounding
  // error. Side by side the two are usually the same height and this is where that is spent.
  if (to === 0 || Math.abs(to - from) < HAIR) {
    el.style.height = ""
    return
  }

  // Set the resting height before animating over it, so the end of the pass is where the
  // element already stands and releasing the hold shows nothing.
  el.style.height = `${to}px`
  sizing = el.animate(
    [{height: `${from}px`}, {height: `${to}px`}],
    {duration: motion.duration(TRAVEL_S) * 1000, easing: EASE_CSS},
  )
  const release = () => {
    if (el.style.height === `${to}px`) el.style.height = ""
  }
  sizing.finished.then(release, release)
}

/**
 * The swap, watched rather than rendered into: the direction arrives with it, and the height
 * has to be taken before the contents leaving are out of the flow.
 */
watch(() => props.stop, () => {
  const going = props.direction !== "same"
  travel(going)
  // A stop that is not going anywhere still has to see off whatever was on the page before it,
  // the first one to arrive replacing the loading block, but it holds no height while it does,
  // because nothing is travelling.
  void carry(going)
})
</script>

<template>
  <div
    ref="shell"
    class="band-swipe"
    :data-swipe="direction"
    :data-swipe-mode="mode"
    :data-testid="testid"
  >
    <!--
      Both stops are on the page for the length of a pass, which is what makes it a pass and
      not a repaint. The one leaving is taken out of the flow so the one arriving has the room
      it needs; the height above carries the difference.
    -->
    <animate-presence
      :initial="false"
      mode="popLayout"
    >
      <Motion
        :key="stop ?? 'none'"
        animate="settled"
        exit="leaving"
        initial="arriving"
        :variants="variants"
      >
        <div :ref="takeArriving">
          <slot />
        </div>
      </Motion>
    </animate-presence>
  </div>
</template>

<style scoped>
.band-swipe {
  position: relative;
  width: 100%;
  /*
   * Clipped rather than hidden: a stop on its way past the edge must not leave the page
   * draggable sideways, and `clip` does that without turning this into a scroll port, which
   * would take the band's own reckoning of which slice is in the middle of the screen with it.
   */
  overflow: clip;
}
</style>
