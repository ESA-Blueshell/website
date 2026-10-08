<script lang="ts" setup>
import {computed, onBeforeUnmount, onMounted, ref, watch} from "vue"
import StateTag from "@/components/island/StateTag.vue"
import {apiUrl, type HouseLine, type Standing} from "@/domains/pinger"

/**
 * The ranked contribution board, drawn in the island's own cut shapes: the SiteCie house line set
 * apart at the top, then every contributor in rank order. A reorder moves the rows in place rather
 * than redrawing the list, so a member overtaking another is something a watcher sees.
 *
 * Each total climbs continuously between the stream's snapshots, interpolated from the row's own
 * rate so the numbers tick as fast as the canvas pixel counter, and reconciled to the real value on
 * every push so they never drift. A reader who asked for less motion gets the pushed value, snapped.
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

const reduceMotion = typeof matchMedia === "function" && matchMedia("(prefers-reduced-motion: reduce)").matches

/** The key the house line's interpolation base is held under; no member id is negative. */
const HOUSE_KEY = -1

interface Base {total: number, at: number, pps: number}

const now = (): number => (typeof performance !== "undefined" ? performance.now() : Date.now())

// Each row's last pushed total, when it arrived and its rate, so the display can climb from there.
const bases = new Map<number, Base>()
const frameNow = ref(now())

const rebase = (key: number, total: number, pps: number): void => {
  bases.set(key, {total, at: now(), pps})
}

// On every snapshot, reconcile each base to the real value, so interpolation never drifts.
watch(() => rows, (next) => next.forEach(row => rebase(row.memberId, row.totalSent, row.pps)), {immediate: true, deep: true})
watch(() => house, (next) => {
  if (next) rebase(HOUSE_KEY, next.totalSent, next.pps)
}, {immediate: true, deep: true})

/** The interpolated total for a key, climbing at its rate since the last push; snapped under reduced motion. */
const liveTotal = (key: number, fallback: number): number => {
  const base = bases.get(key)
  if (!base) return fallback
  if (reduceMotion) return base.total
  return base.total + Math.max(0, base.pps) * Math.max(0, frameNow.value - base.at) / 1000
}

const displayTotal = (standing: Standing): number => Math.floor(liveTotal(standing.memberId, standing.totalSent))
const houseTotal = computed<number>(() => (house ? Math.floor(liveTotal(HOUSE_KEY, house.totalSent)) : 0))

/** The top total on the board, which every meter is read against. At least one so a lone row fills. */
const topTotal = computed<number>(() => Math.max(1, ...rows.map(displayTotal)))

const shareOf = (standing: Standing): number => displayTotal(standing) / topTotal.value

/** The Discord tag where the member linked one, else their username. Never a legal name. */
const nameOf = (standing: Standing): string => standing.discordTag ?? standing.username ?? "—"

const avatarOf = (standing: Standing): string | null =>
  standing.avatarUrl ? apiUrl(standing.avatarUrl) : null

const initialOf = (standing: Standing): string => nameOf(standing).slice(0, 1).toUpperCase()

const formatTotal = (total: number): string => total.toLocaleString("en")
const formatPps = (pps: number): string => pps.toLocaleString("en")

// One clock drives every row's climb; it ticks each frame unless the reader asked for less motion.
let frame = 0
onMounted(() => {
  if (reduceMotion) return
  const step = (): void => {
    frame = requestAnimationFrame(step)
    frameNow.value = now()
  }
  frame = requestAnimationFrame(step)
})
onBeforeUnmount(() => cancelAnimationFrame(frame))
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
      <state-tag
        :tone="house.online ? 'ok' : 'quiet'"
        testid="snt-house-state"
      >
        {{ house.online ? "Painting" : "Idle" }}
      </state-tag>
      <p class="house__total">
        <span class="house__total-line">{{ formatTotal(houseTotal) }}<span class="house__unit">pings</span></span>
        <span
          v-if="house.online && house.pps > 0"
          class="rate"
          data-testid="snt-house-rate"
        >{{ formatPps(house.pps) }}/s now</span>
      </p>
    </div>

    <p
      v-if="rows.length === 0"
      class="board__empty"
      data-testid="snt-empty"
    >
      No members are on the board yet. Contribute from the pinger app to be the first.
    </p>

    <transition-group
      v-else
      name="row"
      tag="ol"
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
        <span
          class="row__dot"
          :class="{'row__dot--on': standing.online}"
          :title="standing.online ? 'Online' : 'Offline'"
        />
        <span class="row__total">
          <span class="row__total-line">{{ formatTotal(displayTotal(standing)) }}<span class="row__unit">pings</span></span>
          <span
            v-if="standing.online && standing.pps > 0"
            class="rate"
            data-testid="snt-row-rate"
          >{{ formatPps(standing.pps) }}/s now</span>
        </span>
      </li>
    </transition-group>
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
  grid-template-columns: auto minmax(0, 1fr) auto auto;
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

/* The body face, not the display one: its figures are tabular, so a ticking total holds its width. */
.house__total-line {
  font-family: var(--font-body);
  font-weight: 700;
  font-size: 1.5rem;
  font-variant-numeric: tabular-nums;
  font-feature-settings: "tnum" 1;
}

/* The live rate right now, acid so it reads as the thing that is happening this second. */
.rate {
  margin-top: 0.15rem;
  font-family: var(--font-body);
  font-size: 0.68rem;
  font-weight: 600;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  font-variant-numeric: tabular-nums;
  font-feature-settings: "tnum" 1;
  color: var(--color-ok);
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
  grid-template-columns: 2.6rem 3rem minmax(0, 1fr) auto auto auto;
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

.row__dot {
  width: 9px;
  height: 9px;
  border-radius: 9999px;
  background: var(--color-ash);
  opacity: 0.5;
}

.row__dot--on {
  background: var(--color-ok);
  opacity: 1;
  box-shadow: 0 0 0 3px color-mix(in oklab, var(--color-ok) 22%, transparent);
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

/* The reorder: the row slides to its new place rather than jumping, which is the whole point of a
   board that moves on its own. A reader who asked for less motion gets the new order at rest. */
.row-move {
  transition: transform 0.45s var(--ease-out-quint);
}

@media (prefers-reduced-motion: reduce) {
  .row-move {
    transition: none;
  }
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

  /* The meter and the online dot step back on a phone; the rank, face, name and total are the row. */
  .row__meter,
  .row__dot {
    display: none;
  }

  .row__face {
    width: 2.6rem;
    height: 2.6rem;
  }
}
</style>
