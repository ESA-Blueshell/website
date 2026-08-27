<script lang="ts" setup>
import {Motion} from "motion-v"
import EsportsIsland from "@/domains/esports/island/EsportsIsland.vue"
import {useMotionAllowed} from "@/domains/esports/island/useMotionAllowed"
import {$require} from "@/plugins/require.js"

defineOptions({name: "EsportsPage"})

const motion = useMotionAllowed()

// The games and their addresses still live here. Which games exist, in what
// order, and what is said about each becomes a record in its own right; until
// then this list is what the router already answers to.
//
// The art is a freely licensed photograph per game rather than the game's own
// key art, which is its publisher's. Credits sit beside the files.
const games = [
  {
    name: "League of Legends",
    art: $require("@/assets/games/league-of-legends.webp"),
    url: "/esports/league-of-legends",
  },
  {name: "CS2", art: $require("@/assets/games/cs2.webp"), url: "/esports/counter-strike-2"},
  {name: "Valorant", art: $require("@/assets/games/valorant.webp"), url: "/esports/valorant"},
  {
    name: "Rocket League",
    art: $require("@/assets/games/rocket-league.webp"),
    url: "/esports/rocketleague",
  },
  {name: "GeoGuessr", art: $require("@/assets/games/geoguessr.webp"), url: "/esports/geoguessr"},
]

/** Tiles arrive one after another, or all at once for a visitor who asked for that. */
const entrance = (index: number) => ({
  initial: motion.decorative.value ? {opacity: 0, y: 16} : {opacity: 1},
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
    v-main belongs to the shell, not to the island: it is what clears the fixed
    app bar, and it knows the bar's height at every breakpoint. The island starts
    inside it. When a page deliberately runs its hero under the bar, this is what
    comes off.
  -->
  <v-main>
    <esports-island>
      <header class="relative overflow-hidden border-b border-hairline">
        <div class="absolute inset-0 bg-[radial-gradient(circle_at_15%_-20%,rgba(51,135,250,0.16),transparent_55%)]" />
        <div class="relative mx-auto w-full max-w-5xl px-5 py-10 sm:px-6 sm:py-14">
          <p class="font-body text-[11px] tracking-[0.22em] text-acid uppercase sm:text-xs">
            Blueshell Esports
          </p>
          <h1 class="mt-2 font-display text-2xl leading-tight uppercase sm:text-4xl">
            The competitive
            <span class="text-brand">scene</span>
          </h1>
          <p class="mt-3 max-w-2xl font-body text-sm text-ash sm:text-base">
            Facilitating esports and giving people the chance to find teammates are pillars of the
            association. Dutch student esports is thriving, and there is a competition for nearly
            every game and every level of ambition.
          </p>
        </div>
      </header>

      <section class="mx-auto w-full max-w-5xl px-5 py-8 sm:px-6 sm:py-10">
        <div class="flex flex-wrap items-baseline justify-between gap-x-4 gap-y-1">
          <h2 class="font-display text-base uppercase sm:text-lg">
            Pick a game
          </h2>
          <p class="font-body text-xs text-ash sm:text-sm">
            Every game the association fields a team in, and the ones it used to.
          </p>
        </div>

        <ul
          class="mt-4 grid grid-cols-1 gap-3 sm:mt-5 sm:grid-cols-2 lg:grid-cols-3"
          data-testid="esports-game-grid"
        >
          <Motion
            v-for="(game, index) in games"
            :key="game.url"
            as="li"
            v-bind="entrance(index)"
          >
            <router-link
              class="group relative flex aspect-16/9 items-end overflow-hidden rounded-lg border border-hairline bg-surface transition-colors duration-300 hover:border-brand focus-visible:border-brand"
              :data-testid="`esports-game-${game.name.toLowerCase().replace(/[^a-z0-9]+/g, '-')}`"
              :to="game.url"
            >
              <!-- The art is decoration behind a label, so it takes no alt text of
                   its own; the link is named by the heading over it. -->
              <img
                alt=""
                class="absolute inset-0 h-full w-full object-cover opacity-55 transition duration-500 ease-out-quint group-hover:opacity-80 motion-safe:group-hover:scale-[1.04]"
                :src="game.art"
              >
              <span class="absolute inset-0 bg-gradient-to-t from-void via-void/55 to-transparent" />
              <h3 class="relative px-3 pt-6 pb-2.5 font-display text-sm uppercase sm:text-base">
                {{ game.name }}
              </h3>
            </router-link>
          </Motion>
        </ul>
      </section>

      <section class="mx-auto w-full max-w-5xl px-5 pb-10 sm:px-6 sm:pb-14">
        <div class="rounded-lg border border-hairline bg-pit px-4 py-4 sm:px-5 sm:py-5">
          <h2 class="font-display text-sm uppercase sm:text-base">
            Want in?
          </h2>
          <p class="mt-1.5 max-w-2xl font-body text-sm text-ash">
            Talk to esports affairs on Discord, or mail
            <a
              class="text-brand underline decoration-brand/40 underline-offset-4 transition-colors hover:text-brand-lit hover:decoration-brand-lit"
              href="mailto:esports@blueshell.utwente.nl"
            >esports@blueshell.utwente.nl</a>.
          </p>
        </div>
      </section>
    </esports-island>
  </v-main>
</template>
