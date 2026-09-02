<script lang="ts" setup>
import {computed, onBeforeUnmount, onMounted, ref} from "vue"
import {coveredWidth, sizeOf, srcsetOf, type Picture} from "@/components/island/pictures"
import {romanNumeral} from "../reading"

/**
 * A board's photograph as a band across the island, with its number set large across the lower
 * cut of it.
 *
 * The band is the page's hero and the one thing on it that is the board rather than a fact about
 * the board — which is why it is edge to edge and cut on the island's own diagonal: another band
 * in the stack rather than a picture placed on a page.
 *
 * A board with no photograph keeps the band and its height, filled with its own colour and its
 * numeral centred. Boards I to IV have no photograph at all and the board taking office has none
 * yet, so half the association's history arrives this way: it has to look like a decision.
 */
defineOptions({name: "BoardBand"})

const props = withDefaults(defineProps<{
  /** The board's number, which the band sets in Roman numerals. */
  number: number
  /** The board's photograph, where one has been recorded. */
  photo?: Picture | null
  /** What the photograph is of, for a reader who is not being shown it. */
  label?: string
  /**
   * Whether to offer a photograph on a board that has none.
   *
   * Offered in the band itself, which is where its absence is what a reader is looking at.
   * Decided by the page, which knows who is reading; nothing here is a guard.
   */
  mayAddPhoto?: boolean
  testid?: string
}>(), {photo: null, label: "", mayAddPhoto: false, testid: "board-band"})

const emit = defineEmits<{(event: "add-photo"): void}>()

const numeral = computed(() => romanNumeral(props.number))
const srcset = computed(() => srcsetOf(props.photo))
const size = computed(() => sizeOf(props.photo))

const frame = ref<HTMLElement | null>(null)

/**
 * How much picture the band will really need, in css pixels.
 *
 * A band edge to edge is as wide as the window, and it covers its box: a photograph shorter in
 * proportion than the band is scaled up until it fills the height and drawn wider than the box
 * it sits in, so the width a browser is promised has to say so. Measured rather than guessed,
 * because the band's height is a clamp on the viewport and nothing here can work it out.
 *
 * Nothing until the band has been laid out, and `100vw` stands in — which understates on a wide
 * screen and is exactly right on a phone. That is the way round to be wrong: the phone is what
 * this is for, and a wide screen corrects itself on the next frame.
 */
const asked = ref(0)

const measure = () => {
  const box = frame.value
  if (!box) return
  asked.value = Math.max(asked.value, coveredWidth({
    boxWidth: box.clientWidth,
    boxHeight: box.clientHeight,
    imageWidth: props.photo?.width,
    imageHeight: props.photo?.height,
  }))
}

const sizes = computed(() => (asked.value > 0 ? `${asked.value}px` : "100vw"))

let observer: ResizeObserver | null = null

onMounted(() => {
  measure()
  if (!frame.value || typeof ResizeObserver === "undefined") return
  observer = new ResizeObserver(measure)
  observer.observe(frame.value)
})

onBeforeUnmount(() => observer?.disconnect())
</script>

<template>
  <section
    class="board-band"
    :data-testid="testid"
  >
    <div
      ref="frame"
      class="board-band__frame"
      :class="{'board-band__frame--bare': !photo}"
    >
      <img
        v-if="photo"
        :alt="label"
        class="board-band__photo"
        data-testid="board-photo"
        :height="size.height"
        :sizes="sizes"
        :src="photo.url"
        :srcset="srcset"
        :width="size.width"
        @load="measure"
      >

      <!-- Under the numeral and nowhere else. A wash over the whole photograph filters the
           picture, which is the board's own and is the thing a visitor came for. -->
      <span
        v-if="photo"
        aria-hidden="true"
        class="board-band__scrim"
      />

      <!--
        Spoken by the identity block below, which reads the same number in the same numerals, so
        this is the figure and not the fact. A screen reader hearing "IX" twice learns nothing
        the second time.
      -->
      <span
        aria-hidden="true"
        class="board-band__numeral"
        :class="{'board-band__numeral--centred': !photo}"
        data-testid="board-numeral"
      >{{ numeral }}</span>

      <!--
        Half the history has no photograph, so the way to add one belongs in the band that is
        standing in for it rather than behind a pencil somewhere else. Only where there is
        none: a photograph that is wrong is replaced in the dialog, beside the crop.
      -->
      <button
        v-if="!photo && mayAddPhoto"
        class="board-band__add"
        data-testid="board-band-add-photo"
        type="button"
        @click="emit('add-photo')"
      >
        Add a photograph
      </button>
    </div>
  </section>
</template>

<style scoped>
/*
 * The band overflows nothing and animates nothing: it is the height it is whether or not there
 * is a photograph in it, so moving from one board to the next does not read as something
 * failing to load.
 */
.board-band {
  --cut: clamp(16px, 2.4vw, 34px);

  position: relative;
  width: 100%;
}

/*
 * Cut top and bottom on the island's own diagonal, and on the same lean: a parallelogram rather
 * than a rectangle with its corners taken off, so the two edges are one angle read twice.
 *
 * The lower left is the cut edge, which is what the numeral straddles. The upper left stays
 * square, so the band meets the timeline above it on a line rather than across a wedge.
 */
.board-band__frame {
  position: relative;
  overflow: hidden;
  height: clamp(13rem, 38vw, 27rem);
  clip-path: polygon(0 0, 100% var(--cut), 100% 100%, 0 calc(100% - var(--cut)));
  background-color: var(--color-pit);
}

/*
 * A board with no photograph, filled with its own colour.
 *
 * The colour washed into the band's ground rather than laid down raw: the ground follows the
 * theme, so a historical colour nobody vetted is readable against the numeral in both halves,
 * and the band still reads as that board's rather than as a grey gap.
 */
.board-band__frame--bare {
  background-color: color-mix(in oklab, var(--accent) 30%, var(--color-pit));
  background-image:
    radial-gradient(
      48rem 22rem at 22% 120%,
      color-mix(in oklab, var(--accent) 34%, transparent),
      transparent 70%
    );
}

.board-band__photo {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

/* Bottom left, where the numeral is, and nowhere near the faces above it. */
.board-band__scrim {
  position: absolute;
  inset: 0;
  pointer-events: none;
  background:
    radial-gradient(30rem 16rem at 4% 108%, oklch(0 0 0 / 78%), transparent 72%),
    linear-gradient(to top, oklch(0 0 0 / 42%), transparent 34%);
}

/*
 * The number, set large and cut by the same diagonal as the band: it straddles the lower edge,
 * so it reads as part of the band's own geometry rather than as a caption laid on it.
 *
 * White in both themes over a photograph, because the picture underneath is the board's own and
 * not something the site chose — the same reason the esports band keeps its dark treatment in
 * light mode.
 */
.board-band__numeral {
  position: absolute;
  bottom: calc(var(--cut) * 0.25);
  left: clamp(0.9rem, 3vw, 2.75rem);
  font-family: var(--font-display);
  font-size: clamp(3.75rem, 13vw, 9.5rem);
  line-height: 0.82;
  letter-spacing: 0.02em;
  color: #f4f6f8;
  text-shadow: 0 2px 24px oklch(0 0 0 / 45%);
}

/*
 * The way to a photograph on a board that has none, under the numeral it stands beside.
 *
 * Written rather than drawn: the band is the largest empty space on the page and a bare plus
 * in the middle of it would read as decoration. Set on the chalk of the theme, which the
 * band's own fill is washed into, so it is legible whatever colour the board chose.
 */
.board-band__add {
  position: absolute;
  bottom: 14%;
  left: 50%;
  translate: -50%;
  padding: 0.5rem 1.1rem;
  font-family: var(--font-display);
  font-size: 0.75rem;
  font-style: italic;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--color-chalk);
  cursor: pointer;
  background: color-mix(in oklab, var(--color-chalk) 10%, transparent);
  border: 1px solid color-mix(in oklab, var(--color-chalk) 34%, transparent);
  clip-path: polygon(10px 0, 100% 0, calc(100% - 10px) 100%, 0 100%);
}

.board-band__add:hover {
  background: color-mix(in oklab, var(--color-chalk) 20%, transparent);
}

/* No photograph, no lower left to hold: the numeral is the whole band, so it takes the middle. */
.board-band__numeral--centred {
  bottom: auto;
  left: 50%;
  top: 50%;
  translate: -50% -50%;
  color: var(--color-chalk);
  text-shadow: none;
  opacity: 0.92;
}
</style>
