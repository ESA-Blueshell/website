<script lang="ts">
/** One poster on the strip: its own art, the line under it, and where it leads. */
export interface PosterItem {
  id: number | string
  /** Read out by a screen reader and shown where the art is missing; the art says it too. */
  title: string
  /**
   * The year, where it is not this one, and who it was for.
   *
   * A poster made this year says its own date; one from an older year needs the year said,
   * because "September" alone reads as this September.
   */
  meta?: string
  /** What the art cannot say: the description, cut short by the strip rather than the caller. */
  said?: string
  /** The event's own poster, where somebody made one. Without it the template is drawn. */
  banner?: string
  /** Written onto the template, where there is no poster: the day and time, and the place. */
  when?: string
  where?: string
  /** The widths that image is stored at, ready for a `srcset`, where it has several. */
  srcset?: string
  width?: number
  height?: number
  /** Where the poster leads. A path is followed by the router; without one the poster opens. */
  href?: string
  /** Whether it can be signed up for, and how full it is, drawn beside the way through. */
  state?: string
}
</script>

<script lang="ts" setup>
import {computed, onBeforeUnmount, onMounted, ref, watch} from "vue"
import {RouterLink} from "vue-router"
import PosterArt from "./PosterArt.vue"
import {fitAcross} from "./fitAcross"
import $markdownToHtml from "@/plugins/markdownToHtml"
import {useMotionAllowed} from "./useMotionAllowed"

/**
 * A row of event posters, travelled the way the timeline's strip is travelled.
 *
 * The art already carries the name, the time and the place, so the strip adds only what the art
 * cannot: the line under it and the way through. It scrolls rather than paging, and says when it
 * is near the end so a caller can fetch the next few before they are asked for.
 */
defineOptions({name: "PosterStrip"})

const {
  items,
  testidPrefix,
  narrowest = 340,
  fewest = 2,
  ahead = 2,
  panBackLabel = "Earlier events",
  panOnLabel = "Later events",
} = defineProps<{
  items: PosterItem[]
  testidPrefix: string
  /** The narrowest a poster may be, in pixels: as many as fit are shown, widened to fill the row. */
  narrowest?: number
  /** The fewest shown however narrow the row, since one poster across a phone hides the strip. */
  fewest?: number
  /** How many past those are drawn, which is also when `needs-more` is emitted. */
  ahead?: number
  panBackLabel?: string
  panOnLabel?: string
}>()

const emit = defineEmits<{"needs-more": []; open: [id: number | string]}>()

/** How many posters a chevron moves, which is what one press is worth. */
const JUMP = 2

const motion = useMotionAllowed()

const scroller = ref<HTMLElement | null>(null)

/** The poster under the pointer: the strip lights it and quietens the rest, as the strip does. */
const lit = ref<number | string | null>(null)

/* The window until the row has been laid out, which the strip spans on every page it is on. */
const width = ref(window.innerWidth)

const perView = computed(() => fitAcross(width.value, narrowest, fewest))

const canPanBack = ref(false)
const canPanOn = ref(false)

/**
 * One poster and the gap after it, read off the row because how many fit depends on the screen.
 *
 * Only asked of a row that scrolls, which holds more posters than fit and so at least two.
 */
const pitchOf = (box: HTMLElement): number => {
  const first = box.children[0] as HTMLElement
  const second = box.children[1] as HTMLElement
  return second.offsetLeft - first.offsetLeft
}

const measureScroll = () => {
  const box = scroller.value
  if (!box) return
  const furthest = box.scrollWidth - box.clientWidth
  canPanBack.value = box.scrollLeft > 1
  canPanOn.value = box.scrollLeft < furthest - 1
  // Near the end is where the next few are worth asking for, rather than at it. A wide row can
  // fit more than a page holds, so a row with room to spare asks too.
  const short = items.length < perView.value + ahead
  if (short || (furthest > 0 && box.scrollLeft > furthest - pitchOf(box) * ahead)) {
    emit("needs-more")
  }
}

watch([() => items.length, perView], () => {
  requestAnimationFrame(measureScroll)
})

/**
 * Flips two posters that way.
 *
 * A press moves a fixed number of posters rather than a share of the strip, so the row lands
 * where a poster starts and a reader always knows how far they have gone.
 */
const panBy = (direction: number) => {
  // The chevrons are drawn only where the strip is, so it is there to be moved.
  const box = scroller.value as HTMLElement
  box.scrollBy({
    left: direction * pitchOf(box) * JUMP,
    behavior: motion.decorative.value ? "smooth" : "auto",
  })
}

/**
 * The description as it was written, in markdown.
 *
 * The whole card is one control, so a link inside it would be a control inside a control: the
 * anchors are unwrapped and their words kept.
 */
const saidOf = (one: PosterItem): string =>
  $markdownToHtml(one.said || one.title).replaceAll(/<a\b[^>]*>|<\/a>/gu, "")

/* Only what applies is bound: a router link handed an empty `href` draws that instead of its own. */
const leadOf = (one: PosterItem): Record<string, string> => {
  if (one.href === undefined) return {type: "button"}
  return one.href.startsWith("/") ? {to: one.href} : {href: one.href}
}

const sizes = computed<string>(() => `${Math.ceil(width.value / perView.value)}px`)

let observer: ResizeObserver | null = null

onMounted(() => {
  requestAnimationFrame(measureScroll)
  // A strip that is hidden measures nothing, which is not a width to fit posters to.
  observer = new ResizeObserver(entries => {
    width.value = entries[0]?.contentRect.width || width.value
  })
  observer.observe(scroller.value as HTMLElement)
})

onBeforeUnmount(() => observer?.disconnect())
</script>

<template>
  <div
    class="posters"
    :class="{'posters--quiet': lit !== null}"
    :data-testid="testidPrefix"
    :style="{'--per-view': perView}"
    @mouseleave="lit = null"
  >
    <div
      ref="scroller"
      class="posters__scroll"
      @scroll="measureScroll"
    >
      <component
        :is="one.href === undefined ? 'button' : one.href.startsWith('/') ? RouterLink : 'a'"
        v-for="one in items"
        :key="one.id"
        class="posters__poster"
        :class="{'posters__poster--lit': one.id === lit}"
        :data-testid="`${testidPrefix}-${one.id}`"
        v-bind="leadOf(one)"
        @click="emit('open', one.id)"
        @focusin="lit = one.id"
        @mouseenter="lit = one.id"
      >
        <poster-art
          :banner="one.banner"
          class="posters__art"
          :height="one.height"
          :sizes="sizes"
          :srcset="one.srcset"
          :title="one.title"
          :when="one.when"
          :where="one.where"
          :width="one.width"
        />

        <span class="posters__foot">
          <span
            v-if="one.meta"
            class="posters__meta"
          >
            {{ one.meta }}
          </span>
          <!-- The description is written as markdown, and reads as the words it was written in. -->
          <!-- eslint-disable-next-line vue/no-v-html -->
          <span
            class="posters__said"
            v-html="saidOf(one)"
          />
          <!-- The way through is the whole card; the arrow only says so. -->
          <span
            v-if="one.state || one.href"
            class="posters__row"
          >
            <span
              class="posters__state"
              :data-testid="`${testidPrefix}-${one.id}-state`"
            >{{ one.state }}</span>
            <svg
              v-if="one.href"
              aria-hidden="true"
              class="posters__through"
              fill="none"
              height="12"
              viewBox="0 0 20 12"
              width="20"
            >
              <path
                d="M0 6h17M13 1.5L18.5 6L13 10.5"
                stroke="currentColor"
                stroke-width="1.4"
              />
            </svg>
          </span>
        </span>
      </component>
    </div>

    <!--
      Where the strip holds more posters than fit, the way to the rest of them. Resting the
      pointer on one travels that way; a click moves a screenful, which is what somebody
      arriving by keyboard gets.
    -->
    <button
      v-if="canPanBack"
      :aria-label="panBackLabel"
      class="posters__pan posters__pan--back"
      :data-testid="`${testidPrefix}-pan-back`"
      type="button"
      @click="panBy(-1)"
    >
      <svg
        aria-hidden="true"
        fill="none"
        stroke="currentColor"
        stroke-linecap="round"
        stroke-linejoin="round"
        stroke-width="1.6"
        viewBox="0 0 24 24"
      >
        <path d="M14.5 5.5 8 12l6.5 6.5" />
      </svg>
    </button>

    <button
      v-if="canPanOn"
      :aria-label="panOnLabel"
      class="posters__pan posters__pan--on"
      :data-testid="`${testidPrefix}-pan-on`"
      type="button"
      @click="panBy(1)"
    >
      <svg
        aria-hidden="true"
        fill="none"
        stroke="currentColor"
        stroke-linecap="round"
        stroke-linejoin="round"
        stroke-width="1.6"
        viewBox="0 0 24 24"
      >
        <path d="M9.5 5.5 16 12l-6.5 6.5" />
      </svg>
    </button>
  </div>
</template>

<style scoped>
.posters {
  position: relative;
  width: 100%;
}

/* Scrolled rather than paged, and snapped so a poster never rests half off the edge. */
.posters__scroll {
  display: flex;
  gap: 2px;
  /* An x overflow makes y scroll too, and the lit poster's scale would give it room to; the
     padding holds that growth without moving the row. */
  padding-block: 0.5rem;
  margin-block: -0.5rem;
  overflow-x: auto;
  overflow-y: hidden;
  overscroll-behavior-x: contain;
  scroll-snap-type: x mandatory;
  scrollbar-width: none;
}

.posters__scroll::-webkit-scrollbar {
  display: none;
}

.posters__poster {
  display: flex;
  flex: none;
  flex-direction: column;
  width: calc((100% - (var(--per-view) - 1) * 2px) / var(--per-view));
  padding: 0;
  overflow: hidden;
  color: inherit;
  text-align: left;
  cursor: pointer;
  background: none;
  border: 0;
  scroll-snap-align: start;
  /*
   * Composited rather than laid out again: a card that grows by re-rastering redraws its text
   * at each step, and an emoji, which is a bitmap glyph, jumps a size at a time while the
   * letters beside it grow smoothly.
   */
  transform: scale(1);
  transform-origin: center center;
  backface-visibility: hidden;
  will-change: transform;
  transition: transform 320ms var(--ease-out-quint);
}

/* The whole card answers the pointer, not only its picture. */
.posters__poster--lit {
  position: relative;
  z-index: 1;
  transform: scale(1.02);
}




/* One poster lit, the rest quietened: the strip's own way of answering the pointer. */
.posters--quiet :deep(.poster-art__img) {
  opacity: 0.55;
}

.posters__poster--lit :deep(.poster-art__img) {
  opacity: 1;
}

.posters__foot {
  display: flex;
  flex-grow: 1;
  flex-direction: column;
  gap: 0.2rem;
  padding: 0.85rem 1rem 1.1rem;
  background-color: var(--band-ground);
}

.posters__meta {
  display: block;
  font-family: var(--font-bitmap);
  font-size: 0.68rem;
  letter-spacing: 0.06em;
  color: var(--color-ash);
}

.posters__row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.75rem;
  margin-top: auto;
  padding-top: 0.45rem;
}

.posters__state {
  font-size: 0.78rem;
  color: var(--color-ash);
}

.posters__through {
  flex: none;
  color: var(--color-ash);
  transition: color 200ms ease, translate 200ms ease;
}

.posters__poster--lit .posters__through {
  color: var(--color-brand);
  translate: 3px 0;
}

.posters__said {
  display: -webkit-box;
  overflow: hidden;
  font-size: 0.88rem;
  line-height: 1.5;
  color: var(--color-chalk);
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 3;
}

/* As tall as the posters, not the room kept above and below them for the lit one to grow. */
.posters__pan {
  position: absolute;
  top: 0.5rem;
  bottom: 0.5rem;
  z-index: 3;
  display: grid;
  place-items: center;
  width: 44px;
  padding: 0;
  color: var(--color-chalk);
  cursor: pointer;
  background: none;
  border: 0;
}

.posters__pan::before {
  content: "";
  position: absolute;
  top: 0;
  bottom: 0;
  pointer-events: none;
  opacity: 0.72;
  transition: opacity 220ms ease;
}

.posters__pan:hover::before,
.posters__pan:focus-visible::before {
  opacity: 1;
}

.posters__pan svg {
  position: relative;
  width: 26px;
  height: 26px;
  opacity: 0.78;
  transition: scale 220ms ease, opacity 220ms ease;
}

.posters__pan:hover svg,
.posters__pan:focus-visible svg {
  opacity: 1;
  scale: 1.24;
}

.posters__pan--back {
  left: 0;
}

.posters__pan--back::before {
  left: 0;
  right: -40px;
  background: linear-gradient(to right, color-mix(in oklab, var(--color-ground) 82%, transparent), transparent);
}

.posters__pan--on {
  right: 0;
}

.posters__pan--on::before {
  left: -40px;
  right: 0;
  background: linear-gradient(to left, color-mix(in oklab, var(--color-ground) 82%, transparent), transparent);
}

@media (max-width: 639.98px) {
  .posters__foot {
    padding: 0.8rem 1rem 1rem;
  }

  .posters__pan {
    width: 34px;
  }

  .posters__pan svg {
    width: 20px;
    height: 20px;
  }
}

@media (prefers-reduced-motion: reduce) {
  :deep(.poster-art__img),
  .posters__poster {
    transition: none;
  }
}
</style>
