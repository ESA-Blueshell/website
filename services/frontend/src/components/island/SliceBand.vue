<script lang="ts" setup>
import {computed, onBeforeUnmount, onMounted, ref, watch} from "vue"
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
   * What the plus is called, since one page adds a game and the other adds a team.
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
   * The band settles this for itself — a hover, a tap, a scroll — and until now nobody
   * outside needed to know. A season change rebuilds the band, and the page is the only thing
   * that outlives it: it holds what was open and hands it back through [openId], so switching
   * season does not also change which game is being read.
   */
  (event: "open", id: SliceItem["id"] | null): void
}>()

const motion = useMotionAllowed()

/** Whether a slice has anything behind it. Absent from an item means it has. */
const opens = (index: number): boolean => props.items[index]?.expandable !== false

/** The first slice that opens onto anything, or nothing where none of them do. */
const firstThatOpens = (): number | null => {
  const found = props.items.findIndex((_, index) => opens(index))
  return found >= 0 ? found : null
}

/**
 * Which slice is open. Nothing is open for the first frame so the opening of the first one is
 * something the visitor sees happen rather than something already done — except under reduced
 * motion, where it is simply open from the start.
 */
const open = ref<number | null>(null)
const slices = ref<HTMLElement[]>([])

/**
 * A slice the visitor opened themselves. Stacked, the scroll decides which slice is open, and
 * without this a tap was undone by the next observation: the slice opened and shut again
 * before a finger left the screen. The choice stands until the page is scrolled, at which
 * point the scroll is the visitor's intent again.
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
    const index = slices.value.indexOf(best.target as HTMLElement)
    if (index >= 0) open.value = index
  }, {rootMargin: "-42% 0px -42% 0px", threshold: [0, 0.25, 0.5, 1]})
  slices.value.forEach(el => el && watcher?.observe(el))
}

const releaseTap = () => {
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
 * How wide a slice's box is, worked out rather than measured.
 *
 * Worked out from the two things that decide it — how many slices share the row and how wide
 * the window is — because measuring means waiting for layout, and because a measurement taken
 * as a slice opens reads the box it had shut: its share of the row is transitioned, over
 * `--slice-open`.
 * The arithmetic knows where it is going the moment the pointer arrives.
 *
 * Stacked, a slice is the width of the window and nothing else comes into it.
 */
const boxWidth = (index: number): number => {
  const width = viewport.value
  if (width === 0) return 0
  if (stacked()) return width

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
 * A banner covers its slice, so a box taller than it is wide is filled by its height and the
 * picture is painted well past both edges: a slice sharing a row with seven others is a couple
 * of hundred pixels across and a good three hundred tall, and a sixteen-by-nine banner covering
 * it is drawn some six hundred wide. Asked for its own share alone it fetched the bottom of the
 * ladder for that, and read as mush.
 *
 * Height is measured where width is worked out, because it is the one figure the arithmetic
 * cannot reach: the row is as tall as whatever is in it, and it does not move as a slice opens.
 * A height of nothing — the first frame, or a page with no layout — leaves the share of the row
 * standing on its own.
 *
 * Both states, not just the slice being read. A collapsed banner used to hide under a
 * grayscale-and-dim filter, which was the case for asking it for its share and no more; the
 * art is shown as uploaded now, so a copy stretched four times over is simply soft on the
 * page. This is the raised share that comment asked for, taken from the height rather than
 * guessed at.
 */
const wanted = (index: number): number => coveredWidth({
  boxWidth: boxWidth(index),
  boxHeight: slices.value[index]?.clientHeight ?? 0,
  imageWidth: props.items[index]?.width,
  imageHeight: props.items[index]?.height,
})

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
 * thing this is meant to avoid — the small copy is there to be quick, and racing it with the
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
 * Understated until a copy has arrived, and by a lot: 200 css pixels side by side and half the
 * window stacked fetches the bottom of the ladder, and a collapsed slice is dimmed almost to a
 * silhouette anyway. The worked-out figure replaces it once there is a picture on the screen to
 * replace, and grows again as a slice opens.
 */
const sizesOf = (index: number): string => {
  const asked = arrived.value.has(index) ? askedFor.value[index] : 0
  return asked ? `${asked}px` : "(min-width: 768px) 200px, 50vw"
}


/**
 * Opening a slice and going to it are the same gesture, one after the other: a slice that is
 * already showing what it holds has said what it has to say, so the next click follows it.
 * Stacked, that is the second tap; side by side, the pointer has already opened it.
 */
/** A pointer or a focus arriving on a slice, which opens it only where it opens onto anything. */
const reach = (index: number) => {
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
}

const indexOfNamed = () => props.items.findIndex(item => item.id === props.openId)



const settle = () => {
  const named = indexOfNamed()
  // The first slice that opens onto anything, so a band where nothing does settles on nothing
  // and no slice grows. Half the association's board members wrote no blurb.
  open.value = named >= 0 ? named : firstThatOpens()
  // Stacked, the scroll decides what is open, so a named slice has to be held against it the
  // same way a tap is. This runs on mount too, which is where a band rebuilt around a slice
  // that was just added arrives with the name already set and no change left to react to.
  if (named >= 0 && stacked()) tapped.value = named
}

onMounted(() => {
  if (!motion.decorative.value) {
    settle()
  } else {
    requestAnimationFrame(() => requestAnimationFrame(settle))
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
// — unless one of them is named, which is the set arriving because that one was just added.
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
  const named = indexOfNamed()
  const held = before?.[open.value ?? -1]?.id
  const stillThere = held == null ? -1 : items.findIndex(item => item.id === held)
  const target = named >= 0 ? named : (stillThere >= 0 ? stillThere : 0)
  tapped.value = stacked() && named >= 0 ? named : null
  // Only a band that has nothing in common with the one before it opens from nothing.
  open.value = motion.decorative.value && stillThere < 0 && named < 0 ? null : target
  if (open.value === null) requestAnimationFrame(() => requestAnimationFrame(settle))
  requestAnimationFrame(watchScroll)
})

/**
 * Opens the named slice, which is the one just added.
 *
 * After the update rather than during it, so the set it belongs to is the one on screen.
 * Stacked, this is held the same way a tap is held — until the visitor scrolls, at which
 * point the scroll is their intent again. Scrolling to it ourselves would be that scroll,
 * and would hand the choice straight back to whichever slice happened to be in the middle.
 */
watch([() => props.openId, () => props.items], () => {
  const named = indexOfNamed()
  if (named < 0) return
  open.value = named
  if (stacked()) tapped.value = named
}, {flush: "post"})

// Said by id rather than by position: the page holding it hands it to a different band, where
// the same game or team rarely sits in the same place.
watch(open, (index) => {
  emit("open", index == null ? null : props.items[index]?.id ?? null)
  // The slice being read wants the wider copy, and knows how wide without waiting for the
  // row to finish moving.
  grow()
})
</script>

<template>
  <div
    class="team-slices"
    :data-testid="`${testidPrefix}-slices`"
    :style="{'--accent': accent, ...(shutShare ? {'--share': `${shutShare}px`} : {})}"
  >
    <section
      v-for="(item, index) in items"
      :key="item.id"
      :ref="el => { if (el) slices[index] = el as HTMLElement }"
      class="team-slice"
      :class="{
        'team-slice--open': index === open,
        'team-slice--aside': layout === 'aside',
        'team-slice--first': index === 0,
        'team-slice--last': index === items.length - 1 && !mayAdd,
      }"
      :data-testid="`${testidPrefix}-${item.id}`"
      :style="item.accent ? {'--accent': item.accent} : undefined"
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
        class="team-slice__edit"
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
        class="team-slice__banner"
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
        class="team-slice__glow"
      />

      <button
        class="team-slice__body"
        :aria-expanded="index === open"
        type="button"
        @click="choose(index)"
      >
        <span class="team-slice__heading">
          <span
            aria-hidden="true"
            class="team-slice__tick"
          />
          <span class="team-slice__titles">
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
              class="team-slice__icon"
              sizes="40px"
              :src="item.icon"
              :srcset="item.iconSrcset"
            >
            <span class="team-slice__name">{{ item.title }}</span>
          </span>
          <span class="team-slice__count">{{ item.meta }}</span>
        </span>

        <span class="team-slice__roster">
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
      class="team-slice team-slice--empty team-slice--first"
      :data-testid="`${testidPrefix}-empty-slice`"
    >
      <span class="team-slice__body team-slice__nothing">
        <span class="team-slice__heading">
          <span class="team-slice__name">{{ emptyLabel }}</span>
          <!-- Whatever else is worth saying where there is nothing: on a game's page, the
               last season it did play, which is a way on rather than a dead end. -->
          <slot name="empty" />
        </span>
      </span>
    </section>

    <section
      v-if="mayAdd"
      class="team-slice team-slice--add team-slice--last"
    >
      <button
        class="team-slice__body team-slice__add"
        :data-testid="`${testidPrefix}-add`"
        type="button"
        @click="emit('add')"
      >
        <span
          aria-hidden="true"
          class="team-slice__plus island-plus"
        >
          <svg
            class="island-plus__edge"
            fill="none"
            viewBox="0 0 100 100"
          >
            <path d="M38 2 H62 V38 H98 V62 H62 V98 H38 V62 H2 V38 H38 Z" />
          </svg>
        </span>
        <span class="team-slice__heading">
          <span
            aria-hidden="true"
            class="team-slice__tick"
          />
          <span class="team-slice__name">{{ addLabel }}</span>
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
.team-slices {
  --cut: 30px;
  /* How long a slice takes to grow, and everything that has to move with it. */
  --slice-open: 620ms;

  display: flex;
  width: 100%;
  min-height: 22rem;
}

.team-slice {
  position: relative;
  flex: 1 1 0;
  min-width: 0;
  overflow: hidden;
  background-color: var(--color-surface);
  clip-path: polygon(var(--cut) 0, 100% 0, calc(100% - var(--cut)) 100%, 0 100%);
  margin-left: calc(var(--cut) * -1);
  transition: flex-grow var(--slice-open) cubic-bezier(0.22, 1, 0.36, 1);
}

/* The cut edge, drawn. Two slices of the same tone meet on an invisible diagonal in light, so
   the boundary is a sliver clipped to the same geometry — no angle to keep in step with the
   slice's height. Not on the first: there is nothing to its left to divide it from. */
.team-slice:not(.team-slice--first)::after {
  content: "";
  position: absolute;
  inset: 0;
  background: var(--color-hairline);
  clip-path: polygon(var(--cut) 0, calc(var(--cut) + 1.5px) 0, 1.5px 100%, 0 100%);
  pointer-events: none;
}

.team-slice--first {
  clip-path: polygon(0 0, 100% 0, calc(100% - var(--cut)) 100%, 0 100%);
  margin-left: 0;
}

.team-slice--last {
  clip-path: polygon(var(--cut) 0, 100% 0, 100% 100%, 0 100%);
}

/* Hidden rather than transparent, for the same reason as the strip's: a see-through
   affordance still answers a click. */
.team-slice__edit {
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

.team-slice__edit svg {
  width: 23px;
  height: 23px;
}

/*
 * Focus on the slice rather than on the affordance itself: hidden means unfocusable, so an
 * affordance that waited to be focused could never be reached. Focus lands on the slice
 * first, which reveals the affordances sitting on it, and the next tab reaches them.
 */
.team-slice:hover .team-slice__edit,
.team-slice:focus-within .team-slice__edit {
  visibility: visible;
}

/*
 * Except where the focus the slice holds is a pointer's. A click focuses the body it lands
 * on, and `:focus-within` cannot tell that focus from a keyboard's — so a slice clicked open
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
 * it — so an exception about pointers would hide every affordance on the page the moment one
 * was used.
 */
@media (hover: hover) {
  .team-slice:not(:hover):has(.team-slice__body:focus:not(:focus-visible)) .team-slice__edit {
    visibility: hidden;
  }
}

@media (hover: none) {
  .team-slice__edit {
    visibility: visible;
  }
}

/*
 * A slice like the others rather than a strip on the end: it takes the same share of the
 * band, carries the same seam, and answers the pointer the same way. What is behind it is a
 * plus through the middle instead of a photograph.
 */
/* Narrower than a team: a way in rather than something to read. */
/* Wider than the plus beside it and quieter than a slice with a picture: it is a statement,
   not somewhere to go. */
.team-slice--empty {
  flex: 2 1 0;
  background-color: color-mix(in oklab, var(--color-chalk) 4%, transparent);
}

.team-slice__nothing {
  display: flex;
  align-items: flex-end;
  width: 100%;
  height: 100%;
  color: var(--color-ash);
}

/* The association's blue, patterned and darkened, which makes the way in the one saturated
   thing on the page in either theme. */
.team-slice--add {
  flex: 0 0 clamp(6.5rem, 11%, 10rem);

  --color-chalk: #ffffff;

  background-color: var(--color-brand);
  /* Taken down a fifth, which is where white on it clears AA, and the pattern still reads. */
  background-image:
    linear-gradient(var(--add-tint, transparent), var(--add-tint, transparent)),
    linear-gradient(oklch(0 0 0 / 22%), oklch(0 0 0 / 22%)),
    url("../../../assets/bg/shelly-bg-blue.jpg");
  background-size: auto, auto, 135px 77px;
  background-repeat: repeat;
}

.team-slice--add:hover {
  --add-tint: oklch(1 0 0 / 12%);
}

.team-slice__add {
  position: relative;
  color: var(--color-chalk);
}

/*
 * The middle of the slice rather than the middle of a stack: the plus is the mark on the
 * block, and the label below it sits where a team's name sits.
 */
/*
 * Skewed, not rotated, and to the angle of the seam this band is cut on — the same lean the
 * slices themselves have, so the mark belongs to the block rather than sitting on top of it.
 */
.team-slice__plus {
  position: absolute;
  top: 50%;
  left: 50%;
  /* Centred and skewed in the one property: the two split across `translate` and `transform`
     did not compose here, which left the mark hanging off the edge of its own block. */
  transform: translate(-50%, -50%) skewX(-5deg);
  width: min(56%, 92px);
}

.team-slice--add:hover .team-slice__plus {
  opacity: 0.95;
  background: color-mix(in oklab, var(--color-chalk) 20%, transparent);
}

.team-slice__plus svg {
  width: 100%;
  height: auto;
  aspect-ratio: 1;
}

.team-slice--first.team-slice--last {
  clip-path: none;
}

.team-slice--open {
  flex-grow: 3.4;
  z-index: 1;
}

.team-slice__banner {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  scale: 1.06;
  transition: scale 900ms cubic-bezier(0.22, 1, 0.36, 1);
}

.team-slice--open .team-slice__banner {
  scale: 1;
}

.team-slice__glow {
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

.team-slice__body {
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

.team-slice__heading {
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
}

.team-slice__tick {
  width: 2rem;
  height: 3px;
  margin-bottom: 0.6rem;
  background-color: var(--accent);
  scale: 0.35 1;
  transform-origin: left center;
  transition: scale 520ms cubic-bezier(0.22, 1, 0.36, 1);
}

.team-slice--open .team-slice__tick {
  scale: 1;
}

/* The logo and the name read as one line, so they sit on a row and grow together. */
.team-slice__titles {
  display: flex;
  gap: 0.5rem;
  align-items: center;
}

.team-slice__icon {
  flex: none;
  width: 2rem;
  height: 2rem;
  object-fit: contain;
  transition: width 520ms cubic-bezier(0.22, 1, 0.36, 1),
    height 520ms cubic-bezier(0.22, 1, 0.36, 1);
}

.team-slice--open .team-slice__icon {
  width: 2.5rem;
  height: 2.5rem;
}

.team-slice__name {
  font-family: var(--font-display);
  font-size: 1rem;
  line-height: 1.1;
  text-transform: uppercase;
  color: var(--color-chalk);
}

.team-slice--open .team-slice__name {
  font-size: 1.5rem;
}

.team-slice__count {
  font-size: 0.7rem;
  letter-spacing: 0.02em;
  color: var(--color-ash);
}

/*
 * The roster belongs to the open slice. A closed one keeps it in the document — it is one
 * button, and its label should say who is in the team — but gives it no room and no ink.
 */
.team-slice__roster {
  display: flex;
  flex-direction: column;
  gap: 0.65rem;
  overflow: hidden;
  max-height: 0;
  opacity: 0;
  transition: max-height 560ms cubic-bezier(0.22, 1, 0.36, 1), opacity 320ms ease;
}

.team-slice--open .team-slice__roster {
  max-height: 12rem;
  opacity: 1;
}

/* A roster clipped at its last line is worse than a taller slice. */
@media (max-width: 767px) {
  .team-slice--open .team-slice__roster {
    max-height: 22rem;
  }
}

/*
 * The rules below dress what a page renders into the details slot. That content is compiled
 * in the page's own scope, not this component's, so a plain scoped selector never matches it
 * — which left rosters running together as one line of text.
 */
:slotted(.team-slice__group) {
  display: block;
}

:slotted(.team-slice__group-label) {
  display: block;
  font-size: 0.6rem;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: var(--color-ash);
}

:slotted(.team-slice__members) {
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
:slotted(.team-slice__member) {
  display: flex;
  min-width: 0;
  flex-direction: column;
  line-height: 1.15;
}

:slotted(.team-slice__handle) {
  font-size: 0.95rem;
  color: var(--color-chalk);
}

:slotted(.team-slice__link) {
  align-self: flex-start;
  margin-top: 0.9rem;
  font-family: var(--font-display);
  font-size: 0.7rem;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--accent);
  transition: opacity 200ms ease;
}

:slotted(.team-slice__link):hover {
  opacity: 0.75;
}

:slotted(.team-slice__member-name) {
  font-size: 0.7rem;
  letter-spacing: 0.01em;
  color: color-mix(in oklab, var(--color-ash) 85%, transparent);
}

/* Beside the part they played rather than under it: it qualifies the role, it is not a
   second fact about the person. */
:slotted(.team-slice__member-role) {
  display: block;
  font-size: 0.7rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: color-mix(in oklab, var(--accent) 82%, var(--color-chalk));
}

/* A caption, so it is held to a couple of lines and the markdown inside it stays inline. */
:slotted(.team-slice__member-note) {
  display: block;
  margin-top: 0.15rem;
  max-width: 22rem;
  font-size: 0.72rem;
  line-height: 1.35;
  color: color-mix(in oklab, var(--color-ash) 92%, transparent);
}

:slotted(.team-slice__member-note p) {
  display: inline;
  margin: 0;
}

/* Stacked on a narrow screen, where a row of slices would leave each one a sliver. The cut
   turns with them so the seams still read as diagonal. */
@media (max-width: 767px) {
  .team-slices {
    --cut: 22px;

    flex-direction: column;
    min-height: 0;
  }

  .team-slice--add {
    flex: 0 0 auto;
    min-height: 7rem;
  }

  .team-slice__plus {
    width: 44px;
  }

  .team-slice {
    clip-path: polygon(0 var(--cut), 100% 0, 100% calc(100% - var(--cut)), 0 100%);
    margin-left: 0;
    margin-top: calc(var(--cut) * -1);
    min-height: 8.5rem;
  }

  .team-slice--first {
    clip-path: polygon(0 0, 100% 0, 100% calc(100% - var(--cut)), 0 100%);
    margin-top: 0;
  }

  .team-slice--last {
    clip-path: polygon(0 var(--cut), 100% 0, 100% 100%, 0 100%);
  }

  /* Stacked, an open slice needs room for its roster rather than a share of a row. */
  .team-slice--open {
    flex-grow: 1;
    min-height: 17rem;
  }

  .team-slice__body {
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
.team-slice--aside {
  /* The face's own width: its share of the row, which the band works out and hands down.
     The column stands in until the row has been laid out. */
  --face: var(--share, clamp(7.5rem, 13vw, 12rem));
  /* What the words get, which is a measure to read on rather than a share of the row. */
  --blurb: clamp(15rem, 20vw, 22rem);
  --portrait: clamp(7.5rem, 13vw, 12rem);

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
.team-slice--aside.team-slice--open {
  flex: 0 0 calc(var(--face) + var(--blurb));
}

/*
 * The surface the blurb is read on, under the picture rather than over it.
 *
 * Under, so nothing is ever laid across a face: what reveals it is the picture's own right
 * edge dissolving, and shut, the picture covers the slice and the panel is simply not seen.
 *
 * The banner's own recipe, on the banner's own tokens and with no ground of its own: the
 * board's colour as a light source at the picture's top right corner, thinning with distance
 * from it, and a carry under it giving the picture's dissolve a colour to fade into. Mixed
 * with `transparent` alone, so what is behind them is the island's own patterned ground.
 *
 * Drawn from that corner rather than clipped to it. Clipped, its own edge landed on the
 * picture's dissolve, where the picture is nearly transparent, and drew the line the dissolve
 * exists to avoid.
 */
.team-slice--aside::before {
  content: "";
  position: absolute;
  inset: 0;
  background-image:
    radial-gradient(
      120% 165% at var(--face) 0,
      color-mix(in oklab, var(--accent, var(--color-brand)) var(--board-wash-on), transparent) 0%,
      color-mix(in oklab, var(--accent, var(--color-brand)) var(--board-wash), transparent) 40%,
      transparent 78%
    ),
    linear-gradient(
      to right,
      transparent calc(var(--face) - 3rem),
      color-mix(in oklab, var(--accent, var(--color-brand)) var(--board-wash), transparent) var(--face),
      transparent 78%
    );
  pointer-events: none;
}

/* Taller than a game band, because a face needs the room a landscape does not. And slower:
   a face and a paragraph are read, where a game's line-up is glanced at. */
.team-slices:has(.team-slice--aside) {
  --slice-open: 950ms;

  min-height: 32rem;
}

/*
 * Shut, the face is the whole slice; open, it holds the left at a column of its own.
 *
 * A real width either way, so what is beside the picture starts where the picture ends. Given
 * its own intrinsic width it came out half again as wide as the column the words were placed
 * off, and everything to its right, the panel included, landed across the face.
 */
.team-slice--aside .team-slice__banner {
  inset: 0 auto 0 0;
  width: 100%;
  height: 100%;
  max-width: none;
  object-fit: cover;
  /* Just below the top rather than flush at it, so a face keeps headroom above the hairline
     wherever the box is wider in proportion than the photograph. */
  object-position: center 12%;
  transition:
    width var(--slice-open) cubic-bezier(0.22, 1, 0.36, 1),
    scale var(--slice-open) cubic-bezier(0.22, 1, 0.36, 1);
}

/*
 * Open, the picture dissolves at its own right edge into the panel behind it.
 *
 * Only open: shut, the picture is the whole slice and a fade at its edge is a seam between
 * one face and the next rather than a join between a face and what it is read beside.
 */
.team-slice--aside.team-slice--open .team-slice__banner {
  width: var(--face);
  mask-image: linear-gradient(to right, #000 0, #000 calc(100% - var(--photo-dissolve)), transparent 100%);
  -webkit-mask-image: linear-gradient(to right, #000 0, #000 calc(100% - var(--photo-dissolve)), transparent 100%);
}

.team-slice--aside .team-slice__body {
  justify-content: flex-end;
  margin-left: 0;
  transition: margin-left var(--slice-open) cubic-bezier(0.22, 1, 0.36, 1);
}

/*
 * Open, the words step off the face and stand beside it.
 *
 * `margin-left` rather than padding, so the offset is exactly the portrait's width and nothing
 * the diagonal cut does to the padding moves the words further out. Capped, because a blurb
 * given the whole of a slice that has grown to three and a half times its share is a line of
 * text with a field of ground after it.
 */
/*
 * Open, the name stays where it was and only the blurb arrives.
 *
 * The face keeps the left of the slice and keeps its name, its nickname and what they were on
 * it — that is one thing and it does not need rearranging to make room. What the slice grows
 * is somewhere for the blurb to be read, to the right of the picture, on a fade of the
 * island's own ground so prose is read on a flat surface rather than over a photograph.
 */
.team-slice--aside.team-slice--open .team-slice__roster {
  position: absolute;
  inset: 0 0 0 calc(var(--face) - 2rem);
  justify-content: center;
  max-width: none;
  max-height: none;
  padding: 1.5rem 2rem 1.5rem 3.5rem;
}

/* A name is prose here, not a label: it wraps rather than running past the slice. */
.team-slice--aside .team-slice__name {
  overflow-wrap: anywhere;
}

/*
 * The ground the overlaid name is read against, which leaves when the name does.
 *
 * Kept to the foot of the slice: the faces are in the middle and the top, and a scrim tall
 * enough to reach them is a filter over the photograph rather than a ground under a name.
 */
.team-slice--aside .team-slice__body::before {
  position: absolute;
  inset: auto 0 0 0;
  height: 38%;
  content: "";
  background: linear-gradient(
    to top,
    color-mix(in oklab, var(--color-pit) 90%, transparent) 0%,
    color-mix(in oklab, var(--color-pit) 46%, transparent) 46%,
    transparent 100%
  );
  transition: opacity calc(var(--slice-open) * 0.6) ease;
  pointer-events: none;
}

.team-slice--aside.team-slice--open .team-slice__body::before {
  opacity: 0;
}

/* Lower than a game's: it lifts the foot of the slice, and a face is not something to fade. */
.team-slice--aside .team-slice__glow {
  inset: auto 0 -10% 0;
  height: 44%;
}

/* Above the panel and the scrim: the words are what they exist to make readable. */
.team-slice--aside .team-slice__heading,
.team-slice--aside .team-slice__roster {
  position: relative;
  z-index: 1;
}

/*
 * Stacked, the same shape at a smaller size.
 *
 * The generic stacked slice is 8.5rem tall and knows nothing of the portrait column, which
 * leaves a blurb a gutter two words wide. A narrower face, real height and a measure to read
 * on, so the page reads the same at both breakpoints.
 */
@media (max-width: 767px) {
  .team-slice--aside {
    --portrait: clamp(5.5rem, 30vw, 8rem);

    min-height: 12rem;
  }

  .team-slice--aside.team-slice--open {
    min-height: 16rem;
  }

  .team-slice--aside.team-slice--open .team-slice__roster {
    inset: 0 0 0 calc(var(--portrait) - 1.5rem);
    max-height: none;
    padding: 1.25rem 1.25rem 1.25rem 2.5rem;
  }

  /*
   * The face keeps its column whether or not the slice is open, and dissolves into the words
   * beside it either way. Given the whole slice it is a face at four times the size, cropped
   * through the head: the box is wider than the photograph here, not narrower.
   */
  .team-slice--aside .team-slice__banner,
  .team-slice--aside.team-slice--open .team-slice__banner {
    width: var(--portrait);
    mask-image: linear-gradient(to right, #000 0, #000 calc(100% - var(--photo-dissolve)), transparent 100%);
    -webkit-mask-image: linear-gradient(to right, #000 0, #000 calc(100% - var(--photo-dissolve)), transparent 100%);
  }
}

@media (prefers-reduced-motion: reduce) {
  .team-slice--aside .team-slice__body,
  .team-slice--aside .team-slice__body::before {
    transition: none;
  }
}

@media (prefers-reduced-motion: reduce) {
  .team-slice,
  .team-slice__banner,
  .team-slice__icon,
  .team-slice__tick,
  .team-slice__roster {
    transition: none;
  }
}
</style>
