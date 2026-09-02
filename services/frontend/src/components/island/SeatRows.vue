<script lang="ts" setup>
import {ref, watch} from "vue"
import {monogramOf} from "./monogram"

/**
 * People as a stack of rows that open: a portrait, a name, what they were, and what they wrote.
 *
 * A vertical accordion rather than a variant of the banner slices. That component's substance is
 * the arithmetic of a horizontal row — what share of it a slice takes, how wide a picture is
 * really painted, and which slice a scroll has in the middle of a phone — and a stack of rows has
 * none of it, so an orientation flag would double every branch in it for nothing.
 *
 * It draws whatever it is handed and knows nothing about boards (frontend ADR-001): the order the
 * rows read in, the name with its nickname back inside it and where a portrait is served from are
 * all settled by the page before they arrive here.
 */
defineOptions({name: "SeatRows"})

export interface SeatRow {
  id: number | string
  /** The name as the history publishes it, nickname and all. */
  name: string
  /** What they were, in the words whoever wrote it down used. */
  role: string
  /** What they wrote about themselves, where anything was written down. */
  blurb?: string
  /** Their portrait, where there is one. */
  portrait?: string
  /** The widths it is stored at, ready for a `srcset`, where it is stored at several. */
  srcset?: string
}

const props = defineProps<{
  rows: SeatRow[]
  /** The colour the plates, the roles and the chevrons are drawn in. */
  accent: string
  /** What each row's data-testid is built from, since a page names its own rows. */
  testidPrefix: string
}>()

/**
 * How wide a plate is drawn, so the browser can fetch a copy of that size rather than the master.
 *
 * Stated here because it is the stylesheet below that decides it, and a portrait covers a square
 * plate: the width is the whole of what a browser needs to be promised. The figure is the top of
 * the clamp — a plate is never drawn wider, and a promise that overstates costs a device pixel
 * ratio's worth of bytes, where one that understates costs a blurred face.
 */
const PLATE_WIDTH = "88px"

/**
 * Which row is open, and one at a time.
 *
 * The first row with something written about it opens as the rows arrive, which on a board is the
 * chair: a reader meets an open row rather than a stack of shut ones and has to guess that any of
 * them open. Where nobody wrote anything — a whole board of the history is like that — nothing
 * opens, because there is nothing to show.
 *
 * Reset as the rows change rather than held by id: a different set is a different board, and the
 * row that was open belonged to the one before it.
 */
const open = ref<SeatRow["id"] | null>(null)

watch(() => props.rows, rows => {
  open.value = rows.find(row => row.blurb)?.id ?? null
}, {immediate: true})

const isOpen = (row: SeatRow): boolean => open.value === row.id

/** Shutting the open row is the same gesture that opened it, so nothing is ever stuck open. */
const toggle = (row: SeatRow) => {
  if (!row.blurb) return
  open.value = isOpen(row) ? null : row.id
}
</script>

<template>
  <div
    class="seat-rows"
    :data-testid="`${testidPrefix}-seat-rows`"
    :style="{'--accent': accent}"
  >
    <article
      v-for="row in rows"
      :key="row.id"
      class="seat-row"
      :class="{'seat-row--open': isOpen(row)}"
      :data-testid="`${testidPrefix}-seat-${row.id}`"
    >
      <!--
        A button only where there is something behind it. A row with nothing written about it is
        not a control that does nothing when pressed: it offers no chevron, takes no focus and
        answers no click.
      -->
      <component
        :is="row.blurb ? 'button' : 'div'"
        :aria-controls="row.blurb ? `${testidPrefix}-seat-said-${row.id}` : undefined"
        :aria-expanded="row.blurb ? isOpen(row) : undefined"
        class="seat-row__head"
        :type="row.blurb ? 'button' : undefined"
        @click="toggle(row)"
      >
        <!-- Decorative: the name is beside it and says the same thing. -->
        <img
          v-if="row.portrait"
          alt=""
          class="seat-row__plate seat-row__portrait"
          :data-testid="`${testidPrefix}-seat-portrait-${row.id}`"
          :sizes="PLATE_WIDTH"
          :src="row.portrait"
          :srcset="row.srcset"
        >
        <!--
          No portrait, so the initials in the board's own colour. The plate is the same size and
          the same shape either way, which is the point of it: most of this history has no
          picture at all, and the column has to stay a column.
        -->
        <span
          v-else
          aria-hidden="true"
          class="seat-row__plate seat-row__monogram"
          :data-testid="`${testidPrefix}-seat-monogram-${row.id}`"
        >{{ monogramOf(row.name) }}</span>

        <span class="seat-row__who">
          <span
            class="seat-row__name"
            :data-testid="`${testidPrefix}-seat-name-${row.id}`"
          >{{ row.name }}</span>
          <span
            class="seat-row__role"
            :data-testid="`${testidPrefix}-seat-role-${row.id}`"
          >{{ row.role }}</span>
        </span>

        <span
          v-if="row.blurb"
          aria-hidden="true"
          class="seat-row__chevron"
          :data-testid="`${testidPrefix}-seat-chevron-${row.id}`"
        >
          <svg
            fill="none"
            stroke="currentColor"
            stroke-linecap="round"
            stroke-width="2"
            viewBox="0 0 24 24"
          >
            <path d="M6 9l6 6 6-6" />
          </svg>
        </span>
      </component>

      <!--
        Kept in the document while it is shut so that opening it is a change of height rather than
        a paragraph appearing out of nothing, and hidden from a screen reader while it is: the
        chevron above has just said the row is shut, and text read out of a shut row contradicts
        it.
      -->
      <div
        v-if="row.blurb"
        :id="`${testidPrefix}-seat-said-${row.id}`"
        :aria-hidden="isOpen(row) ? undefined : 'true'"
        class="seat-row__said"
        :data-testid="`${testidPrefix}-seat-blurb-${row.id}`"
      >
        <p>{{ row.blurb }}</p>
      </div>
    </article>
  </div>
</template>

<style scoped>
/*
 * Rows divided by a hairline rather than cards on a page: a board is a list of the people who
 * ran it, and a stack of boxes reads as a set of unrelated things.
 *
 * The plate's size and the diagonal it is cut on are declared once, here, because the row and
 * the paragraph under it both line up against them.
 */
.seat-rows {
  --plate: clamp(3.5rem, 11vw, 5.5rem);
  --cut: 12px;

  display: flex;
  flex-direction: column;
}

.seat-row {
  border-bottom: 1px solid var(--color-hairline);
}

.seat-row:first-child {
  border-top: 1px solid var(--color-hairline);
}

/* The open row is lit by the board's own colour, faintly: enough to say which row is speaking. */
.seat-row--open {
  background-color: color-mix(in oklab, var(--accent) 7%, transparent);
}

.seat-row__head {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 1rem;
  width: 100%;
  padding: 0.85rem 0.5rem;
  text-align: left;
}

button.seat-row__head {
  cursor: pointer;
}

/*
 * The portrait and the initials are one shape drawn two ways — cut on the island's own diagonal
 * and on the same lean as the bands above it, so the column belongs to the page rather than
 * sitting on it.
 */
.seat-row__plate {
  flex: none;
  width: var(--plate);
  height: var(--plate);
  clip-path: polygon(var(--cut) 0, 100% 0, calc(100% - var(--cut)) 100%, 0 100%);
}

.seat-row__portrait {
  object-fit: cover;
}

/*
 * The board's colour washed into the ground rather than laid down raw, for the reason the bare
 * band gives: the ground follows the theme, so a historical colour nobody vetted carries its
 * letters in both halves instead of only in the one it was picked against.
 */
.seat-row__monogram {
  display: grid;
  place-items: center;
  background-color: color-mix(in oklab, var(--accent) 45%, var(--color-pit));
  font-family: var(--font-display);
  font-size: calc(var(--plate) * 0.32);
  letter-spacing: 0.04em;
  color: var(--color-chalk);
}

.seat-row__who {
  display: flex;
  min-width: 0;
  flex-direction: column;
}

.seat-row__name {
  font-family: var(--font-display);
  font-size: clamp(1rem, 2.6vw, 1.4rem);
  line-height: 1.15;
  text-transform: uppercase;
  color: var(--color-chalk);
}

/* In the board's colour, the way a player's part is on the esports band: it qualifies the name. */
.seat-row__role {
  margin-top: 0.25rem;
  font-family: var(--font-body);
  font-size: 0.78rem;
  letter-spacing: 0.09em;
  text-transform: uppercase;
  color: color-mix(in oklab, var(--accent) 82%, var(--color-chalk));
}

.seat-row__chevron {
  display: grid;
  place-items: center;
  width: 2rem;
  height: 2rem;
  color: color-mix(in oklab, var(--accent) 82%, var(--color-chalk));
  transition: rotate 320ms cubic-bezier(0.22, 1, 0.36, 1);
}

.seat-row--open .seat-row__chevron {
  rotate: 180deg;
}

.seat-row__chevron svg {
  width: 1.2rem;
  height: 1.2rem;
}

.seat-row__said {
  overflow: hidden;
  max-height: 0;
  opacity: 0;
  transition: max-height 460ms cubic-bezier(0.22, 1, 0.36, 1), opacity 260ms ease;
}

.seat-row--open .seat-row__said {
  max-height: 26rem;
  opacity: 1;
}

/* Indented to the name above it, so what somebody wrote reads as theirs rather than as the list's. */
.seat-row__said p {
  max-width: 44rem;
  margin: 0;
  padding: 0 0.5rem 1.1rem calc(var(--plate) + 1.5rem);
  font-family: var(--font-body);
  font-size: 0.92rem;
  line-height: 1.6;
  color: var(--color-ash);
}

button.seat-row__head:hover .seat-row__name {
  color: color-mix(in oklab, var(--accent) 35%, var(--color-chalk));
}

/* On a phone the indent is most of the width, so the paragraph takes the row instead. */
@media (max-width: 640px) {
  .seat-row__head {
    gap: 0.8rem;
    padding-inline: 0.25rem;
  }

  .seat-row__said p {
    padding-left: 0.25rem;
  }
}

@media (prefers-reduced-motion: reduce) {
  .seat-row__chevron,
  .seat-row__said {
    transition: none;
  }
}
</style>
