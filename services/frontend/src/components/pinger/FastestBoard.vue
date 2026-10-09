<script lang="ts" setup>
import StateTag from "@/components/island/StateTag.vue"
import {apiUrl, setAt, type FastestStanding, type HouseLine} from "@/domains/pinger"

/**
 * The fastest board: every member ranked by the top rate they reached across their devices, and
 * when, under SiteCie's own top rate set apart the way the total board sets its house line apart.
 *
 * Only ever the public identity the api prints: a Discord tag and avatar where the member linked
 * one, otherwise their site username and a monogram. Never a real or legal name.
 */
defineOptions({name: "FastestBoard"})

const {house = null, rows, mineId = null} = defineProps<{
  house?: HouseLine | null
  rows: FastestStanding[]
  /** The signed-in member's own id, so their row is marked where it sits on the board. */
  mineId?: number | null
}>()

const nameOf = (standing: FastestStanding): string => standing.discordTag ?? standing.username ?? "—"

const avatarOf = (standing: FastestStanding): string | null =>
  standing.avatarUrl ? apiUrl(standing.avatarUrl) : null

const initialOf = (standing: FastestStanding): string => nameOf(standing).slice(0, 1).toUpperCase()

const formatPps = (pps: number): string => pps.toLocaleString("en")
</script>

<template>
  <div
    class="fastest"
    data-testid="snt-fastest"
  >
    <div
      v-if="house && house.peakPps > 0"
      class="house"
      data-testid="snt-fastest-house"
    >
      <span
        aria-hidden="true"
        class="house__mark"
      >★</span>
      <p class="house__label">
        {{ house.label }}
      </p>
      <p class="peak">
        <span class="peak__line">{{ formatPps(house.peakPps) }}<span class="peak__unit">/s</span></span>
        <span
          v-if="house.peakAt"
          class="peak__when"
        >set {{ setAt(house.peakAt) }}</span>
      </p>
    </div>

    <p
      v-if="rows.length === 0"
      class="fastest__empty"
      data-testid="snt-fastest-empty"
    >
      No member has a top rate yet. Send from the pinger app to set one.
    </p>

    <transition-group
      v-else
      name="row"
      tag="ol"
      class="fastest__rows"
    >
      <li
        v-for="standing in rows"
        :key="standing.memberId"
        class="row"
        :class="{'row--mine': standing.memberId === mineId, 'row--podium': standing.rank <= 3}"
        data-testid="snt-fastest-row"
      >
        <span class="row__rank">{{ standing.rank }}</span>
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
        <span class="row__name">{{ nameOf(standing) }}</span>
        <state-tag
          v-if="standing.memberId === mineId"
          tone="accent"
          testid="snt-fastest-you"
        >
          You
        </state-tag>
        <span class="peak">
          <span class="peak__line">{{ formatPps(standing.peakPps) }}<span class="peak__unit">/s</span></span>
          <span
            v-if="standing.peakAt"
            class="peak__when"
          >set {{ setAt(standing.peakAt) }}</span>
        </span>
      </li>
    </transition-group>
  </div>
</template>

<style scoped>
.fastest {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

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

/* The body face, not the display one: its figures are tabular, so a rate holds its width. */
.peak {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  white-space: nowrap;
}

.peak__line {
  font-family: var(--font-body);
  font-weight: 700;
  font-size: 1.2rem;
  font-variant-numeric: tabular-nums;
  font-feature-settings: "tnum" 1;
}

.peak__unit {
  margin-left: 0.2rem;
  font-size: 0.7rem;
  font-weight: 600;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--color-ash);
}

.peak__when {
  margin-top: 0.15rem;
  font-family: var(--font-body);
  font-size: 0.68rem;
  font-weight: 600;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  font-variant-numeric: tabular-nums;
  color: var(--color-ash);
}

.fastest__empty {
  padding: 1.5rem 1rem;
  font-size: 0.9rem;
  color: var(--color-ash);
}

.fastest__rows {
  margin: 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

.row {
  display: grid;
  grid-template-columns: 2.2rem 2.6rem minmax(0, 1fr) auto auto;
  align-items: center;
  gap: 0 0.9rem;
  padding: 0.6rem calc(0.6rem + 0.9rem);
  clip-path: polygon(0.6rem 0, 100% 0, calc(100% - 0.6rem) 100%, 0 100%);
  background-color: var(--band-ground);
  color: var(--color-chalk);
}

.row--podium {
  background:
    linear-gradient(100deg, color-mix(in oklab, var(--color-acid) 14%, transparent), transparent 55%),
    var(--band-ground);
}

.row--mine {
  box-shadow: inset 3px 0 0 var(--color-brand);
}

.row__rank {
  font-family: var(--font-display);
  font-size: 1.3rem;
  font-variant-numeric: tabular-nums;
  text-align: center;
  color: var(--color-ash);
}

.row--podium .row__rank {
  color: var(--color-acid);
}

.row__face {
  display: grid;
  place-items: center;
  width: 2.6rem;
  height: 2.6rem;
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
  font-size: 1.05rem;
  color: var(--color-brand-ink);
}

.row__name {
  overflow: hidden;
  font-family: var(--font-name);
  font-size: 1rem;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.row-move {
  transition: transform 0.45s var(--ease-out-quint);
}

@media (prefers-reduced-motion: reduce) {
  .row-move {
    transition: none;
  }
}

@media (max-width: 639px) {
  .row {
    grid-template-columns: 1.8rem 2.2rem minmax(0, 1fr) auto;
    gap: 0 0.6rem;
    padding: 0.55rem 0.9rem;
  }

  .row__face {
    width: 2.2rem;
    height: 2.2rem;
  }
}
</style>
