<script lang="ts" setup>
import {onBeforeUnmount, onMounted, ref, watch} from "vue"
import {$require} from "@/plugins/require"
import {useMotionAllowed} from "./useMotionAllowed"
import type {TeamRoster} from "../adapters/esports"

defineOptions({name: "TeamSlices"})

const props = defineProps<{teams: TeamRoster[]; accent: string}>()

const motion = useMotionAllowed()

/**
 * Which slice is open. Nothing is open for the first frame so the opening of the first one is
 * something the visitor sees happen rather than something already done — except under reduced
 * motion, where it is simply open from the start.
 */
const open = ref<number | null>(null)
const slices = ref<HTMLElement[]>([])

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
    const index = slices.value.indexOf(best.target as HTMLElement)
    if (index >= 0) open.value = index
  }, {rootMargin: "-42% 0px -42% 0px", threshold: [0, 0.25, 0.5, 1]})
  slices.value.forEach(el => el && watcher?.observe(el))
}

const settle = () => {
  open.value = 0
}

onMounted(() => {
  if (!motion.decorative.value) {
    settle()
  } else {
    requestAnimationFrame(() => requestAnimationFrame(settle))
  }
  watchScroll()
})

onBeforeUnmount(() => watcher?.disconnect())

// A season change brings a different set of teams, so the first of those opens in its turn.
watch(() => props.teams, () => {
  slices.value = []
  open.value = motion.decorative.value ? null : 0
  if (motion.decorative.value) requestAnimationFrame(() => requestAnimationFrame(settle))
  requestAnimationFrame(watchScroll)
})

const GROUPS = [
  {role: "PLAYER", one: "Player", many: "Players"},
  {role: "SUBSTITUTE", one: "Substitute", many: "Substitutes"},
  {role: "COACH", one: "Coach", many: "Coaches"},
] as const

const rosterOf = (team: TeamRoster) =>
  GROUPS.map(group => ({...group, members: team.members.filter(m => m.role === group.role)}))
    .filter(group => group.members.length > 0)

/** A team's own banner, where it has one. An empty string simply leaves the accent showing. */
const bannerOf = (team: TeamRoster) => (team.image ? $require(`@/assets/${team.image}`) : "")
</script>

<template>
  <div
    class="team-slices"
    data-testid="esports-team-slices"
    :style="{'--accent': accent}"
    @mouseleave="open = 0"
  >
    <section
      v-for="(team, index) in teams"
      :key="team.id"
      :ref="el => { if (el) slices[index] = el as HTMLElement }"
      class="team-slice"
      :class="{
        'team-slice--open': index === open,
        'team-slice--first': index === 0,
        'team-slice--last': index === teams.length - 1,
      }"
      :data-testid="`team-roster-${team.id}`"
      @focusin="open = index"
      @mouseenter="open = index"
    >
      <img
        v-if="bannerOf(team)"
        alt=""
        class="team-slice__banner"
        :src="bannerOf(team)"
      >
      <span
        aria-hidden="true"
        class="team-slice__scrim"
      />

      <button
        class="team-slice__body"
        :aria-expanded="index === open"
        type="button"
        @click="open = index"
      >
        <span class="team-slice__heading">
          <span
            aria-hidden="true"
            class="team-slice__tick"
          />
          <span class="team-slice__name">{{ team.name }}</span>
          <span class="team-slice__count">{{ team.members.length }} on the roster</span>
        </span>

        <span class="team-slice__roster">
          <span
            v-for="group in rosterOf(team)"
            :key="group.role"
            class="team-slice__group"
          >
            <span class="team-slice__group-label">
              {{ group.members.length === 1 ? group.one : group.many }}
            </span>
            <span class="team-slice__members">
              <span
                v-for="member in group.members"
                :key="member.handle"
                class="team-slice__member"
              >
                <span class="team-slice__handle">{{ member.handle }}</span>
                <!-- Only ever present for a member who said their name may be shown. -->
                <span
                  v-if="member.name"
                  class="team-slice__member-name"
                >{{ member.name }}</span>
              </span>
            </span>
          </span>
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
  transition: flex-grow 620ms cubic-bezier(0.22, 1, 0.36, 1);
}

.team-slice--first {
  clip-path: polygon(0 0, 100% 0, calc(100% - var(--cut)) 100%, 0 100%);
  margin-left: 0;
}

.team-slice--last {
  clip-path: polygon(var(--cut) 0, 100% 0, 100% 100%, 0 100%);
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
  filter: grayscale(70%) brightness(0.5);
  scale: 1.06;
  transition: filter 620ms ease, scale 900ms cubic-bezier(0.22, 1, 0.36, 1);
}

.team-slice--open .team-slice__banner {
  filter: grayscale(0%) brightness(0.72);
  scale: 1;
}

.team-slice__scrim {
  position: absolute;
  inset: 0;
  background:
    linear-gradient(to top, color-mix(in oklab, var(--color-void) 92%, transparent) 0%, transparent 62%),
    linear-gradient(to right, color-mix(in oklab, var(--color-void) 55%, transparent), transparent 55%);
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

.team-slice__group-label {
  display: block;
  font-size: 0.6rem;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: var(--color-ash);
}

.team-slice__members {
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
.team-slice__member {
  display: flex;
  min-width: 0;
  flex-direction: column;
  line-height: 1.15;
}

.team-slice__handle {
  font-size: 0.95rem;
  color: var(--color-chalk);
}

.team-slice__member-name {
  font-size: 0.7rem;
  letter-spacing: 0.01em;
  color: color-mix(in oklab, var(--color-ash) 85%, transparent);
}

/* Stacked on a narrow screen, where a row of slices would leave each one a sliver. The cut
   turns with them so the seams still read as diagonal. */
@media (max-width: 767px) {
  .team-slices {
    --cut: 22px;

    flex-direction: column;
    min-height: 0;
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

@media (prefers-reduced-motion: reduce) {
  .team-slice,
  .team-slice__banner,
  .team-slice__tick,
  .team-slice__roster {
    transition: none;
  }
}
</style>
