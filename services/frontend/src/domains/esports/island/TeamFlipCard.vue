<script lang="ts" setup>
import {computed, ref} from "vue"
import {useMotionAllowed} from "./useMotionAllowed"
import type {TeamRoster} from "../adapters/esports"

defineOptions({name: "TeamFlipCard"})

const props = defineProps<{team: TeamRoster; accent: string}>()

const motion = useMotionAllowed()
const pinned = ref(false)

/** The roster as the pages have always read it: players, then substitutes, then coaches. */
const GROUPS = [
  {role: "PLAYER", one: "Player", many: "Players"},
  {role: "SUBSTITUTE", one: "Substitute", many: "Substitutes"},
  {role: "COACH", one: "Coach", many: "Coaches"},
] as const

const groups = computed(() =>
  GROUPS.map(group => ({...group, members: props.team.members.filter(m => m.role === group.role)}))
    .filter(group => group.members.length > 0),
)

const headcount = computed(() => props.team.members.length)

/**
 * A tap has no hover to give, so it pins the card over instead. A pointer needs no help, and
 * a visitor who asked for less motion gets the roster without the card turning at all.
 */
const flipped = computed(() => pinned.value)
const turns = computed(() => motion.decorative.value)
</script>

<template>
  <div
    class="team-card group relative"
    :class="{'team-card--flat': !turns, 'team-card--flipped': flipped}"
    :data-testid="`team-roster-${team.id}`"
    :style="{'--accent': accent}"
  >
    <button
      class="team-card__inner relative block w-full cursor-pointer text-left"
      :aria-expanded="flipped"
      :aria-label="`${team.name} roster`"
      type="button"
      @click="pinned = !pinned"
    >
      <!-- Front: who they are. -->
      <span class="team-card__face team-card__face--front flex flex-col justify-between rounded-2xl bg-surface p-5">
        <span class="flex items-start justify-between gap-3">
          <span class="font-display text-lg leading-tight uppercase sm:text-xl">{{ team.name }}</span>
          <span
            aria-hidden="true"
            class="mt-1 h-2 w-2 shrink-0 rounded-full"
            :style="{backgroundColor: 'var(--accent)'}"
          />
        </span>
        <span class="mt-6 flex items-end justify-between gap-3">
          <span class="font-body text-xs text-ash">
            {{ headcount }} on the roster
          </span>
          <span class="font-body text-[10px] tracking-[0.16em] text-ash/45 uppercase">
            {{ turns ? "Hover" : "Open" }}
          </span>
        </span>
      </span>

      <!-- Back: who played. -->
      <span class="team-card__face team-card__face--back flex flex-col rounded-2xl bg-raised p-5">
        <span
          class="font-display text-sm uppercase"
          :style="{color: 'var(--accent)'}"
        >{{ team.name }}</span>
        <span class="mt-3 flex flex-col gap-2.5 overflow-hidden">
          <span
            v-for="group in groups"
            :key="group.role"
            class="block"
          >
            <span class="font-body text-[10px] tracking-[0.18em] text-ash uppercase">
              {{ group.members.length === 1 ? group.one : group.many }}
            </span>
            <span class="mt-0.5 flex flex-wrap gap-x-3 gap-y-0.5">
              <span
                v-for="member in group.members"
                :key="member.handle"
                class="team-card__member font-body text-sm text-chalk"
              >
                {{ member.handle }}
                <!-- Only ever present for a member who said their name may be shown. -->
                <span
                  v-if="member.name"
                  class="team-card__member-name font-body text-xs text-ash"
                >{{ member.name }}</span>
              </span>
            </span>
          </span>
        </span>
      </span>
    </button>
  </div>
</template>

<style scoped>
/* The handle leads and the name follows it, quieter: a handle is what a team is known by. */
.team-card__member-name {
  margin-left: 0.35rem;
}

.team-card {
  perspective: 1200px;
}

.team-card__inner {
  min-height: 9.5rem;
  transform-style: preserve-3d;
  transition: transform 620ms cubic-bezier(0.22, 1, 0.36, 1);
}

.team-card:hover .team-card__inner,
.team-card__inner:focus-visible,
.team-card--flipped .team-card__inner {
  transform: rotateY(180deg);
}

.team-card__face {
  position: absolute;
  inset: 0;
  backface-visibility: hidden;
  overflow: hidden;
}

/* Both faces fill the card. A front sized to its own content leaves the rest of the
   card showing through behind it. */
.team-card__face--front {
  position: absolute;
}

.team-card__face--back {
  transform: rotateY(180deg);
}

/*
 * Without the turn there is no back to hide behind, so the card stops being two faces and
 * becomes one: the roster sits under the name, always readable. Nothing is lost by not
 * hovering, which is the point.
 */
.team-card--flat .team-card__inner,
.team-card--flat:hover .team-card__inner {
  transform: none;
  transition: none;
}

.team-card--flat .team-card__face {
  position: relative;
  backface-visibility: visible;
  inset: auto;
}

.team-card--flat .team-card__face--back {
  transform: none;
  margin-top: 0.5rem;
}
</style>
