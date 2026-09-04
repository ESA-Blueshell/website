<script lang="ts" setup>
import {computed, onBeforeUnmount, onMounted, ref, watch} from "vue"
import {useTravelling} from "./bandTravel"
import {coveredWidth} from "./pictures"
import {useMotionAllowed} from "./useMotionAllowed"

defineOptions({name: "SliceBand"})

export interface SliceItem {
  id: number | string
  /** Where the slice leads, where it leads anywhere. The whole block is the way in. */
  href?: string
  title: string
  /** A line under the title while the slice is shut, and while it is open. */
  meta: string
  /** The image behind it, where there is one. */
  banner: string
  /**
   * The widths that image is stored at, ready for a `srcset`, where it is stored at several.
   *
   * A picture somebody uploaded has them; a file bundled into the frontend is one file and
   * has none, and a slice drawing one simply gets no attribute.
   */
  srcset?: string
  /** Its own dimensions, so the browser reserves its space before the bytes arrive. */
  width?: number
  height?: number
  /** The logo identifying the thing itself, drawn beside the title, where there is one. */
  icon?: string | null
  /** The widths that logo is stored at, ready for a `srcset`. */
  iconSrcset?: string
  /** Its own colour, where it has one; otherwise the band's accent is used. */
  accent?: string
  /**
   * Whether this slice has anything to open onto. Absent means it does.
   *
   * A game always has a line-up to show. A person may have written nothing about themselves,
   * and a slice that grows to reveal an empty panel is worse than one that never offered: half
   * the association's board members wrote no blurb at all, and four whole boards wrote none
   * between them. Where nothing in the band can open, nothing in it moves.
   */
  expandable?: boolean
}

const props = withDefaults(defineProps<{
  items: SliceItem[]
  accent: string
  /** What each slice's data-testid is built from, since the two pages name them differently. */
  testidPrefix: string
  /** Whether the band ends in ways to add another. */
  mayAdd?: boolean
  /**
   * What a band with nothing in it says, drawn as a slice of its own.
   *
   * A sentence in a bar above the band and the way in at the end of the band read as two
   * separate things stacked on one another. Said in a slice, with the plus beside it, the
   * band is one row again and the emptiness is a fact about it rather than a notice over it.
   */
  emptyLabel?: string
  /**
   * What the plus is called, since each band adds a different kind of thing.
   *
   * One pane, not one per way in. Adding a game the association has played before and adding
   * one it has just started are the same intention answered two ways, and two plusses side by
   * side read as two different things to do. The choice is made in the dialog the pane opens.
   */
  addLabel?: string
  /**
   * One slice to open by name, which wins over opening the first.
   *
   * Something just added is the thing to look at, and it is rarely first: the band is in the
   * order it reads in, not the order things were written down. A prop rather than a method
   * because a new set of items reopens the first of them, and a call made before that lands
   * would simply be undone.
   */
  openId?: SliceItem["id"] | null
  /** Whether each slice offers a way to change what it shows. */
  mayEdit?: boolean
  /**
   * How a slice wears its art.
   *
   * `cover` is a game's: the picture fills the slice and the words sit on it. `aside` is a
   * person's: a face holds the left at a width of its own and stays there as the slice opens,
   * so what grows is the room the words get. One component either way, because the hover, the
   * share of the row, the scroll on a phone and the widths a picture is fetched at are the
   * same question whatever the art is of.
   */
  layout?: "cover" | "aside"
}>(), {
  mayAdd: false, addLabel: "Add", emptyLabel: "", openId: null, mayEdit: false, layout: "cover",
})

const emit = defineEmits<{
  (event: "add"): void
  (event: "edit", id: SliceItem["id"]): void
  (event: "go", item: SliceItem): void
  /**
   * Which slice is open, whenever that changes.
   *
   * The band settles this for itself (a hover, a tap, a scroll) and until now nobody
   * outside needed to know. A season change rebuilds the band, and the page is the only thing
   * that outlives it: it holds what was open and hands it back through [openId], so switching
   * season does not also change which game is being read.
   */
  (event: "open", id: SliceItem["id"] | null): void
}>()

const motion = useMotionAllowed()

/**
 * Whether the page this band is on is travelling, which is also how this band got here.
 *
 * A slice opening is a row's layout animated over most of a second, and done mid-pass it is
 * that animation inside a moving subtree, twice over, the band leaving being still on the page
 * with its own slice open. So a band already standing settles once the pass is over and answers
 * no pointer until then, while a band built while this is true was carried in by the pass and
 * arrives open instead: the gesture was the whole animation.
 */
const travelling = useTravelling()

/** Whether a slice has anything behind it. Absent from an item means it has. */
const opens = (index: number): boolean => props.items[index]?.expandable !== false

/**
 * [index] if that slice opens onto anything, and nothing otherwise.
 *
 * Every route to opening a slice goes through this: a pointer, a focus, a click, a scroll, the
 * slice the band settles on when it first draws, and the slice named from outside after
 * something is added. A slice with nothing behind it grows onto an empty panel, which is the
 * defect the whole expandable rule exists to prevent, so a route that cannot tell is a route
 * that must ask.
 */
const openable = (index: number | null): number | null =>
  (index != null && index >= 0 && opens(index) ? index : null)

/** The first slice that opens onto anything, or nothing where none of them do. */
const firstThatOpens = (): number | null => {
  const found = props.items.findIndex((_, index) => opens(index))
  return found >= 0 ? found : null
}

/**
 * Which slice is open. Nothing is open for the first frame so the opening of the first one is
 * something the visitor sees happen rather than something already done — except under reduced
 * motion, and except a band a pass carried in, both of which are simply open from the start.
 */
const open = ref<number | null>(null)
const slices = ref<HTMLElement[]>([])

/**
 * A slice the visitor opened themselves. Stacked, the scroll decides which slice is open, and
 * without this a tap was undone by the next observation: the slice opened and shut again
 * before a finger left the screen. The choice stands until the visitor scrolls, at which point
 * the scroll is their intent again.
 *
 * Their scroll, though, and not any scroll. Opening a slice reflows the page under the finger
 * that opened it, and a reflow moves the scroll position: watching the `scroll` event alone,
 * the tap was undone by its own consequence, and the observer then reasserted the neighbour —
 * whose face, at a whole portrait's height, still fills the middle of the screen. So what
 * releases the choice is an input that only a visitor produces.
 */
const tapped = ref<number | null>(null)

/** Stacked, there is no pointer to move across the slices, so the scroll does the choosing. */
const stacked = () =>
  typeof window !== "undefined" && window.matchMedia("(max-width: 767px)").matches

let watcher: IntersectionObserver | null = null

const watchScroll = () => {
  watcher?.disconnect()
  if (!stacked() || typeof IntersectionObserver === "undefined") return
  watcher = new IntersectionObserver(entries => {
    // Whichever slice has most of itself in the middle band of the screen is the one open.
    const best = entries
      .filter(entry => entry.isIntersecting)
      .sort((a, b) => b.intersectionRatio - a.intersectionRatio)[0]
    if (!best) return
    if (tapped.value !== null) return
    // A slice with nothing behind it is scrolled past rather than opened: what was open stays
    // open, which is the same answer a pointer crossing it gives.
    const index = openable(slices.value.indexOf(best.target as HTMLElement))
    if (index != null) open.value = index
  }, {rootMargin: "-42% 0px -42% 0px", threshold: [0, 0.25, 0.5, 1]})
  slices.value.forEach(el => el && watcher?.observe(el))
}

/**
 * When the visitor last pressed a slice, so the page settling after it is not read as a scroll.
 *
 * Only a press: the band also opens a slice of its own accord — the first as it draws, the one
 * named from outside after something is added — and stamping those swallows the first scroll a
 * reader makes, which is the one thing the stacked band relies on. Never pressed is `-Infinity`
 * rather than nought, `performance.now()` counting from the page loading: against nought every
 * scroll in the first second falls inside the window and is ignored, which is most of the life a
 * reader has when they arrive.
 */
let chose = Number.NEGATIVE_INFINITY

/**
 * The visitor scrolled, so the middle of the screen decides again — but only their scroll.
 *
 * Two other things move the page and neither is the visitor changing their mind. A slice opening
 * reflows everything under it, and read as a scroll the tap is undone by its own consequence,
 * the slice handed back to whichever neighbour still fills the middle of the screen — so a
 * scroll inside the opening is not one. A gesture along the band scrolls too, for the length of
 * the pass, and taken mid-flight the middle of the screen decides which slice the page above is
 * told about and reopens a stop later.
 */
const releaseTap = () => {
  if (travelling.value) return
  if (performance.now() - chose < motion.duration(OPEN_SECONDS) * 1000 + 120) return
  tapped.value = null
}

/**
 * The share of the row an open slice takes, which is what `flex-grow` gives it.
 *
 * Kept here as a number because the figure a banner is fetched at is worked out from it: the
 * stylesheet and this have to say the same thing, and there is no way to ask the stylesheet.
 */
const OPEN_SHARE = 3.4

/** Roughly what the way-in slice takes out of the row, which is `clamp(6.5rem, 11%, 10rem)`. */
const WAY_IN_SHARE = 0.11

/**
 * How tall a stacked face is drawn, as a multiple of its own width.
 *
 * The picture's own proportions, so the whole photograph is shown: every recorded portrait is
 * between 1.36 and 1.55 times taller than wide, so any single figure of this layout's choosing
 * throws away most of half of them. Given a box of the picture's own ratio, `object-fit: cover`
 * crops nothing. `FACE_FALLBACK` stands in only where dimensions are unknown, as for a file bundled
 * into the frontend rather than measured by the api. A number here for the reason `OPEN_SHARE` is:
 * the fetch width is worked out from the box the picture is drawn in, the stylesheet decides that
 * box, and there is no way to ask it.
 */
const FACE_FALLBACK = 3 / 2

/** How tall [item]'s picture is per unit of its width, or the fallback where it never said. */
const faceAspect = (item: SliceItem | undefined): number => {
  const w = item?.width
  const h = item?.height
  return w && h ? h / w : FACE_FALLBACK
}

/**
 * What a slice states for itself: its own colour, and the shape of its own picture.
 *
 * The aspect goes down as a custom property rather than into a height, because which of the two
 * layouts is drawn is the stylesheet's business: a row of faces never asks, and a stacked one
 * multiplies it by the slice's own width.
 */
const sliceStyle = (item: SliceItem): Record<string, string> => ({
  ...(item.accent ? {"--accent": item.accent} : {}),
  "--face-aspect": String(faceAspect(item)),
})

/**
 * How long a band of faces takes to open, in seconds.
 *
 * Said here and nowhere else: the stylesheet needs the figure twice, raw for the row resizing and
 * clamped for a stacked slice's two movements, and a figure said in two languages can disagree with
 * itself. So the band hands both down. Clamped here because a stylesheet cannot clamp — all it can
 * do under reduced motion is remove the transition, and on a phone the dissolve and the words
 * growing in are the only thing saying a slice opened at all. The ceiling is the motion policy's,
 * so the figure passes through it.
 */
const OPEN_SECONDS = 0.95

const viewport = ref(typeof window === "undefined" ? 0 : window.innerWidth)

/**
 * The width a slice has while nothing is open, handed to the stylesheet as `--share`.
 *
 * A face keeps the width it had when its slice opens: the slice grows by a fixed measure for
 * the words instead of by a multiple of the row, and the stylesheet needs the figure this
 * arithmetic already knows to hold the picture still. Only where there are faces, and only
 * across a row: stacked, the portrait has a column of its own.
 */
const shutShare = computed<number | null>(() => {
  if (props.layout !== "aside" || viewport.value === 0 || stacked()) return null
  const count = props.items.length
  if (count === 0) return null
  return Math.round((viewport.value * (props.mayAdd ? 1 - WAY_IN_SHARE : 1)) / count)
})

/**
 * What the band hands its own stylesheet: a colour, two durations and a width.
 *
 * `--slice-open` only for the layout that draws faces — a band of games keeps the shorter one
 * the stylesheet states for itself, because a picture and a list are glanced at where a face and
 * a paragraph are read. `--slice-ease` is the same figure with the visitor's preference applied,
 * for the movements the stylesheet's own reduced-motion blankets cannot reach.
 */
const bandStyle = computed<Record<string, string>>(() => ({
  "--accent": props.accent,
  "--slice-ease": `${motion.duration(OPEN_SECONDS)}s`,
  ...(props.layout === "aside" ? {"--slice-open": `${OPEN_SECONDS}s`} : {}),
  ...(shutShare.value ? {"--share": `${shutShare.value}px`} : {}),
}))

/**
 * How wide a slice's box is, worked out rather than measured.
 *
 * From the two things that decide it — how many slices share the row, how wide the window is —
 * because measuring waits for layout, and a measurement taken as a slice opens reads the box it
 * had shut. Stacked on the `aside` layout a slice is the band's full width and that one is
 * measured, its width not moving as it opens; only that layout, since a stacked `cover` slice
 * fetches at the window and measuring it would change the bytes every landscape page asks for.
 */
const boxWidth = (index: number): number => {
  const width = viewport.value
  if (width === 0) return 0
  if (stacked()) return props.layout === "aside" ? slices.value[index]?.clientWidth || width : width

  const count = props.items.length
  if (count === 0) return width
  const band = width * (props.mayAdd ? 1 - WAY_IN_SHARE : 1)
  // A face holds its share whether or not its slice is open, so every box is the same width.
  if (props.layout === "aside") return Math.ceil(band / count)
  // One slice is open, unless nothing is yet.
  const units = open.value == null ? count : count - 1 + OPEN_SHARE
  const share = index === open.value ? OPEN_SHARE : 1
  return Math.ceil((band * share) / units)
}

/**
 * The width each banner is fetched at, which is wider than the slice wherever the slice is
 * tall and narrow.
 *
 * A banner covers its slice, so a box taller than it is wide is filled by its height and painted
 * past both edges: a slice two hundred across and three hundred tall draws a sixteen-by-nine
 * banner some six hundred wide, and asking for its share alone fetches the bottom of the ladder.
 * Height is measured where width is worked out, being the one figure the arithmetic cannot reach;
 * no height leaves the share of the row standing on its own.
 */
const wanted = (index: number): number => {
  const width = boxWidth(index)
  // Stacked, the face is not the slice: it takes the full width at the picture's own shape, and
  // the words read below it. Asked for the slice's height the arithmetic answered with the room
  // the prose took as well, so a slice with a lot to say fetched its picture at half again the
  // pixels the face is drawn at.
  const height = props.layout === "aside" && stacked()
    ? width * faceAspect(props.items[index])
    : slices.value[index]?.clientHeight ?? 0
  return coveredWidth({
    boxWidth: width,
    boxHeight: height,
    imageWidth: props.items[index]?.width,
    imageHeight: props.items[index]?.height,
  })
}

/**
 * What each banner has been asked for, never less than it was already asked for.
 *
 * Only ever upward, for two reasons: a browser will not swap a picture it has for a smaller
 * one, so asking for less achieves nothing; and a slice that has been opened once has the
 * wider copy in cache, so asking for it again is a cache hit rather than a download.
 */
const askedFor = ref<number[]>([])

const grow = () => {
  const next = props.items.map((_, index) => Math.max(askedFor.value[index] ?? 0, wanted(index)))
  if (next.some((width, index) => width !== (askedFor.value[index] ?? 0))) askedFor.value = next
}

/**
 * Which banners have had a copy arrive.
 *
 * The second fetch waits for the first to land rather than for the next frame. Both at once
 * puts two copies of every picture on the wire together, which on a slow connection is the one
 * thing this is meant to avoid: the small copy is there to be quick, and racing it with the
 * large one spends the saving before it is made.
 */
const arrived = ref<Set<number>>(new Set())

const onLoaded = (index: number) => {
  if (arrived.value.has(index)) return
  arrived.value = new Set(arrived.value).add(index)
  grow()
}

const onResize = () => {
  viewport.value = window.innerWidth
  grow()
}

/**
 * What the browser is promised a banner will be drawn at.
 *
 * Understated until a copy has arrived, and by a lot, since a collapsed slice is dimmed almost
 * to a silhouette: the worked-out figure replaces it once there is a picture to replace, and
 * grows again as a slice opens. Except a stacked face, which takes the slice's full width and is
 * the first thing such a slice is — understating it fetches a face to be blown up and then
 * swapped, which is the swap this exists to avoid. Said as a media condition rather than asked
 * of `stacked()`, so the browser re-answers it when the screen turns.
 */
const sizesOf = (index: number): string => {
  const asked = arrived.value.has(index) ? askedFor.value[index] : 0
  if (asked) return `${asked}px`
  return props.layout === "aside"
    ? "(min-width: 768px) 200px, 100vw"
    : "(min-width: 768px) 200px, 50vw"
}


/**
 * Opening a slice and going to it are the same gesture, one after the other: a slice that is
 * already showing what it holds has said what it has to say, so the next click follows it.
 * Stacked, that is the second tap; side by side, the pointer has already opened it.
 */
/** A pointer or a focus arriving on a slice, which opens it only where it opens onto anything. */
const reach = (index: number) => {
  if (travelling.value) return
  if (opens(index)) open.value = index
}

const choose = (index: number) => {
  if (!opens(index)) return
  const item = props.items[index]
  if (item?.href && index === open.value) {
    emit("go", item)
    return
  }
  open.value = index
  if (stacked()) tapped.value = index
  chose = performance.now()
}

const indexOfNamed = () => props.items.findIndex(item => item.id === props.openId)

/** Whether a pass carried this band in rather than drawing it where it stands. */
const carriedIn = travelling.value

/**
 * Whether that arrival is still owed.
 *
 * Spent by the first slices the band actually holds: the stop under a finger is only read once
 * the gesture claims the axis, so a band can be carried in with nothing in it and its slices
 * land long after the pass. A change the visitor makes afterwards opens from nothing as ever.
 */
let arriving = carriedIn

/**
 * Opens the slice the band is meant to open: the named one where it opens onto anything, else
 * the first of [fallbacks] that does, in the order given.
 *
 * The one answer every route asks for, fallbacks included — a slice growing onto an empty panel
 * is the defect the expandable rule exists to prevent, so a route that cannot tell must not have
 * to remember. Stacked, the scroll decides what is open, so the choice is held against the
 * observer the way a tap is where the page named it, or where the band is not yet where it lands.
 */
const openChosen = (fallbacks: (number | null)[]) => {
  const named = openable(indexOfNamed())
  const target = [named, ...fallbacks.map(openable)].find(index => index != null) ?? null
  open.value = target
  if (target != null && stacked() && (named != null || arriving)) tapped.value = target
}

// Taken as the band is built rather than on mount: `carry()` measures the arriving panel between
// the two, so a slice opened on mount transitions out of a box the browser has taken as shut.
if (carriedIn) {
  openChosen([firstThatOpens()])
  // An answer holding no slices at all leaves this standing, so a team added to an empty season
  // swiped to arrives open as well.
  arriving = props.items.length === 0
}

const settle = () => {
  // Not while the page is moving. The watcher below settles the band the moment it stops, so
  // nothing is lost by waiting and a pass is a slide rather than a slide with two rows of
  // slices animating inside it.
  if (travelling.value) return
  // The first slice that opens where none is named, so a band where nothing does settles on
  // nothing. This runs on mount too, which is where a band rebuilt around a slice that was
  // just added arrives with the name already set and no change left to react to.
  openChosen([firstThatOpens()])
}

// The pass over, a band that was standing when it began opens what it was going to open, as
// does one whose pass was refused and brought no stop to build. A band carried in reaches this
// too and re-runs the same choice, which changes nothing.
watch(travelling, (going) => {
  if (!going) settle()
})

onMounted(() => {
  // Only a band drawn where it stands is left to open: one a pass carried in already is.
  if (!carriedIn) {
    if (!motion.decorative.value) {
      settle()
    } else {
      requestAnimationFrame(() => requestAnimationFrame(settle))
    }
  }
  watchScroll()
  window.addEventListener("scroll", releaseTap, {passive: true})
  window.addEventListener("resize", onResize)
})

onBeforeUnmount(() => {
  watcher?.disconnect()
  window.removeEventListener("scroll", releaseTap)
  window.removeEventListener("resize", onResize)
})

// A change of what is shown brings a different set, so the first of those opens in its turn
// unless one of them is named, which is the set arriving because that one was just added.
/**
 * A change of what is shown keeps the slice that was open where the same one is still
 * there. Switching season re-answers with much the same band, and reopening the first of them
 * each time made every switch look like a page rebuilding itself.
 */
watch(() => props.items, (items, before) => {
  slices.value = []
  // Different pictures, so neither the figures asked for the last ones nor the fact that they
  // arrived says anything about these.
  askedFor.value = []
  arrived.value = new Set()
  // A hold names a place in the set that has just gone, so it is dropped here and re-taken by
  // the answer below where a slice is named.
  tapped.value = null
  // The slices a pass was carrying, arriving at last: open at once rather than grown, which is
  // the arrival the band was owed. Spent by them, so the next change is the visitor's own again.
  if (arriving && items.length > 0) {
    openChosen([firstThatOpens()])
    arriving = false
    requestAnimationFrame(watchScroll)
    return
  }
  const held = before?.[open.value ?? -1]?.id
  const stillThere = held == null ? -1 : items.findIndex(item => item.id === held)
  // Only a band that has nothing in common with the one before it opens from nothing, so such
  // a set is offered no fallback at all and a named slice is all that opens.
  const fromNothing = motion.decorative.value && stillThere < 0
  // The one that was open where it is still here, else the first that opens onto anything: the
  // slice that was held may no longer open, and the first of a set is one nobody named.
  openChosen(fromNothing ? [] : [stillThere, firstThatOpens()])
  if (open.value === null) requestAnimationFrame(() => requestAnimationFrame(settle))
  requestAnimationFrame(watchScroll)
})

/**
 * Opens the named slice, which is the one just added.
 *
 * After the update rather than during it, so the set it belongs to is the one on screen.
 * Stacked, this is held the same way a tap is held, until the visitor scrolls, at which
 * point the scroll is their intent again. Scrolling to it ourselves would be that scroll,
 * and would hand the choice straight back to whichever slice happened to be in the middle.
 */
watch([() => props.openId, () => props.items], () => {
  // Nothing to open where the named slice has nothing behind it: something was added and has
  // nothing written about it yet, which is the ordinary case for anything just recorded.
  const named = openable(indexOfNamed())
  if (named == null) return
  open.value = named
  if (stacked()) tapped.value = named
}, {flush: "post"})

// Said by id rather than by position: the page holding it hands it to a different band, where
// the same item rarely sits in the same place.
watch(open, (index) => {
  emit("open", index == null ? null : props.items[index]?.id ?? null)
  // The slice being read wants the wider copy, and knows how wide without waiting for the
  // row to finish moving.
  grow()
})
</script>

<template>
  <div
    class="slices"
    :data-testid="`${testidPrefix}-slices`"
    :style="bandStyle"
  >
    <section
      v-for="(item, index) in items"
      :key="item.id"
      :ref="el => { if (el) slices[index] = el as HTMLElement }"
      class="slice"
      :class="{
        'slice--open': index === open,
        'slice--aside': layout === 'aside',
        'slice--bare': !item.banner,
        'slice--first': index === 0,
        'slice--last': index === items.length - 1 && !mayAdd,
      }"
      :data-testid="`${testidPrefix}-${item.id}`"
      :style="sliceStyle(item)"
      @focusin="reach(index)"
      @mouseenter="reach(index)"
    >
      <!--
        Offered only to somebody who may take it up, and belonging to the slice it sits on.
        Where there is a pointer it waits for one; where there is not, it stands.
      -->
      <button
        v-if="mayEdit"
        :aria-label="`Edit ${item.title}`"
        class="slice__edit"
        :data-testid="`${testidPrefix}-edit-${item.id}`"
        type="button"
        @click.stop="emit('edit', item.id)"
      >
        <svg
          aria-hidden="true"
          fill="none"
          stroke="currentColor"
          stroke-width="2"
          viewBox="0 0 24 24"
        >
          <path d="M4 20h4L19 9a2.8 2.8 0 0 0-4-4L4 16v4Z" />
        </svg>
      </button>

      <!--
        `sizes` is a guess before the band has been laid out and a measurement afterwards: see
        `sizesOf`. The guess understates, so the first picture to arrive is a small one.
      -->
      <!--
        No testid of its own: the specs reach it through the slice, and a testid built from
        the same prefix the slices use would be caught by their own prefix selector.
      -->
      <img
        v-if="item.banner"
        alt=""
        class="slice__banner"
        :height="item.height"
        :sizes="sizesOf(index)"
        :src="item.banner"
        :srcset="item.srcset"
        :width="item.width"
        @load="onLoaded(index)"
      >
      <!-- Only over art, and only under the text and the icon: a wash across the whole
           picture was filtering the art rather than carrying the names. -->
      <span
        v-if="item.banner"
        aria-hidden="true"
        class="slice__glow"
      />

      <button
        class="slice__body"
        :aria-expanded="index === open"
        type="button"
        @click="choose(index)"
      >
        <span class="slice__heading">
          <span
            aria-hidden="true"
            class="slice__tick"
          />
          <span class="slice__titles">
            <!--
              Decorative: the name is right beside it and says the same thing, so a reader who
              cannot see the logo is told nothing twice.

              One `sizes` for both states rather than a viewport query: the logo grows when the
              slice opens, which no media query describes, so the browser is told the largest
              it is ever drawn and picks a candidate that is enough either way.
            -->
            <img
              v-if="item.icon"
              alt=""
              class="slice__icon"
              sizes="40px"
              :src="item.icon"
              :srcset="item.iconSrcset"
            >
            <span class="slice__name">{{ item.title }}</span>
          </span>
          <span class="slice__count">{{ item.meta }}</span>
        </span>

        <span class="slice__reveal">
          <slot
            :item="item"
            name="details"
          />
        </span>
      </button>
    </section>

    <!--
      A pane of its own rather than a control floating over the band: adding belongs to the
      band, and the band is the page. Narrow, because it is a way in rather than a thing to
      read. One pane per way in, so a choice is made by pressing rather than inside a dialog.
    -->
    <!-- Nothing to show, and a way to change that beside it rather than under it. -->
    <section
      v-if="items.length === 0 && emptyLabel"
      class="slice slice--empty slice--first"
      :data-testid="`${testidPrefix}-empty-slice`"
    >
      <span class="slice__body slice__nothing">
        <span class="slice__heading">
          <span class="slice__name">{{ emptyLabel }}</span>
          <!-- Whatever else is worth saying where there is nothing: on a game's page, the
               last season it did play, which is a way on rather than a dead end. -->
          <slot name="empty" />
        </span>
      </span>
    </section>

    <section
      v-if="mayAdd"
      class="slice slice--add slice--last"
    >
      <button
        class="slice__body slice__add"
        :data-testid="`${testidPrefix}-add`"
        type="button"
        @click="emit('add')"
      >
        <span
          aria-hidden="true"
          class="slice__plus island-plus"
        >
          <svg
            class="island-plus__edge"
            fill="none"
            viewBox="0 0 100 100"
          >
            <path d="M38 2 H62 V38 H98 V62 H62 V98 H38 V62 H2 V38 H38 Z" />
          </svg>
        </span>
        <span class="slice__heading">
          <span
            aria-hidden="true"
            class="slice__tick"
          />
          <span class="slice__name">{{ addLabel }}</span>
        </span>
      </button>
    </section>
  </div>
</template>

<style scoped>
/*
 * Slices, not cards: full width, square, and cut apart on the diagonal so the seams read as
 * one band rather than as a row of boxes. The cut is a fixed number of pixels so the angle
 * does not change as a slice opens.
 */
.slices {
  --cut: 30px;
  /* How long a slice takes to grow, and everything that has to move with it. */
  --slice-open: 620ms;

  display: flex;
  width: 100%;
  min-height: 22rem;
}

.slice {
  position: relative;
  flex: 1 1 0;
  min-width: 0;
  overflow: hidden;
  /* A slice's own business stays its own: the row is six pictures with masks and washes over
     them, and without this a layout in one of them is a paint of all six. */
  contain: layout paint;
  background-color: var(--color-surface);
  clip-path: polygon(var(--cut) 0, 100% 0, calc(100% - var(--cut)) 100%, 0 100%);
  margin-left: calc(var(--cut) * -1);
  transition: flex-grow var(--slice-open) cubic-bezier(0.22, 1, 0.36, 1);
}

/* The cut edge, drawn. Two slices of the same tone meet on an invisible diagonal in light, so
   the boundary is a sliver clipped to the same geometry, with no angle to keep in step with the
   slice's height. Not on the first: there is nothing to its left to divide it from. */
.slice:not(.slice--first)::after {
  content: "";
  position: absolute;
  inset: 0;
  background: var(--color-hairline);
  clip-path: polygon(var(--cut) 0, calc(var(--cut) + 1.5px) 0, 1.5px 100%, 0 100%);
  pointer-events: none;
}

.slice--first {
  clip-path: polygon(0 0, 100% 0, calc(100% - var(--cut)) 100%, 0 100%);
  margin-left: 0;
}

.slice--last {
  clip-path: polygon(var(--cut) 0, 100% 0, 100% 100%, 0 100%);
}

/* Hidden rather than transparent, for the same reason as the strip's: a see-through
   affordance still answers a click. */
.slice__edit {
  position: absolute;
  top: 10px;
  right: 12px;
  z-index: 3;
  visibility: hidden;
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  background: none;
  border: 0;
  color: var(--color-chalk);
  cursor: pointer;
}

.slice__edit svg {
  width: 23px;
  height: 23px;
}

/*
 * Focus on the slice rather than on the affordance itself: hidden means unfocusable, so an
 * affordance that waited to be focused could never be reached. Focus lands on the slice
 * first, which reveals the affordances sitting on it, and the next tab reaches them.
 */
.slice:hover .slice__edit,
.slice:focus-within .slice__edit {
  visibility: visible;
}

/*
 * Except where the focus the slice holds is a pointer's. A click focuses the body it lands
 * on, and `:focus-within` cannot tell that focus from a keyboard's, so a slice clicked open
 * went on offering to be edited long after the pointer had left it, and read as a control
 * that had latched.
 *
 * Written as an exception to the rule above rather than folded into it. The obvious fold,
 * asking the slice for `:has(:focus-visible)`, breaks the keyboard route: the affordances are
 * revealed only while the body holds the focus, so the tab that hands the focus over hides
 * them in the same recalc, and the browser drops the focus rather than landing it on
 * something that has just gone. `:focus-within` survives that hand-off because the focus
 * never leaves the slice, and the exception below does not apply during it: the body has let
 * the focus go by then.
 *
 * Asked of a pointer rather than written to outrank the standing rule below. Where there is
 * nothing to hover with, a tap is the only way to reach anything and leaves the focus behind
 * it, so an exception about pointers would hide every affordance on the page the moment one
 * was used.
 */
@media (hover: hover) {
  .slice:not(:hover):has(.slice__body:focus:not(:focus-visible)) .slice__edit {
    visibility: hidden;
  }
}

@media (hover: none) {
  .slice__edit {
    visibility: visible;
  }
}

/* Wider than the plus beside it and quieter than a slice with a picture: it is a statement,
   not somewhere to go. */
.slice--empty {
  flex: 2 1 0;
  background-color: color-mix(in oklab, var(--color-chalk) 4%, transparent);
}

.slice__nothing {
  display: flex;
  align-items: flex-end;
  width: 100%;
  height: 100%;
  color: var(--color-ash);
}

/* The association's blue, patterned and darkened, which makes the way in the one saturated
   thing on the page in either theme. */
.slice--add {
  flex: 0 0 clamp(6.5rem, 11%, 10rem);

  --color-chalk: #ffffff;

  background-color: var(--color-brand);
  /* Taken down a fifth, which is where white on it clears AA, and the pattern still reads. */
  background-image:
    linear-gradient(var(--add-tint, transparent), var(--add-tint, transparent)),
    linear-gradient(oklch(0 0 0 / 22%), oklch(0 0 0 / 22%)),
    url("../../assets/bg/shelly-bg-blue.jpg");
  background-size: auto, auto, 135px 77px;
  background-repeat: repeat;
}

.slice--add:hover {
  --add-tint: oklch(1 0 0 / 12%);
}

.slice__add {
  position: relative;
  color: var(--color-chalk);
}

/*
 * The mark on the block rather than the middle of a stack, with the label below it where a
 * slice's name sits. Skewed to the seam this band is cut on, so it leans with the slices.
 */
.slice__plus {
  position: absolute;
  top: 50%;
  left: 50%;
  /* Centred and skewed in the one property: the two split across `translate` and `transform`
     did not compose here, which left the mark hanging off the edge of its own block. */
  transform: translate(-50%, -50%) skewX(-5deg);
  width: min(56%, 92px);
}

.slice--add:hover .slice__plus {
  opacity: 0.95;
  background: color-mix(in oklab, var(--color-chalk) 20%, transparent);
}

.slice__plus svg {
  width: 100%;
  height: auto;
  aspect-ratio: 1;
}

.slice--first.slice--last {
  clip-path: none;
}

.slice--open {
  flex-grow: 3.4;
  z-index: 1;
}

.slice__banner {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  scale: 1.06;
  transition: scale 900ms cubic-bezier(0.22, 1, 0.36, 1);
}

.slice--open .slice__banner {
  scale: 1;
}

.slice__glow {
  position: absolute;
  inset: auto 0 -14% 0;
  height: 72%;
  background: radial-gradient(
    64% 118% at 16% 100%,
    color-mix(in oklab, var(--color-ground) 92%, transparent) 0%,
    color-mix(in oklab, var(--color-ground) 62%, transparent) 42%,
    transparent 74%
  );
  filter: blur(26px);
  pointer-events: none;
}

.slice__body {
  position: relative;
  display: flex;
  height: 100%;
  width: 100%;
  flex-direction: column;
  justify-content: flex-end;
  gap: 0.75rem;
  padding: 1.5rem calc(var(--cut) + 0.5rem);
  text-align: left;
  cursor: pointer;
}

.slice__heading {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
}

.slice__tick {
  width: 2rem;
  height: 3px;
  margin-bottom: 0.6rem;
  background-color: var(--accent);
  scale: 0.35 1;
  transform-origin: left center;
  transition: scale 520ms cubic-bezier(0.22, 1, 0.36, 1);
}

.slice--open .slice__tick {
  scale: 1;
}

/* The logo and the name read as one line, so they sit on a row and grow together. */
.slice__titles {
  display: flex;
  gap: 0.5rem;
  align-items: center;
}

.slice__icon {
  flex: none;
  width: 2rem;
  height: 2rem;
  object-fit: contain;
  transition: width 520ms cubic-bezier(0.22, 1, 0.36, 1),
    height 520ms cubic-bezier(0.22, 1, 0.36, 1);
}

.slice--open .slice__icon {
  width: 2.5rem;
  height: 2.5rem;
}

.slice__name {
  font-family: var(--font-display);
  font-size: 1rem;
  line-height: 1.1;
  text-transform: uppercase;
  color: var(--color-chalk);
}

.slice--open .slice__name {
  font-size: 1.5rem;
}

.slice__count {
  font-size: 0.7rem;
  letter-spacing: 0.02em;
  color: var(--color-ash);
}

/*
 * What a slice opens onto belongs to the open one. A closed slice keeps it in the document,
 * since it is one button and its label should say what it holds, but gives it no room and no ink.
 */
.slice__reveal {
  display: flex;
  flex-direction: column;
  gap: 0.65rem;
  overflow: hidden;
  max-height: 0;
  opacity: 0;
  transition: max-height 560ms cubic-bezier(0.22, 1, 0.36, 1), opacity 320ms ease;
}

.slice--open .slice__reveal {
  max-height: 12rem;
  opacity: 1;
}

/* A reveal clipped at its last line is worse than a taller slice. */
@media (max-width: 767px) {
  .slice--open .slice__reveal {
    max-height: 22rem;
  }
}

/*
 * The rules below dress what a page renders into the details slot. That content is compiled
 * in the page's own scope, not this component's, so a plain scoped selector never matches it
 * which left the revealed lines running together as one line of text.
 */
:slotted(.slice__group) {
  display: block;
}

:slotted(.slice__group-label) {
  display: block;
  font-size: 0.6rem;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: var(--color-ash);
}

:slotted(.slice__entries) {
  display: flex;
  flex-wrap: wrap;
  gap: 0.35rem 1.5rem;
  margin-top: 0.3rem;
}

/*
 * One member, one block. Run along a line the handle and the name beside it read as two
 * players; stacked, with the handle leading, it is clear which is the person and which is
 * what they play under.
 */
:slotted(.slice__entry) {
  display: flex;
  min-width: 0;
  flex-direction: column;
  line-height: 1.15;
}

:slotted(.slice__entry-handle) {
  font-size: 0.95rem;
  color: var(--color-chalk);
}

:slotted(.slice__link) {
  align-self: flex-start;
  margin-top: 0.9rem;
  font-family: var(--font-display);
  font-size: 0.7rem;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--accent);
  transition: opacity 200ms ease;
}

:slotted(.slice__link):hover {
  opacity: 0.75;
}

:slotted(.slice__entry-name) {
  font-size: 0.7rem;
  letter-spacing: 0.01em;
  color: color-mix(in oklab, var(--color-ash) 85%, transparent);
}

/* Beside the part they played rather than under it: it qualifies the role, it is not a
   second fact about the person. */
:slotted(.slice__entry-role) {
  display: block;
  font-size: 0.7rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: color-mix(in oklab, var(--accent) 82%, var(--color-chalk));
}

/* A caption, so it is held to a couple of lines and the markdown inside it stays inline. */
:slotted(.slice__entry-note) {
  display: block;
  margin-top: 0.15rem;
  max-width: 22rem;
  font-size: 0.72rem;
  line-height: 1.35;
  color: color-mix(in oklab, var(--color-ash) 92%, transparent);
}

:slotted(.slice__entry-note p) {
  display: inline;
  margin: 0;
}

/* Stacked on a narrow screen, where a row of slices would leave each one a sliver. The cut
   turns with them so the seams still read as diagonal. */
@media (max-width: 767px) {
  .slices {
    --cut: 22px;

    flex-direction: column;
    min-height: 0;
  }

  .slice--add {
    flex: 0 0 auto;
    min-height: 7rem;
  }

  .slice__plus {
    width: 44px;
  }

  .slice {
    clip-path: polygon(0 var(--cut), 100% 0, 100% calc(100% - var(--cut)), 0 100%);
    margin-left: 0;
    margin-top: calc(var(--cut) * -1);
    min-height: 8.5rem;
  }

  .slice--first {
    clip-path: polygon(0 0, 100% 0, 100% calc(100% - var(--cut)), 0 100%);
    margin-top: 0;
  }

  /*
   * The drawn divider turns with the seam.
   *
   * Across a row the boundary between two slices is their leaning left edge, so the sliver is
   * tall and thin and leans by `--cut` over the slice's height. Stacked, the boundary is the
   * leaning *top* edge, and the same sliver left as it was drew a near-vertical hairline down
   * the inside of every slice, crossing the words instead of dividing anything.
   *
   * So it is wide and thin here, and leans by `--cut` across the slice's width: the same 1.5px
   * line on the same diagonal as the cut it is drawn on, running between one slice and the next
   * rather than through either.
   */
  .slice:not(.slice--first)::after {
    clip-path: polygon(0 var(--cut), 100% 0, 100% 1.5px, 0 calc(var(--cut) + 1.5px));
  }

  .slice--last {
    clip-path: polygon(0 var(--cut), 100% 0, 100% 100%, 0 100%);
  }

  /* Stacked, an open slice needs room for its reveal rather than a share of a row. */
  .slice--open {
    flex-grow: 1;
    min-height: 17rem;
  }

  .slice__body {
    padding: 1.75rem 1.25rem;
  }
}

/*
 * A slice whose art is a face rather than a landscape.
 *
 * A portrait holds the left of the slice at a width of its own and stays there as the slice
 * opens, so what grows is the room the words get rather than the picture. Collapsed, the
 * portrait is the whole slice and the name sits on it over a scrim; open, the name has moved
 * off the face and the description has arrived beside it. The scrim goes with the name, so a
 * face is never dimmed for the sake of text that is no longer on it.
 */
.slice--aside {
  /* The face's own width: its share of the row, which the band works out and hands down.
     The column stands in until the row has been laid out. */
  --face: var(--share, clamp(7.5rem, 13vw, 12rem));
  /* What the words get, which is a measure to read on rather than a share of the row. */
  --blurb: clamp(15rem, 20vw, 22rem);
  /* Where the light on the panel comes from: the picture's own right edge. */
  --lit-from: var(--face);

  /* No ground of its own, the way the banner's words have none: the washes are laid straight
     over the island's own patterned ground, which is what makes them read as see-through. */
  background-color: transparent;
  transition:
    flex-grow var(--slice-open) cubic-bezier(0.22, 1, 0.36, 1),
    flex-basis var(--slice-open) cubic-bezier(0.22, 1, 0.36, 1);
}

/*
 * Open, the slice grows by what the words need and by nothing else.
 *
 * A multiple of the row took the face down with it, because the words were then given room
 * measured against a picture that had to shrink to make it. The face keeps its share, the
 * words get a fixed measure beside it, and the slices either side give up the difference.
 */
.slice--aside.slice--open {
  flex: 0 0 calc(var(--face) + var(--blurb));
}

/*
 * The surface the blurb is read on, under the picture rather than over it.
 *
 * Under, so nothing is ever laid across a face: what reveals it is the picture's own right
 * edge dissolving, and shut, the picture covers the slice and the panel is simply not seen.
 *
 * The page's own header, one band down: a soft blue at the top left thinning away from it, and
 * the ground gathering towards the foot. Nothing opaque, so what is behind it is the island's
 * patterned ground rather than a grey panel laid on one, and the words are read on the page
 * rather than on a box.
 *
 * The blue starts where the picture's right edge is, so the same gradient that lights the
 * panel is the colour the picture's dissolve fades into.
 *
 * Drawn from that corner rather than clipped to it. Clipped, its own edge landed on the
 * picture's dissolve, where the picture is nearly transparent, and drew the line the dissolve
 * exists to avoid.
 *
 * And it reaches the panel's own top right corner. Dying before it, the colour ran out
 * somewhere down the slice's leaning right edge and drew a boundary of its own across the top,
 * which is the line the whole thing exists not to have. What thins is the wash downwards and
 * away from the light, not the wash before the corner.
 */
.slice--aside::before {
  content: "";
  position: absolute;
  inset: 0;
  background-image:
    radial-gradient(
      190% 250% at var(--lit-from) 0,
      color-mix(in oklab, var(--accent, var(--color-brand)) var(--board-wash-on), transparent) 0%,
      color-mix(in oklab, var(--accent, var(--color-brand)) var(--board-wash), transparent) 46%,
      transparent 100%
    ),
    linear-gradient(
      to right,
      transparent calc(var(--lit-from) - 3rem),
      color-mix(in oklab, var(--accent, var(--color-brand)) var(--board-wash), transparent) var(--lit-from),
      transparent 96%
    ),
    linear-gradient(to bottom, transparent 34%, var(--band-ground));
  pointer-events: none;
}

/* Taller than a cover band, because a face needs the room a landscape does not. And slower, but
   that figure is `OPEN_SECONDS` in the script above and arrives as `--slice-open` on the element
   itself: it is clamped there for the visitor's preference, which a stylesheet cannot do. */
.slices:has(.slice--aside) {
  min-height: 32rem;
}

/* No picture to come off, so the panel is lit from its own corner rather than from where a
   picture's edge would have been. */
.slices:not(:has(.slice__banner)) .slice--aside {
  --lit-from: 0px;
}

/*
 * Nobody on the board has a portrait, so there is nothing for the height to be for.
 *
 * The figure above is a face's, and a band held to it with no faces in it is a row of names
 * in a field of ground. What is there is the names, so the names decide how tall it is.
 * Boardwide rather than per slice, because slices in a row are all the height of the row.
 */
.slices:has(.slice--aside):not(:has(.slice__banner)) {
  min-height: 0;
}

/*
 * Shut, the face is the whole slice; open, it holds the left at a column of its own.
 *
 * A real width either way, so what is beside the picture starts where the picture ends. Given
 * its own intrinsic width it came out half again as wide as the column the words were placed
 * off, and everything to its right, the panel included, landed across the face.
 */
.slice--aside .slice__banner {
  inset: 0 auto 0 0;
  width: 100%;
  height: 100%;
  max-width: none;
  object-fit: cover;
  /* Just below the top rather than flush at it, so a face keeps headroom above the hairline
     wherever the box is wider in proportion than the photograph. */
  object-position: center 12%;
  /*
   * One zoom level, whether the slice is open or shut.
   *
   * A game's banner sits at 1.06 shut and comes back to 1 open, which is a slow push on a
   * landscape. On a row of faces it read as the wrong thing entirely: moving the pointer
   * along, the face being left zoomed in while the face being reached zoomed out, so what
   * should be one shift across the band was two zooms in opposite directions. A face is also
   * scaled about its own centre, so the framing drifted vertically while it happened.
   */
  scale: 1;
  transition: width var(--slice-open) cubic-bezier(0.22, 1, 0.36, 1);
}

/*
 * Open, the picture dissolves at its own right edge into the panel behind it.
 *
 * Only open: shut, the picture is the whole slice and a fade at its edge is a seam between
 * one face and the next rather than a join between a face and what it is read beside.
 */
.slice--aside.slice--open .slice__banner {
  width: var(--face);
  mask-image: linear-gradient(to right, #000 0, #000 calc(100% - var(--photo-dissolve)), transparent 100%);
  -webkit-mask-image: linear-gradient(to right, #000 0, #000 calc(100% - var(--photo-dissolve)), transparent 100%);
}

.slice--aside .slice__body {
  justify-content: flex-end;
  margin-left: 0;
  transition: margin-left var(--slice-open) cubic-bezier(0.22, 1, 0.36, 1);
}

/*
 * Open, the name stays where it was and only the blurb arrives.
 *
 * The face keeps the left of the slice and keeps its name, its nickname and what they were on
 * it: that is one thing and it does not need rearranging to make room. What the slice grows
 * is somewhere for the blurb to be read, beside the picture on the panel's own ground.
 *
 * A box of its own width rather than one that ends where the slice does. Pinned to the slice's
 * right edge, the blurb was re-wrapped on every frame of the growth, three lines becoming two
 * becoming one as the box widened, which is a text layout a frame across the whole band and
 * exactly what read as lag. Its left is the picture's edge and its width is the room the words
 * were given, both of which stand still, so the box the prose is laid out in never changes.
 */
.slice--aside.slice--open .slice__reveal {
  position: absolute;
  inset: 0 auto 0 calc(var(--face) - 2rem);
  width: calc(var(--blurb) + 2rem);
  justify-content: center;
  max-width: none;
  max-height: none;
  padding: 1.5rem 2rem 1.5rem 3.5rem;
  /* It starts arriving while the slice is still widening, a little over half way through, and
     is there as the slice settles. It can, now that the box it is laid out in stands still:
     what made waiting worth it was prose being re-wrapped on every frame, not prose moving. */
  transition: opacity 340ms ease calc(var(--slice-open) * 0.3);
}

/* Going, it goes at once. There is nothing to wait for on the way out. */
.slice--aside .slice__reveal {
  transition: opacity 200ms ease;
}

/* A name is prose here, not a label: it wraps rather than running past the slice. */
.slice--aside .slice__name {
  overflow-wrap: anywhere;
}

/*
 * Open, the name stays on the face: it wraps at the picture's edge rather than running past
 * it.
 *
 * What is under it is the picture and the lift at its foot, both of which stop where the
 * picture does. A name that carried on into the panel carried on past its own ground, and in
 * the light half that is near-white ink on a pale panel.
 */
.slice--aside.slice--open .slice__heading {
  max-width: calc(var(--face) - var(--cut) - 1.25rem);
}

/*
 * The ground the overlaid name is read against, which leaves when the name does.
 *
 * Kept to the foot of the slice: the faces are in the middle and the top, and a scrim tall
 * enough to reach them is a filter over the photograph rather than a ground under a name.
 *
 * `--photo-scrim`, since it is drawn on a photograph rather than on the page: near-black in
 * the dark half, a few steps lighter in the light one.
 */
.slice--aside .slice__body::before {
  position: absolute;
  inset: auto 0 0 0;
  height: 38%;
  content: "";
  background: linear-gradient(
    to top,
    color-mix(in oklab, var(--photo-scrim) 90%, transparent) 0%,
    color-mix(in oklab, var(--photo-scrim) 46%, transparent) 46%,
    transparent 100%
  );
  transition: opacity calc(var(--slice-open) * 0.6) ease;
  pointer-events: none;
}

.slice--aside.slice--open .slice__body::before {
  opacity: 0;
}

/* Nobody's face to carry, so nothing to carry it against: a scrim with no photograph under it
   is a dark fade up the foot of the panel and nothing else. Said as a class rather than asked
   with `:has`, because whether a slice has art is something the band already knows. */
.slice--aside.slice--bare .slice__body::before {
  display: none;
}

/*
 * Lower than a game's: it lifts the foot of the slice, and a face is not something to fade.
 *
 * `--photo-scrim` rather than the ground, because this is drawn on a photograph: the ground
 * flips with the theme, and a pale lift over a dark portrait is a haze.
 */
.slice--aside .slice__glow {
  inset: auto 0 -10% 0;
  height: 44%;
  background: radial-gradient(
    72% 124% at 16% 100%,
    color-mix(in oklab, var(--photo-scrim) 86%, transparent) 0%,
    color-mix(in oklab, var(--photo-scrim) 54%, transparent) 34%,
    color-mix(in oklab, var(--photo-scrim) 22%, transparent) 58%,
    transparent 82%
  );
  /* No blur of its own: a gradient this soft does not need a second pass over it, and six of
     them on a band is six offscreen renders a frame while the row is moving. */
  filter: none;
}

/* Open, the lift stops at the picture's edge: the panel beside it has a ground of its own,
   and a near-black haze over the foot of it is not that ground. */
.slice--aside.slice--open .slice__glow {
  right: auto;
  width: var(--face);
}

/*
 * The name, the nickname and the role are read on the photograph, so they take the ink a
 * photograph needs whichever theme the reader is on: near-white, over the scrim below.
 *
 * Only where there is a photograph. A slice with no portrait has no dark ground under its
 * name, so near-white ink there is near-white ink on the page: it takes the theme's own, like
 * the description beside it. The dark treatment is here to deal with photography, not to
 * darken the page.
 */
.slice--aside:not(.slice--bare) .slice__heading {
  --color-chalk: #f2f4f6;
  --color-ash: #a0a6ac;

  color: var(--color-chalk);
}


/* Above the panel and the scrim: the words are what they exist to make readable. */
.slice--aside .slice__heading,
.slice--aside .slice__reveal {
  position: relative;
  z-index: 1;
}

/*
 * How deep the stacked face's dissolve has gone, registered so that it can be interpolated.
 *
 * A plain custom property is a piece of text until something reads it, so substituted into a
 * `mask-image` it tells the browser nothing: the gradient is an image, one image becomes another
 * image discretely, and a dissolve declared that way lands at its full depth in the frame the
 * slice opened in while everything around it is still easing. Registering the property with a
 * syntax says what kind of value it holds, and a value of a known kind is a value that can be
 * transitioned — so the transition is declared on the depth, and the mask is drawn from the
 * depth again on every frame of it.
 *
 * The slice's own, distinct from the island-wide `--photo-dissolve`. That token is the resting
 * depth, and the band of one big picture a caller draws above this one reads it for a mask that
 * never moves; registering the token itself would set that picture animating for no reason. The
 * token says how deep the dissolve goes, this says how far along the way there it is.
 *
 * The first `@property` in this frontend. Where it is not understood the block is dropped and
 * the depth is untyped text: the mask still reaches the resting depth, and still only when the
 * slice is open, it simply gets there in one frame rather than easing to it.
 */
@property --slice-dissolve {
  syntax: "<length-percentage>";
  /* Inherited, because two things fade on this one line: the photograph, and the ground under
     the name that sits on its foot. They are siblings, so the eased depth is declared on the
     slice above both and read by each. */
  inherits: true;
  initial-value: 0%;
}

/*
 * Stacked, a slice reads top to bottom: the face over the words rather than beside them.
 *
 * Side by side, a face beside its prose is right, because there is a row to hold them both.
 * Stacked there is not. A face holding a third of a 390px screen left the words a measure a few
 * words wide, so every sentence broke into a ladder of fragments; and a dissolve at the
 * picture's right edge drew the join across the reading direction, which put the palest part of
 * the photograph exactly where the first character of every line begins.
 *
 * The band of one big picture a caller draws above this one had already settled it: its
 * photograph fades to the right on a row and downwards on a phone, so the picture goes to ground
 * and the words start on the ground it left. A band of faces now makes the same decision,
 * because it is the same decision, and a page no longer contradicts itself one band down.
 */
@media (max-width: 767px) {
  /*
   * The slice measures itself, so the picture's height is a proportion of the slice.
   *
   * A viewport unit would be the wrong question. What the height is of is the band's own width,
   * and the page's horizontal padding is the page's business rather than the band's: `100vw`
   * would be over by that padding, by a different amount at every width the page is read at.
   * A container query on the slice asks the one box that decides it. Nothing else in the
   * frontend needs one yet, which is why this is the first.
   *
   * The figures are declared here and read in the rules below rather than used here: a
   * container unit in a declaration on the container itself resolves against whatever contains
   * *that*, and it is the substitution into a descendant that asks the slice.
   */
  .slice--aside {
    container-type: inline-size;

    /*
     * The whole picture, at the full width of the slice.
     *
     * The height is the slice's own width times the picture's own aspect, which the script hands
     * down per slice as `--face-aspect`. So the box and the photograph are the same shape and the
     * `object-fit: cover` above crops nothing: what a reader gets is the picture, entire.
     *
     * A crop of this layout's choosing was the wrong idea however it was tuned. Every portrait
     * the association has recorded is taller than it is wide — between 1.36 and 1.55 — so a
     * landscape figure discarded most of half of each one.
     */
    --face-band: calc(100cqw * var(--face-aspect, 1.5));
    /* No picture edge for the light to come off: the face spans the slice, so the wash is lit
       from the panel's own corner, the way a slice with no picture at all is. */
    --lit-from: 0px;
  }

  /*
   * Height is whatever the face and the prose come to.
   *
   * Every figure that used to hold a stacked slice open was standing in for words that were
   * absolutely positioned and could not be measured. In flow the content says how tall it is:
   * one line of prose takes one line, five take five and none of them is clipped, and a band
   * with no pictures in it at all is as tall as the names need.
   */
  .slice--aside,
  .slice--aside.slice--open {
    flex: 0 0 auto;
    min-height: 0;
  }

  /* And the band is the sum of its slices rather than the height a row of faces wanted. */
  .slices:has(.slice--aside) {
    min-height: 0;
  }

  /*
   * Two rows: the face's band, and the words under it.
   *
   * The name stays on the photograph, at the foot of the first row; the words are in normal
   * flow in the second, at the full width of the slice. A first row of exactly the picture's
   * height is what keeps the face the same size shut and open — what opening a slice fills is
   * the second row, and the first does not move.
   *
   * The padding goes on the two children rather than on the body, because the second row has to
   * collapse to nothing while the slice is shut and a padded box never does.
   *
   * And the second row is what opening the slice grows: from `0fr`, which is no room at all, to
   * `1fr`, which in a grid as tall as its own contents is the room the prose actually asked for.
   * So five lines of prose get five and one line gets one, and neither is told a figure by
   * this stylesheet.
   *
   * Deliberately not the `max-height` the `cover` layout reveals a line-up with — worth saying
   * out loud, because the file now has both idioms and a reader will want to know which to reach
   * for. A line-up is a bounded, known shape and a ceiling over it is honest. What is revealed
   * here is prose of no known length, this band is the only place it is read, and a ceiling
   * there cuts somebody's own words off with nowhere left to finish reading them.
   */
  .slice--aside .slice__body {
    /* No room for the words, until there is. Read into the track list below rather than being
       the track list, so the row the face stands in is stated once. */
    --words: 0fr;

    display: grid;
    grid-template-rows: var(--face-band) var(--words);
    /* The one column takes the whole slice. Said out loud because the flex layout this
       replaces packed its content to the foot, and a grid inherits that as tracks packed to
       one end and sized to their contents, which left the prose a measure again. */
    justify-content: stretch;
    height: auto;
    gap: 0;
    padding: 0;
    transition: grid-template-rows var(--slice-ease) cubic-bezier(0.22, 1, 0.36, 1);
  }

  .slice--aside.slice--open .slice__body {
    --words: 1fr;
  }

  /* No photograph, so no band to reserve for one: the name is the whole slice. */
  .slice--aside.slice--bare .slice__body {
    grid-template-rows: auto var(--words);
  }

  /*
   * At the very bottom of the picture.
   *
   * Not lifted clear of the line where the picture starts to go, the way it was: the picture is
   * the thing being looked at, and a name floating a fifth of the way up it reads as neither on
   * the photograph nor under it. What keeps it legible where the picture is fading is the scrim
   * below, which is darkest exactly here and dissolves on the same line the photograph does.
   */
  .slice--aside .slice__heading {
    align-self: end;
    /* Tight underneath: the role and the prose are one block about one person, and the picture's
       own foot is already a boundary between them. The gap was this padding and the reveal's
       own stacked on each other, which read as two separate things. */
    padding: 1.25rem 1.25rem 0.55rem;
  }

  /*
   * Smaller than a row of faces gives it, but not small.
   *
   * Across a row a name has a column to itself and room to be the band's own display face;
   * stacked it has the whole width, and at that size two or three words of somebody's name
   * stood as tall as the picture they belong to. This is the size that reads as a heading on a
   * phone without becoming one.
   */
  .slice--aside .slice__name {
    font-size: 1.2rem;
    line-height: 1.2;
  }

  /* And the role under it, which at the shared figure was down at 11px on a phone. */
  .slice--aside .slice__count {
    font-size: 0.8rem;
  }

  /* Nothing under it to sit clear of. */
  .slice--aside.slice--bare .slice__heading {
    padding-bottom: 1.25rem;
  }

  /* Nothing to the right of it to wrap clear of either: the picture is the width of the slice
     open or shut, so the name has the whole of it. */
  .slice--aside.slice--open .slice__heading {
    max-width: none;
  }

  /*
   * The words read under the face, in flow, at the full width of the slice.
   *
   * A longer line than typography would choose on a 390px screen, and deliberately so: the
   * alternative, an inset measure inside a full-width slice, puts back the gutter the restack
   * exists to remove. Uncapped, because this band is the only place these words are read and a
   * cap here would clip somebody's own prose with nowhere else to go.
   */
  .slice--aside.slice--open .slice__reveal {
    position: relative;
    inset: auto;
    width: auto;
    justify-content: flex-start;
    max-width: none;
    padding: 0.5rem 1.25rem 1.25rem;
    /*
     * No delay to wait out. Side by side the words hold back while the slice widens, because
     * prose re-wrapped on every frame of that growth is what read as lag; here the box they
     * are laid out in was never moving.
     *
     * A share of `--slice-ease` rather than a figure of its own, so the visitor's preference
     * reaches it. This selector outranks the blanket below that switches a reveal's transition
     * off, and it would go on outranking it however many exceptions were written underneath —
     * so the ceiling is applied to the figure where the figure is worked out, and a rule that
     * beats the blanket is no longer a rule that escapes the policy. Roughly a quarter of the
     * opening, which is what a fifth of a second was of it: the words are legible well before
     * the room they are in has finished growing.
     */
    transition: opacity calc(var(--slice-ease) * 0.23) ease;
  }

  .slice--aside .slice__reveal {
    /* The row it sits in is the one thing deciding its height, in both states. Two mechanisms
       clipping the same box is one of them fighting the other: the ceiling a slice in a row
       keeps its reveal under would stop the growth short of the prose it grew for, and a
       ceiling of nothing while shut would hold the row open at nothing of its own. What
       collapses the box is the `0fr` row above, and the clip that lets it is `overflow` on this
       one, which the shared rule already sets. */
    min-height: 0;
    max-height: none;
  }

  /*
   * The picture takes the full width of the slice at the crop above, and keeps that height
   * whether the slice is shut or open. What opening a slice brings is the words under the
   * picture; it does not resize the face.
   */
  .slice--aside .slice__banner,
  .slice--aside.slice--open .slice__banner {
    inset: 0 0 auto 0;
    width: 100%;
    height: var(--face-band);
    mask-image: none;
    -webkit-mask-image: none;
  }

  /*
   * Open, the foot of the picture dissolves downwards into the words.
   *
   * The same move the one big picture in the band above makes on a narrow screen, and the same
   * reason: the photograph goes to ground, and the prose begins on the ground it left.
   *
   * Only open. A shut slice has no words for the picture to be joined to, and six faces each
   * melting into the next would read as a stack of unfinished photographs and would fight the
   * diagonal cut that is the island's own way of dividing one slice from the next.
   */
  .slice--aside.slice--open {
    --slice-dissolve: var(--photo-dissolve);
  }

  .slice--aside.slice--open .slice__banner {
    mask-image: linear-gradient(to bottom, #000 0, #000 calc(100% - var(--slice-dissolve)), transparent 100%);
    -webkit-mask-image: linear-gradient(to bottom, #000 0, #000 calc(100% - var(--slice-dissolve)), transparent 100%);
  }

  /*
   * And it goes soft over the slice's own opening rather than in the one frame it opened in.
   *
   * The depth rests at nothing while the slice is shut, which is a mask that hides none of the
   * photograph, and eases to `--photo-dissolve` as the words grow in underneath. The mask itself
   * still switches on at once — a shut slice has none, which is the rule above and the reason a
   * band of faces does not read as a stack of unfinished photographs — but at nothing deep it is
   * the whole picture, so what a reader sees is the foot of the face going to ground in step with
   * the room opening below it.
   *
   * On the way out it goes at once, like everything else here: there is nothing to explain about
   * a slice that has stopped being read.
   */
  .slice--aside {
    --slice-dissolve: 0%;

    transition: --slice-dissolve var(--slice-ease) cubic-bezier(0.22, 1, 0.36, 1);
  }

  /*
   * The name's ground is the picture's own band, not the foot of the slice.
   *
   * Stacked, the foot of the slice is below the words, so a scrim left there would draw a dark
   * band under the prose — in the light half, a dark smear across the page. The scrim belongs to
   * the picture, so the picture's box is the box it is drawn in.
   *
   * And it dies on the line the photograph dies on: both fade out over the last
   * `--photo-dissolve` of the same box, so there is one boundary between the picture and the
   * words rather than two. A caller hands this layout whatever picture it has and some of them
   * are bright — the near-white ink the heading takes over a photograph is only safe because
   * this is under it, which is why the name sits above the line where the two begin to go.
   */
  .slice--aside .slice__body::before {
    inset: 0 0 auto 0;
    height: var(--face-band);
    /* Darkest at the very bottom, because that is where the name is now. It used to die before
       the picture's own last stretch so the two ended together; the name has moved down into
       that stretch, so instead the scrim goes the whole way and is masked with the picture. */
    background: linear-gradient(
      to bottom,
      transparent 52%,
      color-mix(in oklab, var(--photo-scrim) 40%, transparent) 74%,
      color-mix(in oklab, var(--photo-scrim) 92%, transparent) 100%
    );
  }

  /*
   * Open, the scrim dissolves on the picture's own line.
   *
   * The same mask, over the same box, so the name's ground and the photograph under it go to
   * page together. Without this the scrim would be a dark band left standing where the picture
   * had already gone — in the light half, a smear across the page.
   */
  .slice--aside.slice--open .slice__body::before {
    mask-image: linear-gradient(to bottom, #000 0, #000 calc(100% - var(--slice-dissolve)), transparent 100%);
    -webkit-mask-image: linear-gradient(to bottom, #000 0, #000 calc(100% - var(--slice-dissolve)), transparent 100%);
  }

  /* The name is still on the face when the slice is open, so its ground stays with it. Side by
     side the name moves off the picture as the slice grows and the scrim is right to leave;
     stacked, nothing moves. */
  .slice--aside.slice--open .slice__body::before {
    opacity: 1;
  }

  /*
   * The lift is anchored to the picture's band on the same reasoning, and masked over its own
   * last stretch so it dies rather than stopping. At the foot of the slice it would be a
   * near-black haze laid under the words.
   *
   * The 88% is by eye and deliberately not `--photo-dissolve`. The two fades are over different
   * boxes — the picture's whole band, and the lift's own bottom stretch of it — so tying them
   * together would only look like a shared figure. What was checked is the rendered stack: the
   * ramp through the dissolve is smooth and there is no dark line where the lift stops. If the
   * dissolve's depth changes, look at this again rather than deriving it.
   */
  .slice--aside .slice__glow {
    inset: calc(var(--face-band) * 0.56) 0 auto 0;
    height: calc(var(--face-band) * 0.44);
    mask-image: linear-gradient(to bottom, #000 0, #000 88%, transparent 100%);
    -webkit-mask-image: linear-gradient(to bottom, #000 0, #000 88%, transparent 100%);
  }

  /* Nothing beside the picture for the lift to stop at: it spans the slice open or shut. */
  .slice--aside.slice--open .slice__glow {
    right: 0;
    width: auto;
  }
}

@media (prefers-reduced-motion: reduce) {
  .slice--aside .slice__body,
  .slice--aside .slice__body::before {
    transition: none;
  }
}

@media (prefers-reduced-motion: reduce) {
  .slice,
  .slice__banner,
  .slice__icon,
  .slice__tick,
  .slice__reveal {
    transition: none;
  }
}

/*
 * Except the two movements a stacked slice is made of, which are shortened rather than removed.
 *
 * The blankets above are right about everything they cover: a picture zooming, a row of slices
 * resizing and a tick drawing itself are decoration, and decoration is what the preference asks
 * about. These two are not. On a phone nothing widens and nothing crosses over, so the dissolve
 * arriving and the words growing in are the whole of what says the slice opened — take them away
 * and the description is simply there, next to a face, with nothing having happened.
 *
 * So they keep themselves, at the ceiling the island allows: `--slice-ease` is already clamped
 * where it is worked out, so this rule has nothing to shorten and exists only to say that the
 * blanket does not reach here.
 *
 * The fade the words arrive on needs no exception of its own. Its selector already outranks the
 * blanket, which is why its figure is a share of `--slice-ease` too: a movement that cannot be
 * switched off by a blanket has to carry the ceiling itself.
 */
@media (prefers-reduced-motion: reduce) and (max-width: 767px) {
  .slice--aside .slice__body {
    transition: grid-template-rows var(--slice-ease) cubic-bezier(0.22, 1, 0.36, 1);
  }

  .slice--aside .slice__banner {
    transition: --slice-dissolve var(--slice-ease) cubic-bezier(0.22, 1, 0.36, 1);
  }
}
</style>
