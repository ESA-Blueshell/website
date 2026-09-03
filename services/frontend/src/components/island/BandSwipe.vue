<script lang="ts" setup>
import {computed, nextTick, onBeforeUnmount, onScopeDispose, ref, watch} from "vue"
import {AnimatePresence, Motion} from "motion-v"
import {provideTravelling} from "./bandTravel"
import {commits, directionOf, DRAG, follow, paceOf} from "./dragAxis"
import {useMotionAllowed} from "./useMotionAllowed"

defineOptions({name: "BandSwipe"})

/** Which way one stop lies from another along a strip: back down it, or on up it. */
export type BandDirection = "past" | "future" | "same"

const props = withDefaults(defineProps<{
  /**
   * The stop whose contents are shown.
   *
   * The stop that has *arrived*, not the one that was clicked. A click on the strip is answered
   * at once; the band waits, and then moves once. A band half way across the screen while a
   * request the visitor cannot see is still in flight is a worse answer than a band that has not
   * set off yet: nobody asked for that movement, so there is nothing for it to be reporting, and
   * a band that sets off and then stalls has reported something untrue.
   *
   * Under a finger the same argument says the opposite, which is why the gesture below does not
   * wait. A band half way across the screen during a drag is where the visitor put it, moment by
   * moment; it is the gesture's own answer to how far there is left to go, and it can no more be
   * untrue than a scrollbar can. So the two rules stand together rather than one replacing the
   * other: what the visitor did not initiate waits for its contents, and what their finger is
   * doing follows their finger. The seam between them is the handover at the commit below, where
   * the gesture finishes its own pass before this prop is ever asked to change.
   *
   * It is also what the slot is handed back, so what is carried is drawn as a function of a
   * stop rather than of whatever the page happens to be holding. See the slot below.
   */
  stop: string | number | null
  /**
   * Which way this change travels, which the domain works out.
   *
   * Which of two stops is later is knowledge about what the stops are, which the island does
   * not have: it draws the pass, the way the strip takes stops it cannot order for itself.
   */
  direction?: BandDirection
  /**
   * The stops either side of the one showing, back down the line and on up it, which the domain
   * works out for the same reason the direction is.
   *
   * They are what makes a drag possible at all: the one the finger is heading for is drawn beside
   * the one showing for the length of the gesture, and where there is none that way the band
   * leans and springs home instead. A band handed neither is a band that does not drag, which is
   * how a page that has no neighbours to offer opts out by saying nothing.
   */
  past?: string | number | null
  future?: string | number | null
  testid?: string
}>(), {direction: "same", past: null, future: null, testid: "band-swipe"})

/**
 * A committed gesture asks the page to travel; it does not travel by itself.
 *
 * The page answers a finger the way it answers a click on a node: it changes the stop, it sets
 * the url, and the arrived stop comes back down. So a swipe is a navigation like any other, with
 * a history entry and a shareable address, and the island has not learned how either is made.
 */
const emit = defineEmits<{(event: "travel", stop: string | number): void}>()

const motion = useMotionAllowed()

/**
 * How long one pass takes, and on what curve.
 *
 * The curve is the island's own, the same one the entrance and a slice opening use. The
 * duration is longer than either of them on purpose: this is not a control answering a click,
 * which wants to be quick, but a page's worth of content travelling the width of the window,
 * and at the speed the rest of the island moves it was over before it read as movement at all.
 *
 * It times the gesture's own two movements too — the ease onto the neighbour once a drag has
 * committed, and the spring home when it has not — because they are the same journey made by
 * another route, and a commit that ran to a different clock would read as a different mechanism.
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
 *
 * The gesture is not governed by this. Content under a finger is not unbidden movement, and a
 * band that refused to follow the finger would leave a visitor who asked for reduced motion with
 * no gesture at all on the one device where the gesture is the point. What is clamped there is
 * everything they did not do themselves: the ease onto the neighbour and the spring home, both
 * of which take their duration from the policy above.
 */
const mode = computed<"slide" | "fade">(() => (motion.reduced.value ? "fade" : "slide"))

/**
 * Whether a pass is on, for whatever is being carried to read.
 *
 * A band that opens a slice mid-pass animates a row's layout inside a subtree that is being
 * translated, twice over, since both stops are on the page. The bands wait for this instead.
 *
 * A gesture says it too, from the moment a finger claims the horizontal axis until the track it
 * dragged is dropped, so no slice opens or closes under a finger that is travelling.
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
 * Whether the pass for the change now arriving has already been played, by a finger.
 *
 * A committed gesture carries the neighbour all the way across the screen before the page is
 * asked for it, so by the time the arrived stop comes back down the movement has happened. Both
 * panels are drawing the same contents at that moment, so the cross-slide is suppressed for that
 * one change: dropping the track and standing the band square is then invisible, where playing
 * the pass again would send the same contents across the screen a second time.
 *
 * Read through the variants below rather than mixed into them, which is the whole of why no
 * offset arithmetic is shared between the two mechanisms.
 */
const played = ref(false)

/**
 * Where the contents come in from and go out to, as a share of their own width.
 *
 * Oldest sits left on the strip, so travelling back sends what is on screen off to the right
 * and brings the older stop in from the left. Forward is the mirror of it.
 */
const offset = (edge: "in" | "out"): string => {
  if (mode.value === "fade" || props.direction === "same" || played.value) return "0%"
  const back = props.direction === "past"
  return (edge === "in") === back ? "-100%" : "100%"
}

/**
 * How long this pass takes.
 *
 * Nothing at all for the first stop to arrive: it has nowhere to have come from, so it does not
 * travel, and the page plays its own entrance instead as it always has. Nothing either for a
 * change a finger has already carried across the screen.
 */
const crossing = () => ({
  duration: props.direction === "same" || played.value ? 0 : motion.duration(TRAVEL_S),
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
 *
 * It is also why the gesture's own offset is nowhere near here. A live drag position threaded
 * into these would be read a pass old by exactly the child that is leaving, which is the bug
 * this shape exists to prevent.
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
 *
 * The neighbour a gesture draws beside the stop showing is the same kind of thing for the same
 * reasons — a picture of a stop, not a stop — so it is ghosted by this too.
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

/*
 * ── The gesture ──────────────────────────────────────────────────────────────────────────────
 *
 * A drag is a second mechanism rather than the same one driven differently, and everything below
 * belongs to it alone: its own two-panel track, rendered only for the length of a gesture, its
 * own offset in pixels, and its own two movements. The pass above is not touched by any of it.
 * The only place they meet is `played` and the watch at the bottom, which is the handover.
 */

const COARSE = "(pointer: coarse)"

const asks = (query: string): boolean => {
  if (typeof window === "undefined" || typeof window.matchMedia !== "function") return false
  return window.matchMedia(query).matches
}

/**
 * Whether the pointer reading this page is a finger.
 *
 * The band is a row of slices that open under a pointer on a wide screen, so a mouse dragged
 * across it would open every slice it crossed while the band translated underneath. A finger has
 * no hover to lose and nothing else to do with a sideways drag. The strip beside this one already
 * branches on the pointer the same way, for the mirror of the same reason.
 */
const coarse = ref(asks(COARSE))
if (typeof window !== "undefined" && typeof window.matchMedia === "function") {
  const media = window.matchMedia(COARSE)
  const onChange = (event: MediaQueryListEvent) => {
    coarse.value = event.matches
  }
  media.addEventListener("change", onChange)
  onScopeDispose(() => media.removeEventListener("change", onChange))
}

/**
 * Whether this band drags at all: a finger, and somewhere for it to go.
 *
 * A page that names neither neighbour has said it has none to offer, and the gesture stays out
 * of its way entirely — no track, no claim on the axis, no press swallowed.
 */
const armed = computed(() => coarse.value && (props.past != null || props.future != null))

/** The neighbouring stop drawn beside the one showing, for the length of a gesture. */
const beside = ref<string | number | null>(null)
/** Which side of the band it sits on, as a share of its own width. */
const asideAt = ref("100%")
/** How far the band stands from home, in pixels: the gesture's whole offset, and its only one. */
const reach = ref(0)
/** Whether the track is on the page and translated, from the first sideways move until it drops. */
const holding = ref(false)
/** Whether one of the gesture's own two movements is playing, which is not a moment to grab. */
const easing = ref(false)
/**
 * The stop a committed gesture has asked the page for, until it arrives.
 *
 * What the handover turns on: the change that comes back carrying this stop is the one the finger
 * has already played, and the track holds the neighbour on screen until then. Where the contents
 * are slow that hold is the whole answer — the visitor is looking at the stop they asked for,
 * which is honest, and nothing snaps.
 */
const asked = ref<string | number | null>(null)

const aside = ref<HTMLElement | null>(null)

/** A place on the axis, as a transform: so far from home, and so far beside it. */
const at = (x: number, from = "0px"): string => `translate3d(calc(${x}px + ${from}), 0, 0)`

/** Where the band and its neighbour stand, the neighbour a width to one side of the band. */
const standing = computed(() => at(reach.value))
const asideStanding = computed(() => at(reach.value, asideAt.value))

const neighbour = (way: BandDirection): string | number | null => {
  if (way === "past") return props.past ?? null
  if (way === "future") return props.future ?? null
  return null
}

/** How far the band may lean at the end of the line, in pixels, at this visitor's text size. */
const leanCap = (): number => {
  if (typeof window === "undefined") return DRAG.leanCap * 16
  return DRAG.leanCap * (parseFloat(getComputedStyle(document.documentElement).fontSize) || 16)
}

/**
 * One of the gesture's own two movements: the band eased from where the finger left it to [to].
 *
 * Both panels are animated rather than a wrapper around them, because the pass above owns the
 * element that would have been that wrapper and animates it for its own reasons. Two animations
 * given one duration and one curve stay together; a shared parent would have had them arguing.
 *
 * The resting place is set before the animation is started, so when it ends the elements are
 * already standing where it left them and releasing it shows nothing — the same idiom the height
 * above uses.
 */
const glide = async (to: number) => {
  const ms = motion.duration(TRAVEL_S) * 1000
  const runs = [
    arriving.value?.animate(
      [{transform: at(reach.value)}, {transform: at(to)}],
      {duration: ms, easing: EASE_CSS},
    ),
    aside.value?.animate(
      [{transform: at(reach.value, asideAt.value)}, {transform: at(to, asideAt.value)}],
      {duration: ms, easing: EASE_CSS},
    ),
  ]
  reach.value = to
  await Promise.all(runs.map(run => run?.finished.catch(() => undefined)))
}

/**
 * The band's height carried onto the neighbour's while the commit plays out.
 *
 * The neighbour is drawn out of the flow, so the band stands at the height of the stop showing
 * for the whole of a drag. Left alone, the page would jump to the arrived stop's height the
 * moment it landed — two stops' contents are not the same height, one board having six members
 * and the next one — and it would jump after the movement rather than during it, which is the
 * one thing the handover is for. So the height travels with the commit, and is held at the end
 * of it until the track is dropped, by which point the arrived contents stand at it themselves.
 */
const bear = (ms: number) => {
  const el = shell.value
  const to = aside.value?.offsetHeight ?? 0
  if (!el || to === 0) return
  const from = el.offsetHeight
  if (Math.abs(to - from) < HAIR) return
  sizing?.cancel()
  el.style.height = `${to}px`
  sizing = el.animate([{height: `${from}px`}, {height: `${to}px`}], {duration: ms, easing: EASE_CSS})
}

/** The track put away: the neighbour gone, the band square, and nothing travelling. */
const drop = () => {
  const el = shell.value
  if (el?.style.height) el.style.height = ""
  beside.value = null
  reach.value = 0
  holding.value = false
  easing.value = false
  travelling.value = false
}

let finger: number | null = null
let claimed = false
let began = {x: 0, y: 0}
let last = {x: 0, at: 0}
let before = {x: 0, at: 0}
/** The width the gesture is measured and clamped against, taken once when the axis is claimed. */
let across = 0
let cap = 0
/**
 * Whether the press this gesture began on still has to be swallowed.
 *
 * A drag that went anywhere was not a press on whatever it started from — a slice, its pencil, a
 * link — and the click that follows a finger lifting has no way of knowing that. It is caught on
 * the way down at this band's own root rather than by suppressing anything inside it, because
 * what the band carries is not the band's to reach into: it does not know what a slice is, and a
 * page that put something else inside it would need no new arrangement.
 */
let pressed = false

const grab = (event: PointerEvent) => {
  pressed = false
  if (!armed.value || easing.value || asked.value != null) return
  if (event.pointerType === "mouse" && event.button !== 0) return
  finger = event.pointerId
  claimed = false
  began = {x: event.clientX, y: event.clientY}
  last = {x: event.clientX, at: event.timeStamp}
  before = last
}

const drag = (event: PointerEvent) => {
  if (finger !== event.pointerId) return
  const gone = event.clientX - began.x

  if (!claimed) {
    // Under the slop it is a tap that wobbled, or the first pixels of a scroll.
    if (Math.abs(gone) < DRAG.slop) return
    // And the vertical axis is the browser's. Only the horizontal one is claimed, here and in
    // the touch-action below, so the ordinary scroll of the page and the scroll that decides
    // which slice is open are both exactly what they were.
    if (Math.abs(gone) <= Math.abs(event.clientY - began.y)) {
      finger = null
      return
    }
    claimed = true
    across = shell.value?.clientWidth || window.innerWidth
    cap = leanCap()
    holding.value = true
    travelling.value = true
    // So the gesture keeps its events when the finger wanders off the band, which on a page
    // this tall it does: an arrival is a whole width away and the band is not a whole width tall.
    shell.value?.setPointerCapture(event.pointerId)
  }

  // The neighbour is mounted on the first sideways move, so it has the rest of the drag to paint
  // in. Answered every move rather than once, because a finger that crosses back through where it
  // started is now heading for the stop on the other side.
  const way = directionOf(gone)
  if (way !== "same") {
    beside.value = neighbour(way)
    asideAt.value = way === "past" ? "-100%" : "100%"
  }
  reach.value = follow({travel: gone, width: across, onward: beside.value != null, cap})
  before = last
  last = {x: event.clientX, at: event.timeStamp}
}

/**
 * The finger lifted: the journey either finished or handed back.
 *
 * On a commit the gesture finishes its own pass first and asks the page afterwards. That order is
 * the handover: the neighbour is already drawing the real contents, so easing it to a full width
 * leaves it filling the window, and only then is there any point in the page changing stop. Asked
 * first and eased second, the two mechanisms would both be moving the same contents at once.
 */
const release = async (event: PointerEvent) => {
  if (finger !== event.pointerId) return
  finger = null
  if (!claimed) return
  claimed = false

  const gone = event.clientX - began.x
  pressed = Math.abs(gone) >= DRAG.slop
  const wanted = beside.value
  const pace = paceOf(last.x - before.x, last.at - before.at)
  easing.value = true

  if (wanted != null && commits({travel: gone, pace, width: across, onward: true})) {
    const ms = motion.duration(TRAVEL_S) * 1000
    bear(ms)
    await glide(Math.sign(gone) * across)
    // Set before the page is asked, because the page may answer immediately: this is what the
    // change coming back is recognised by.
    asked.value = wanted
    easing.value = false
    emit("travel", wanted)
    return
  }

  await glide(0)
  drop()
}

/** The browser took the gesture over — a scroll that won, a call coming in. Nothing happened. */
const forfeit = async (event: PointerEvent) => {
  if (finger !== event.pointerId) return
  finger = null
  if (!claimed) return
  claimed = false
  easing.value = true
  await glide(0)
  drop()
}

const stifle = (event: MouseEvent) => {
  if (!pressed) return
  pressed = false
  event.preventDefault()
  event.stopPropagation()
}

/**
 * And the browser's own drag, which is the same problem wearing a different hat.
 *
 * A band is photographs, and a photograph is a thing a browser will pick up and carry off if a
 * pointer moves while it is held down — at five pixels, which is before this gesture has decided
 * it is a gesture at all. The dragged image takes the pointer with it and the band is left
 * mid-travel. Only where the pointer is coarse, because carrying a picture out of a page is a
 * real thing to do with a mouse and no thing at all to do with a thumb.
 */
const stall = (event: Event) => {
  if (armed.value) event.preventDefault()
}

// The neighbour is a picture of a stop rather than a stop, the same as the one leaving a pass, so
// it is out of the tab order, out of what is read and answers to no name. The template says the
// first two of those as it renders; the names have to be taken off what has been rendered.
watch(beside, (stop) => {
  if (stop == null) return
  void nextTick().then(() => {
    if (aside.value) ghost(aside.value)
  })
})

/**
 * The swap, watched rather than rendered into: the direction arrives with it, and the height
 * has to be taken before the contents leaving are out of the flow.
 *
 * And where the two mechanisms hand over. A change carrying the stop a gesture asked for is a
 * change whose movement has already happened, so the pass is suppressed for it and the track is
 * dropped once the arrived contents are in the flow.
 */
watch(() => props.stop, () => {
  const arrived = asked.value != null && props.stop === asked.value
  played.value = arrived
  asked.value = null
  const going = !arrived && props.direction !== "same"

  if (arrived) {
    // Still travelling until the track is put away: the band is not standing where it rests yet.
    if (settling) clearTimeout(settling)
    settling = null
    travelling.value = true
  } else {
    // A change that is not the arrival a gesture asked for is the page answering with something
    // else — a link followed mid-flight, a stop that has just been removed. The track is put away
    // before the pass rather than left holding a stop nobody is going to.
    if (holding.value) drop()
    travel(going)
  }

  // A stop that is not going anywhere still has to see off whatever was on the page before it,
  // the first one to arrive replacing the loading block, but it holds no height while it does,
  // because nothing is travelling.
  void carry(going).then(() => {
    if (arrived) drop()
  })
})
</script>

<template>
  <div
    ref="shell"
    class="band-swipe"
    :class="{'band-swipe--swipeable': armed, 'band-swipe--held': holding}"
    :data-swipe="direction"
    :data-swipe-mode="mode"
    :data-testid="testid"
    @click.capture="stifle"
    @dragstart="stall"
    @pointercancel="forfeit"
    @pointerdown="grab"
    @pointermove="drag"
    @pointerup="release"
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
        <div
          :ref="takeArriving"
          class="band-swipe__carried"
          :style="holding ? {transform: standing} : undefined"
        >
          <!--
            What is carried is drawn for the stop it is handed, not for the one the page is
            holding.

            A panel is a stop's worth of contents, and there is nothing about a panel that says
            it must be the stop showing: a pass has two of them on the page at once, and a
            gesture that carries the band under a finger wants the neighbour's real contents
            beside the current ones rather than an empty box. So the stop goes down with the
            slot and the page answers for whichever one it is asked about.
          -->
          <slot :stop="stop" />
        </div>
      </Motion>
    </animate-presence>

    <!--
      The neighbour, for the length of a gesture and no longer.

      Out of the flow so the stop showing keeps the page's shape, a width to one side so the drag
      brings it in from the edge the pass would have brought it from, and drawn from the same slot
      the stop showing is: the whole point of the gesture is that what arrives is the real thing
      rather than a placeholder that turns into it.
    -->
    <div
      v-if="beside != null"
      ref="aside"
      aria-hidden="true"
      class="band-swipe__aside"
      inert
      :style="{transform: asideStanding}"
    >
      <slot :stop="beside" />
    </div>
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

/*
 * Only the horizontal axis is claimed, and only where the band drags at all.
 *
 * The browser keeps the vertical one, so the ordinary scroll of the page is untouched and no
 * gesture handler has to guess at it or cancel it. Pinching is kept as well: this is a band of
 * photographs, and a reader who wants a closer look at one is not making a mistake.
 */
.band-swipe--swipeable {
  touch-action: pan-y pinch-zoom;
}

/* A finger dragging a page of words across the screen is not selecting them. */
.band-swipe--held {
  user-select: none;
}

.band-swipe--held .band-swipe__aside,
.band-swipe--held .band-swipe__carried {
  will-change: transform;
}

.band-swipe__aside {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
}
</style>
