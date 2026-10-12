<script lang="ts" setup>
import {computed} from "vue"
import StateTag from "@/components/island/StateTag.vue"
import {apiUrl, type HouseLine, type Standing} from "@/domains/pinger"

/**
 * The ranked contribution board, drawn in the island's own cut shapes: the SiteCie house line set
 * apart at the top, then every contributor in rank order.
 *
 * Only ever the public identity the api prints: a Discord tag and avatar where the member linked
 * one, otherwise their site username and a monogram. Never a real or legal name.
 */
defineOptions({name: "PingerLeaderboard"})

const {house = null, rows, mineId = null} = defineProps<{
  house?: HouseLine | null
  rows: Standing[]
  /** The signed-in member's own id, so their row is marked where it sits on the board. */
  mineId?: number | null
}>()

/** The top total on the board, which every meter is read against. At least one so a lone row fills. */
const topTotal = computed<number>(() => Math.max(1, ...rows.map(row => row.totalSent)))

const shareOf = (standing: Standing): number => standing.totalSent / topTotal.value

/** The Discord tag where the member linked one, else their username. Never a legal name. */
const nameOf = (standing: Standing): string => standing.discordTag ?? standing.username ?? "—"

const avatarOf = (standing: Standing): string | null =>
  standing.avatarUrl ? apiUrl(standing.avatarUrl) : null

const initialOf = (standing: Standing): string => nameOf(standing).slice(0, 1).toUpperCase()

const formatTotal = (total: number): string => total.toLocaleString("en")
</script>

<template>
  <div
    class="board"
    data-testid="snt-board"
  >
    <!-- The house line, set apart above the ranking: it is not a member and takes no rank. -->
    <div
      v-if="house"
      class="house"
      data-testid="snt-house"
    >
      <span
        aria-hidden="true"
        class="house__mark"
      >★</span>
      <div class="house__words">
        <p class="house__label">
          {{ house.label }}
        </p>
      </div>
      <p class="house__total">
        <span class="house__total-line">{{ formatTotal(house.totalSent) }}<span class="house__unit">pings</span></span>
      </p>
    </div>

    <p
      v-if="rows.length === 0"
      class="board__empty"
      data-testid="snt-empty"
    >
      No members made it onto the board.
    </p>

    <ol
      v-else
      class="board__rows"
    >
      <li
        v-for="standing in rows"
        :key="standing.memberId"
        class="row"
        :class="{'row--mine': standing.memberId === mineId, 'row--podium': standing.rank <= 3}"
        data-testid="snt-row"
      >
        <span
          class="row__rank"
          :data-rank="standing.rank"
        >{{ standing.rank }}</span>
        <span class="row__face">
          <img
            v-if="avatarOf(standing)"
            :src="avatarOf(standing)!"
            alt=""
            draggable="false"
          >
          <span
            v-else
            class="row__initial"
          >{{ initialOf(standing) }}</span>
        </span>
        <span class="row__words">
          <span class="row__name">{{ nameOf(standing) }}</span>
          <span class="row__meter">
            <span
              class="row__fill"
              :style="{width: `${Math.round(shareOf(standing) * 100)}%`}"
            />
          </span>
        </span>
        <state-tag
          v-if="standing.memberId === mineId"
          tone="accent"
          testid="snt-row-you"
        >
          You
        </state-tag>
        <span class="row__total">
          <span class="row__total-line">{{ formatTotal(standing.totalSent) }}<span class="row__unit">pings</span></span>
        </span>
      </li>
    </ol>
  </div>
</template>

<style scoped>
.board {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

/* The house line: lit in the acid accent and cut on the lean, so it reads as the board's banner
   rather than as another row. */
.house {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 0 1.1rem;
  padding: 0.95rem calc(0.6rem + 0.9rem);
  clip-path: polygon(0.6rem 0, 100% 0, calc(100% - 0.6rem) 100%, 0 100%);
  background:
    linear-gradient(100deg, color-mix(in oklab, var(--color-acid) 22%, transparent), transparent 70%),
    var(--band-ground);
}

.house__mark {
  font-size: 1.4rem;
  line-height: 1;
  color: var(--color-acid);
}

.house__label {
  font-family: var(--font-display);
  font-size: 1.25rem;
  text-transform: uppercase;
  letter-spacing: 0.02em;
}

.house__said {
  margin-top: 0.1rem;
  font-size: 0.78rem;
  color: var(--color-ash);
}

.house__total {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  white-space: nowrap;
}

/* The body face, not the display one: its figures are tabular, so the totals line up. */
.house__total-line {
  font-family: var(--font-body);
  font-weight: 700;
  font-size: 1.5rem;
  font-variant-numeric: tabular-nums;
  font-feature-settings: "tnum" 1;
}

.house__unit,
.row__unit {
  margin-left: 0.4rem;
  font-family: var(--font-body);
  font-size: 0.62rem;
  font-weight: 600;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: var(--color-ash);
}

.board__empty {
  padding: 1.5rem 1rem;
  font-size: 0.9rem;
  color: var(--color-ash);
}

.board__rows {
  /* An ol keeps a default list margin and a 40px inline-start padding; reset both so the rows share
     the house line's left edge instead of sitting indented from it. */
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.row {
  position: relative;
  display: grid;
  grid-template-columns: 2.6rem 3rem minmax(0, 1fr) auto auto;
  align-items: center;
  gap: 0 1rem;
  padding: 0.65rem calc(0.6rem + 0.9rem);
  clip-path: polygon(0.6rem 0, 100% 0, calc(100% - 0.6rem) 100%, 0 100%);
  background-color: var(--band-ground);
  color: var(--color-chalk);
}

/* The top three carry the accent on their rank; the rest read in ash so the order is legible at
   a glance. */
.row--podium {
  background:
    linear-gradient(100deg, color-mix(in oklab, var(--color-brand) 14%, transparent), transparent 55%),
    var(--band-ground);
}

.row--mine {
  box-shadow: inset 3px 0 0 var(--color-brand);
}

.row__rank {
  font-family: var(--font-display);
  font-size: 1.5rem;
  font-variant-numeric: tabular-nums;
  font-feature-settings: "tnum" 1;
  text-align: center;
  color: var(--color-ash);
}

.row--podium .row__rank {
  color: var(--color-brand-ink);
}

/* Round, the way Discord draws its avatars, so a tag's real avatar sits right and the monogram
   fallback matches it. */
.row__face {
  display: grid;
  place-items: center;
  width: 3rem;
  height: 3rem;
  overflow: hidden;
  border-radius: 9999px;
  background: color-mix(in oklab, var(--color-chalk) 8%, transparent);
}

.row__face img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.row__initial {
  font-family: var(--font-display);
  font-size: 1.15rem;
  color: var(--color-brand-ink);
}

.row__words {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
  min-width: 0;
}

.row__name {
  overflow: hidden;
  font-family: var(--font-name);
  font-size: 1rem;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* Leaned at the island's 12°, so the meter's ends track the cut boxes rather than meeting them
   square. The fill leans with it, so its leading edge runs parallel to the row's cut edge. */
.row__meter {
  position: relative;
  width: 100%;
  max-width: 16rem;
  height: 0.5rem;
  overflow: hidden;
  background: color-mix(in oklab, var(--color-chalk) 12%, transparent);
  transform: skewX(-12deg);
}

.row__fill {
  position: absolute;
  inset: 0 auto 0 0;
  background: var(--color-brand);
}

.row--podium .row__fill {
  background: var(--color-acid);
}

.row__total {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  white-space: nowrap;
}

.row__total-line {
  font-family: var(--font-body);
  font-weight: 700;
  font-size: 1.2rem;
  font-variant-numeric: tabular-nums;
  font-feature-settings: "tnum" 1;
}

@media (max-width: 639px) {
  .house {
    grid-template-columns: auto minmax(0, 1fr) auto;
    gap: 0.35rem 0.8rem;
  }

  .house__total {
    grid-column: 2 / -1;
    text-align: left;
  }

  .row {
    grid-template-columns: 2rem 2.6rem minmax(0, 1fr) auto;
    gap: 0 0.7rem;
    padding: 0.6rem 0.9rem;
  }

  /* The meter steps back on a phone; the rank, face, name and total are the row. */
  .row__meter {
    display: none;
  }

  .row__face {
    width: 2.6rem;
    height: 2.6rem;
  }
}
</style>
