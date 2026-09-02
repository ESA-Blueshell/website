<script lang="ts" setup>
import {computed, onBeforeUnmount, onMounted, ref} from "vue"
import {coveredWidth, sizeOf, srcsetOf, type Picture} from "@/components/island/pictures"

/**
 * A board's photograph as a strip across the island, with the board's own words beside it.
 *
 * The strip is the page's hero and the one thing on it that is the board rather than a fact
 * about the board, which is why it runs edge to edge: another band in the stack rather than a
 * picture placed on a page.
 *
 * A board with no photograph keeps the strip and its height, filled with its own colour, and its
 * words take the whole of it. Boards I to IV have no photograph at all and the board taking
 * office has none yet, so half the association's history arrives this way: it has to look like
 * a decision.
 */
defineOptions({name: "BoardBand"})

const props = withDefaults(defineProps<{
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
  /** `BOARD IX · 2025-2026`, which the domain composes rather than the band. */
  eyebrow?: string
  /** The board's own name, where one is recorded. */
  name?: string
  /** What the board shouted, where anything was. */
  cheer?: string
  /** What the board was about, where anybody has written it down. */
  description?: string
  testid?: string
}>(), {
  photo: null, label: "", mayAddPhoto: false,
  eyebrow: "", name: "", cheer: "", description: "",
  testid: "board-band",
})

const emit = defineEmits<{(event: "add-photo"): void}>()

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
      <!-- Only where there is one: with no photograph there is no box for one to stand in,
           and the words take the whole of the strip. -->
      <div
        v-if="photo"
        class="board-band__picture"
      >
        <img
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

        <!--
        The second wash, in from the top left and gone by the bottom right.

        The one on the right carries the words; this one lights the picture from a corner so it
        is not a flat rectangle with colour bolted to one side. Slight, and in the board's own
        colour, drawn with the same token the timeline lights its bands with.
      -->
        <span
          aria-hidden="true"
          class="board-band__corner"
        />
      </div>

      <!--
        The words, on a wash of the board's own colour that comes in off the photograph.

        Here rather than under the band because a board's name, its year, what it shouted and
        what it was about are the caption to its photograph, and a caption belongs beside the
        picture. The wash is the board's colour so that moving along the strip recolours the
        page, which is the whole of why a board has one.
      -->
      <div
        class="board-band__words"
        :data-testid="`${testid}-words`"
      >
        <p
          v-if="eyebrow"
          class="board-band__eyebrow"
          :data-testid="`${testid}-eyebrow`"
        >
          {{ eyebrow }}
        </p>
        <p
          v-if="name"
          class="board-band__name"
          :data-testid="`${testid}-name`"
        >
          {{ name }}
        </p>
        <p
          v-if="cheer"
          class="board-band__cheer"
          :data-testid="`${testid}-cheer`"
        >
          &ldquo;{{ cheer }}&rdquo;
        </p>
        <p
          v-if="description"
          class="board-band__blurb"
          :data-testid="`${testid}-description`"
        >
          {{ description }}
        </p>

        <!--
          Half the history has no photograph, so the way to add one belongs in the strip that
          is standing in for it rather than behind a pencil somewhere else. In among the words
          because there is no picture for it to sit on. Only where there is none: a photograph
          that is wrong is replaced in the dialog, beside the crop.
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
    </div>
  </section>
</template>

<style scoped>
/*
 * The band overflows nothing and animates nothing: it is the height it is whether or not there
 * is a photograph in it, so moving from one board to the next does not read as something
 * failing to load.
 */
/*
 * The band sits in the island's own column rather than edge to edge.
 *
 * Full-bleed was the defect: a window 2560 wide asked for 2560 pixels of a photograph that has
 * 1000 of them, so the widest board was drawn at two and a half times its size, and a band a
 * fixed few hundred tall cropped a 1.5:1 group photograph to a quarter of its height. In the
 * island's column the photograph's box is about 670 across, which every board downscales into,
 * and the box is nearly the shape the photographs already are — so what a reader sees is the
 * photograph rather than a strip taken out of the middle of it.
 */
/*
 * The strip runs edge to edge, and the photograph inside it is never stretched to get there.
 *
 * Full-bleed was the original defect only because the picture was made to cover the whole
 * width: a window 2560 across asked for 2560 pixels of a photograph that has 1000. Given its
 * own height and its own proportions the picture is about 530 across whatever the window is,
 * so the strip can have the width and the photograph is still drawn smaller than it was taken.
 */
.board-band {
  width: 100%;
}

/*
 * The photograph at its own shape, and the board's colour filling what is left.
 *
 * No diagonal across the picture and no crop: the photograph is given the band's full height
 * and whatever width its own proportions ask for, so what a reader sees is the whole
 * photograph. The colour takes the rest of the row and fades in over the picture's right edge
 * rather than meeting it at a seam, which is why the words panel is pulled back over the
 * picture and starts transparent.
 */
.board-band__frame {
  position: relative;
  display: flex;
  align-items: stretch;
  width: 100%;
  overflow: hidden;
}

/* Stacked, the photograph takes the width and the words sit under it. */
@media (max-width: 767px) {
  .board-band__frame {
    flex-direction: column;
  }

  .board-band__picture {
    height: clamp(10rem, 52vw, 16rem);
    width: 100%;
  }

  .board-band__photo {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }

  .board-band__words {
    margin-left: 0;
    margin-top: -3rem;
    padding: 3.5rem 1.5rem 1.5rem;
    background-image: linear-gradient(
      to bottom,
      transparent 0%,
      color-mix(in oklab, var(--accent, var(--color-brand)) 34%, var(--color-surface)) 34%,
      color-mix(in oklab, var(--accent, var(--color-brand)) 58%, var(--color-surface)) 100%
    );
  }
}

/*
 * How tall the picture stands, which is also how wide it comes out.
 *
 * The photograph keeps its own proportions, so height is the only dial: at 38vw it is about
 * 57% of the window across at any size, which is the share that stops the strip reading as a
 * line with a stamp at one end. The ceiling is 36rem because the smallest photograph the
 * association has is 1000 across, and a box taller than about 42rem would ask for more pixels
 * than that — the exact fault this band was rebuilt to remove.
 */
.board-band__corner {
  position: absolute;
  inset: 0;
  z-index: 1;
  pointer-events: none;
  background-image: linear-gradient(
    to bottom right,
    color-mix(in oklab, var(--accent, var(--color-brand)) var(--band-wash-on), transparent) 0%,
    color-mix(in oklab, var(--accent, var(--color-brand)) var(--band-wash), transparent) 34%,
    transparent 62%
  );
}

.board-band__picture {
  position: relative;
  flex: 0 0 auto;
  height: clamp(14rem, 38vw, 36rem);
}

/*
 * The wash the words are read on: the board's own colour, coming in off the photograph.
 *
 * Mixed into the island's own ground rather than painted raw, so it is the board's colour in
 * both themes and the ink the island already sets still reads on it. A board with no colour of
 * its own mixes the association's blue, which is what `--accent` falls back to.
 */
.board-band__words {
  position: relative;
  z-index: 1;
  display: flex;
  flex: 1 1 0;
  min-width: 0;
  flex-direction: column;
  justify-content: center;
  gap: 0.4rem;
  /* Pulled back over the picture, so the colour begins inside it. */
  margin-left: -7rem;
  padding: 1.75rem 2rem 1.75rem 8rem;
  /*
   * The board's colour as a wash rather than a fill.
   *
   * Mixed with `transparent` and taken from the island's own `--band-wash` tokens, which is
   * what the timeline lights its bands with — so the two agree, the page's ground still shows
   * through, and a colour that would be shouting at full strength is a tint. The stops are in
   * rem rather than percentages because the panel is pulled back over the picture by a fixed
   * amount, and the colour has to have arrived by then or the two meet at a seam.
   */
  background-image: linear-gradient(
    to right,
    transparent 0,
    color-mix(in oklab, var(--accent, var(--color-brand)) var(--band-wash), transparent) 7rem,
    color-mix(in oklab, var(--accent, var(--color-brand)) var(--band-wash-on), transparent) 100%
  );
}

/* Nothing to be pulled back over and no edge to meet, so the words simply start at the left
   and the frame's own colour is the ground they are read on. */
.board-band__frame--bare .board-band__words {
  margin-left: 0;
  padding-left: 2rem;
  background-image: none;
}

.board-band__eyebrow,
.board-band__name,
.board-band__cheer,
.board-band__blurb {
  max-width: 34rem;
}

.board-band__eyebrow {
  margin: 0;
  font-family: var(--font-body);
  font-size: clamp(0.8rem, 1vw, 0.95rem);
  font-weight: 500;
  letter-spacing: 0.3em;
  text-transform: uppercase;
  opacity: 0.85;
}

.board-band__name {
  margin: 0;
  font-family: var(--font-name);
  font-size: clamp(1.5rem, 3.4vw, 2.6rem);
  font-weight: 600;
  line-height: 1.05;
}

.board-band__cheer {
  margin: 0.15rem 0 0;
  font-family: var(--font-display);
  font-size: clamp(0.95rem, 1.6vw, 1.3rem);
  line-height: 1.2;
  opacity: 0.95;
}

.board-band__blurb {
  margin: 0.55rem 0 0;
  font-family: var(--font-body);
  font-size: 0.85rem;
  line-height: 1.5;
  opacity: 0.9;
}

/*
 * A board with no photograph, filled with its own colour.
 *
 * The colour washed into the strip's ground rather than laid down raw: the ground follows the
 * theme, so a historical colour nobody vetted still reads under the words in both halves, and
 * the strip still reads as that board's rather than as a grey gap.
 *
 * The height is the strip's own here. There is no picture to stand it up, so the frame carries
 * the same figure the picture does and a board without one is the height of a board with one.
 */
.board-band__frame--bare {
  min-height: clamp(14rem, 38vw, 36rem);
  background-color: color-mix(in oklab, var(--accent) 30%, var(--color-pit));
  background-image:
    radial-gradient(
      48rem 22rem at 22% 120%,
      color-mix(in oklab, var(--accent) 34%, transparent),
      transparent 70%
    );
}

/*
 * The picture dissolves at its right edge rather than stopping at one.
 *
 * The wash alone could not do this: a tint subtle enough to see the page's ground through is
 * invisible over a dark photograph, so the two met at a line however far the colour was pulled
 * back over the picture. Fading the picture itself is what the board rows on the old page did,
 * and it is the join the strip wanted — the photograph goes to ground, and the colour is
 * already there when it arrives.
 */
.board-band__photo {
  display: block;
  height: 100%;
  width: auto;
  max-width: none;
  mask-image: linear-gradient(to right, #000 0, #000 calc(100% - 8rem), transparent 100%);
  -webkit-mask-image: linear-gradient(to right, #000 0, #000 calc(100% - 8rem), transparent 100%);
}

/* Stacked, the picture fades downwards into the words under it instead. */
@media (max-width: 767px) {
  .board-band__photo {
    mask-image: linear-gradient(to bottom, #000 0, #000 calc(100% - 4rem), transparent 100%);
    -webkit-mask-image: linear-gradient(to bottom, #000 0, #000 calc(100% - 4rem), transparent 100%);
  }
}

/*
 * The way to a photograph on a board that has none, under the words it stands beneath.
 *
 * Written rather than drawn: the strip is the largest empty space on the page and a bare plus
 * in the middle of it would read as decoration. In among the words rather than laid over the
 * strip, because there is no picture for it to sit on. Set on the chalk of the theme, which
 * the strip's own fill is washed into, so it is legible whatever colour the board chose.
 */
.board-band__add {
  align-self: flex-start;
  margin-top: 1.1rem;
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

</style>
