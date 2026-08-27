<script lang="ts" setup>
import {Motion} from "motion-v"
import EsportsIsland from "@/domains/esports/island/EsportsIsland.vue"
import {useMotionAllowed} from "@/domains/esports/island/useMotionAllowed"
import {$require} from "@/plugins/require.js"

defineOptions({name: "EsportsPage"})

const motion = useMotionAllowed()

/**
 * The games and their addresses still live here. Which games exist, in what
 * order and what is said about each becomes a record in its own right; until
 * then this is what the router already answers to.
 *
 * Each carries its own accent rather than a photograph. Freely licensed
 * photography of these games barely exists — what there is documents somebody
 * else's tournament, and five of them together look like a clip-art drawer. A
 * mark on its own colour is recognisable at a glance and consistent across the
 * grid.
 */
const games = [
  {
    name: "League of Legends",
    blurb: "Five a side, on the rift",
    mark: $require("@/assets/league.png"),
    accent: "var(--color-game-league)",
    url: "/esports/league-of-legends",
  },
  {
    name: "CS2",
    blurb: "Tactical, and unforgiving",
    mark: $require("@/assets/cs2.png"),
    accent: "var(--color-game-cs2)",
    url: "/esports/counter-strike-2",
  },
  {
    name: "Valorant",
    blurb: "Aim, plus everything else",
    mark: $require("@/assets/valorant.png"),
    accent: "var(--color-game-valorant)",
    url: "/esports/valorant",
  },
  {
    name: "Rocket League",
    blurb: "Football, with rocket cars",
    mark: $require("@/assets/rocketleague.png"),
    accent: "var(--color-game-rocket)",
    url: "/esports/rocketleague",
  },
  {
    name: "GeoGuessr",
    blurb: "Somewhere on a road, guessing",
    mark: $require("@/assets/geoguessrlogo.webp"),
    accent: "var(--color-game-geoguessr)",
    url: "/esports/geoguessr",
  },
]

/** Tiles arrive one after another, or all at once for a visitor who asked for that. */
const entrance = (index: number) => ({
  initial: motion.decorative.value ? {opacity: 0, y: 14} : {opacity: 1},
  animate: {opacity: 1, y: 0},
  transition: {
    duration: motion.duration(0.4),
    delay: motion.decorative.value ? index * 0.05 : 0,
    ease: [0.22, 1, 0.36, 1] as const,
  },
})
</script>

<template>
  <!--
    v-main belongs to the shell, not the island: it is what clears the fixed app
    bar and it knows the bar's height at every breakpoint.
  -->
  <v-main>
    <esports-island>
      <header class="relative isolate overflow-hidden">
        <!-- Depth without a frame: two soft washes of the association's colours
             rather than a rule across the page. -->
        <div
          aria-hidden="true"
          class="pointer-events-none absolute -top-32 -left-24 h-80 w-[36rem] rounded-full bg-brand/18 blur-[90px]"
        />
        <div
          aria-hidden="true"
          class="pointer-events-none absolute -top-24 right-0 h-64 w-96 rounded-full bg-acid/8 blur-[100px]"
        />

        <div class="relative mx-auto w-full max-w-6xl px-5 pt-12 pb-10 sm:px-8 sm:pt-16 sm:pb-14">
          <p class="font-body text-[11px] font-medium tracking-[0.3em] text-acid uppercase">
            Blueshell Esports
          </p>
          <h1 class="mt-2.5 max-w-2xl font-display text-2xl leading-[1.1] uppercase sm:text-4xl">
            Teams in five games,<br>
            <span class="text-brand">and a league for every level</span>
          </h1>
          <p class="mt-3 max-w-lg font-body text-sm leading-relaxed text-ash">
            Tryouts every season, room in more teams than people expect, and a room full of
            people who will tell you exactly what you did wrong afterwards.
          </p>
        </div>
      </header>

      <section class="mx-auto w-full max-w-6xl px-5 pb-6 sm:px-8">
        <h2 class="font-display text-xs tracking-[0.2em] text-ash uppercase">
          Our games
        </h2>

        <ul
          class="mt-4 grid grid-cols-2 gap-2.5 sm:gap-3 lg:grid-cols-4"
          data-testid="esports-game-grid"
        >
          <Motion
            v-for="(game, index) in games"
            :key="game.url"
            as="li"
            :class="index === 0 ? 'col-span-2 lg:row-span-2' : ''"
            v-bind="entrance(index)"
          >
            <router-link
              class="group relative flex h-full min-h-[8.5rem] flex-col justify-end overflow-hidden rounded-2xl bg-surface p-4 transition-[transform,background-color] duration-300 ease-out-quint hover:bg-raised motion-safe:hover:-translate-y-0.5"
              :class="index === 0 ? 'min-h-[11rem] lg:min-h-[17.5rem]' : ''"
              :data-testid="`esports-game-${game.name.toLowerCase().replace(/[^a-z0-9]+/g, '-')}`"
              :style="{'--accent': game.accent}"
              :to="game.url"
            >
              <!-- The accent is the identity: a wash behind the mark, and a bar
                   that grows along the bottom as the tile is approached. -->
              <span
                aria-hidden="true"
                class="pointer-events-none absolute -top-14 -right-8 h-44 w-44 rounded-full opacity-40 blur-[42px] transition-opacity duration-500 group-hover:opacity-70"
                :style="{backgroundColor: 'var(--accent)'}"
              />
              <img
                alt=""
                class="relative h-9 w-9 shrink-0 object-contain transition-transform duration-500 ease-out-quint motion-safe:group-hover:scale-110"
                :class="index === 0 ? 'mx-auto my-auto h-20 w-20 sm:h-28 sm:w-28' : 'mb-auto'"
                :src="game.mark"
              >
              <h3
                class="relative mt-4 font-display text-sm leading-tight uppercase sm:text-base"
                :class="index === 0 ? 'sm:text-xl' : ''"
              >
                {{ game.name }}
              </h3>
              <p class="relative mt-0.5 font-body text-xs text-ash">
                {{ game.blurb }}
              </p>
              <span
                aria-hidden="true"
                class="pointer-events-none absolute inset-x-0 bottom-0 h-[3px] origin-left scale-x-0 transition-transform duration-500 ease-out-quint group-hover:scale-x-100 group-focus-visible:scale-x-100"
                :style="{backgroundColor: 'var(--accent)'}"
              />
            </router-link>
          </Motion>
        </ul>
      </section>

      <section class="mx-auto w-full max-w-6xl px-5 pt-8 pb-14 sm:px-8 sm:pt-10 sm:pb-20">
        <div class="relative isolate overflow-hidden rounded-2xl bg-pit px-5 py-6 sm:px-8 sm:py-8">
          <div
            aria-hidden="true"
            class="pointer-events-none absolute -right-16 -bottom-20 h-56 w-56 rounded-full bg-brand/20 blur-[70px]"
          />
          <h2 class="relative font-display text-base uppercase sm:text-xl">
            Want in?
          </h2>
          <p class="relative mt-2 max-w-xl font-body text-sm text-ash">
            Tryouts run at the start of every season, and there is room in more teams than
            people expect. Ask esports affairs on Discord, or mail
            <a
              class="text-brand transition-colors hover:text-brand-lit"
              href="mailto:esports@blueshell.utwente.nl"
            >esports@blueshell.utwente.nl</a>.
          </p>
        </div>
      </section>
    </esports-island>
  </v-main>
</template>
