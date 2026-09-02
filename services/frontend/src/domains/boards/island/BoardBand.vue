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
 * Nothing until the band has been laid out, and `100vw` stands in, which understates on a wide
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
 * The strip runs edge to edge, and the photograph inside it is never stretched to get there.
 *
 * Full-bleed was the original defect only because the picture was made to cover the whole
 * width: a window 2560 across asked for 2560 pixels of a photograph that has 1000. Given its
 * own height and its own proportions it is about 530 across whatever the window is, so the
 * strip can have the width and the photograph is still drawn smaller than it was taken.
 */
.board-band {
  width: 100%;
}

/* The photograph at its own shape, and the board's colour filling what is left. The colour
   comes in over the picture's right edge rather than meeting it at a seam, which is why the
   words are pulled back over the picture and start transparent. */
.board-band__frame {
  /* How far the words are pulled back over the picture, which the wash beside the picture
     has to stay clear of. */
  --pull: 7rem;

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
 * The photograph keeps its own proportions, so height is the only dial. The floor is a hero
 * rather than a line with a stamp at one end; the ceiling is 36rem because the smallest
 * photograph the association has is 1000 across, and a box taller than about 42rem asks for
 * more pixels than exist.
 */
.board-band__picture {
  position: relative;
  flex: 0 0 auto;
  height: clamp(20rem, 38vw, 36rem);
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
  margin-left: calc(var(--pull) * -1);
  padding: 1.75rem 2rem 1.75rem calc(var(--pull) + 1rem);
  /*
   * The board's colour as a light source at one corner, not a fill.
   *
   * It starts at the picture's top right, which is this panel's own top left, and thins with
   * distance from it in every direction: leftwards it is gone before the faces, rightwards and
   * downwards the page's own ground takes over. The carry under it does one job, giving the
   * picture's dissolve a colour to fade into instead of a hard edge of ground.
   *
   * `--pull` is where the picture's right edge falls inside this panel, since the panel is
   * pulled back over the picture by exactly that much.
   */
  background-image:
    radial-gradient(
      118% 160% at var(--pull) 0,
      color-mix(in oklab, var(--accent, var(--color-brand)) var(--board-wash-on), transparent) 0%,
      color-mix(in oklab, var(--accent, var(--color-brand)) var(--board-wash), transparent) 40%,
      transparent 78%
    ),
    linear-gradient(
      to right,
      transparent calc(var(--pull) - 3rem),
      color-mix(in oklab, var(--accent, var(--color-brand)) var(--board-wash), transparent) var(--pull),
      transparent 72%
    );
}

/* Nothing to be pulled back over and no edge to meet, so the words simply start at the left
   and the frame's own colour is the ground they are read on. */
.board-band__frame--bare .board-band__words {
  margin-left: 0;
  padding-left: 2rem;
  background-image: none;
}

/* Over the corner wash, which is under the words by design. */
.board-band__eyebrow,
.board-band__name,
.board-band__cheer,
.board-band__blurb,
.board-band__add {
  position: relative;
  z-index: 1;
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
  font-size: clamp(0.95rem, 1.1vw, 1.15rem);
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
 * A board with no photograph: the same colour the words get beside one.
 *
 * The banner's own recipe rather than a fill of its own: the corner light and the carry under
 * it, on the same tokens, mixed with `transparent` so the page's ground and its pattern come
 * through. Filled instead, it read as a block of paint with writing on it while the same
 * board beside a photograph read as a tint, which is two different pages.
 *
 * The corner is the strip's own top left here, since there is no picture for it to come off,
 * and it thins away down and to the right into the page.
 *
 * No height of its own either. A hero is a photograph, and a strip held to a photograph's
 * height with nothing in it is a field of colour with a line of writing at the top of it: what
 * is there is the words, so the words decide how tall it is.
 */
.board-band__frame--bare {
  background-image:
    radial-gradient(
      118% 170% at 0 0,
      color-mix(in oklab, var(--accent, var(--color-brand)) var(--board-wash-on), transparent) 0%,
      color-mix(in oklab, var(--accent, var(--color-brand)) var(--board-wash), transparent) 42%,
      transparent 80%
    ),
    linear-gradient(
      to bottom,
      color-mix(in oklab, var(--accent, var(--color-brand)) var(--board-wash), transparent) 0%,
      transparent 100%
    );
}

/* Room of its own, since there is no picture standing beside the words to give them any. */
.board-band__frame--bare .board-band__words {
  padding-top: 2.5rem;
  padding-bottom: 3.25rem;
}

/*
 * The picture dissolves at its right edge rather than stopping at one.
 *
 * The wash alone could not do this: a tint subtle enough to see the page's ground through is
 * invisible over a dark photograph, so the two met at a line however far the colour was pulled
 * back over the picture. Fading the picture itself is what the board rows on the old page did,
 * and it is the join the strip wanted: the photograph goes to ground, and the colour is
 * already there when it arrives.
 */
.board-band__photo {
  display: block;
  height: 100%;
  width: auto;
  max-width: none;
  mask-image: linear-gradient(to right, #000 0, #000 calc(100% - var(--photo-dissolve)), transparent 100%);
  -webkit-mask-image: linear-gradient(to right, #000 0, #000 calc(100% - var(--photo-dissolve)), transparent 100%);
}

/* Stacked, the picture fades downwards into the words under it instead. */
@media (max-width: 767px) {
  .board-band__photo {
    mask-image: linear-gradient(to bottom, #000 0, #000 calc(100% - var(--photo-dissolve)), transparent 100%);
    -webkit-mask-image: linear-gradient(to bottom, #000 0, #000 calc(100% - var(--photo-dissolve)), transparent 100%);
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
