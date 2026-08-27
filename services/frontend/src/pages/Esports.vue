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
const games = [
  {name: "League of Legends", icon: $require("@/assets/league.png"), url: "/esports/league-of-legends"},
  {name: "CS2", icon: $require("@/assets/cs2.png"), url: "/esports/counter-strike-2"},
  {name: "Valorant", icon: $require("@/assets/valorant.png"), url: "/esports/valorant"},
  {name: "Rocket League", icon: $require("@/assets/rocketleague.png"), url: "/esports/rocketleague"},
  {name: "GeoGuessr", icon: $require("@/assets/geoguessrlogo.webp"), url: "/esports/geoguessr"},
]

/** Cards arrive one after another, or all at once for a visitor who asked for that. */
const entrance = (index: number) => ({
  initial: motion.decorative.value ? {opacity: 0, y: 24} : {opacity: 1},
  animate: {opacity: 1, y: 0},
  transition: {
    duration: motion.duration(0.5),
    delay: motion.decorative.value ? index * 0.06 : 0,
    ease: [0.22, 1, 0.36, 1] as const,
  },
})
</script>

<template>
  <esports-island>
    <header class="relative overflow-hidden border-b border-hairline">
      <div class="absolute inset-0 bg-[radial-gradient(circle_at_20%_-10%,rgba(51,135,250,0.32),transparent_60%)]" />
      <div class="relative mx-auto w-full max-w-5xl px-6 py-20 sm:py-28">
        <p class="font-body text-sm tracking-[0.35em] text-acid uppercase">
          Blueshell Esports
        </p>
        <h1 class="mt-4 font-display text-5xl leading-[0.95] uppercase sm:text-7xl">
          The competitive
          <span class="text-brand">scene</span>
        </h1>
        <p class="mt-6 max-w-2xl font-body text-lg text-ash">
          Facilitating esports and giving people the chance to find teammates are pillars of the
          association. Dutch student esports is thriving, and there is a competition for nearly
          every game and every level of ambition.
        </p>
      </div>
    </header>

    <section class="mx-auto w-full max-w-5xl px-6 py-16">
      <h2 class="font-display text-2xl uppercase">
        Pick a game
      </h2>
      <p class="mt-2 font-body text-ash">
        Every game the association fields a team in, and the ones it used to.
      </p>

      <ul
        class="mt-10 grid grid-cols-2 gap-5 sm:grid-cols-3"
        data-testid="esports-game-grid"
      >
        <Motion
          v-for="(game, index) in games"
          :key="game.url"
          as="li"
          v-bind="entrance(index)"
        >
          <router-link
            class="group relative flex aspect-4/3 items-center justify-center overflow-hidden rounded-xl border border-hairline bg-surface transition-colors duration-300 hover:border-brand focus-visible:border-brand"
            :data-testid="`esports-game-${game.name.toLowerCase().replace(/[^a-z0-9]+/g, '-')}`"
            :to="game.url"
          >
            <img
              :alt="game.name"
              class="h-3/5 w-3/5 object-contain transition-transform duration-500 ease-out-quint motion-safe:group-hover:scale-110"
              :src="game.icon"
            >
            <span
              class="absolute inset-x-0 bottom-0 bg-gradient-to-t from-void/95 to-transparent px-4 pt-8 pb-3 font-display text-sm uppercase tracking-wide text-chalk"
            >{{ game.name }}</span>
          </router-link>
        </Motion>
      </ul>
    </section>

    <section class="mx-auto w-full max-w-5xl px-6 pb-24">
      <div class="rounded-xl border border-hairline bg-pit p-8">
        <h2 class="font-display text-xl uppercase">
          Want in?
        </h2>
        <p class="mt-3 max-w-2xl font-body text-ash">
          Talk to esports affairs on Discord, or mail
          <a
            class="text-brand underline decoration-brand/40 underline-offset-4 transition-colors hover:text-brand-lit hover:decoration-brand-lit"
            href="mailto:esports@blueshell.utwente.nl"
          >esports@blueshell.utwente.nl</a>.
        </p>
      </div>
    </section>
  </esports-island>
</template>
