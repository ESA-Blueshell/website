<script lang="ts" setup>
import {computed} from "vue"
import {useRoute, useRouter} from "vue-router"
import TopBanner from "@/components/common/banners/TopBanner.vue"
import TeamRoster from "./TeamRoster.vue"
import {useEsportsPage} from "../composables/useEsportsPage"
import type {Game} from "../adapters/esports"

defineOptions({name: "EsportsGamePage"})

const props = defineProps<{
  game: Game
  title: string
}>()

const route = useRoute()
const router = useRouter()

const seasonFromRoute = () => {
  const raw = route.query.season
  const value = Number(Array.isArray(raw) ? raw[0] : raw)
  return Number.isFinite(value) && value > 0 ? value : null
}

// The season lives in the url, so a roster can be linked to and the back button works.
const rememberSeason = (id: number) => {
  void router.replace({query: {...route.query, season: String(id)}})
}

const {loading, teams, seasons, season, hasRosters, showSeason} = useEsportsPage(
  props.game,
  seasonFromRoute,
  rememberSeason,
)

const currentSeasonId = computed<number | null>(() => season.value?.id ?? null)
</script>

<template>
  <v-main>
    <top-banner :title="title" />

    <div class="mx-3">
      <div
        class="mx-auto my-10 esports-intro"
        style="max-width: 800px"
      >
        <slot name="intro" />
      </div>
    </div>

    <!-- Which season is on show, and the ones that can be. Hidden when there is only ever
         been one, since a switcher offering a single choice is furniture. -->
    <v-container
      v-if="seasons.length > 1"
      class="season-bar"
      data-testid="esports-season-bar"
    >
      <v-slide-group
        :model-value="currentSeasonId"
        show-arrows
      >
        <v-slide-group-item
          v-for="option in seasons"
          :key="option.id"
          :value="option.id"
        >
          <v-chip
            class="ma-1"
            :color="option.id === currentSeasonId ? 'primary' : undefined"
            :data-testid="`esports-season-${option.id}`"
            :variant="option.id === currentSeasonId ? 'flat' : 'tonal'"
            @click="showSeason(option.id)"
          >
            {{ option.name }}
          </v-chip>
        </v-slide-group-item>
      </v-slide-group>
    </v-container>

    <v-container
      v-if="loading"
      class="py-10"
    >
      <v-skeleton-loader
        v-for="n in 2"
        :key="n"
        class="mb-4"
        type="image, list-item-two-line, list-item-two-line"
      />
    </v-container>

    <p
      v-else-if="!hasRosters"
      class="text-body-1 text-center my-16"
      data-testid="esports-empty"
    >
      No teams recorded for this season yet.
    </p>

    <template v-else>
      <transition-group
        appear
        name="team-fade"
      >
        <team-roster
          v-for="(team, index) in teams"
          :key="`${currentSeasonId}-${team.id}`"
          :name-right="index % 2 !== 0"
          :team="team"
        />
      </transition-group>
    </template>
  </v-main>
</template>

<style lang="scss" scoped>
.season-bar {
  max-width: 1100px;
}

// A season change swaps one set of teams for another, so the old set leaves rather than
// being replaced under the reader's eyes.
.team-fade-enter-active,
.team-fade-leave-active {
  transition: opacity 260ms ease, transform 260ms ease;
}

.team-fade-enter-from,
.team-fade-leave-to {
  opacity: 0;
  transform: translateY(10px);
}

.team-fade-leave-active {
  position: absolute;
  width: 100%;
}

@media (prefers-reduced-motion: reduce) {
  .team-fade-enter-active,
  .team-fade-leave-active {
    transition: none;
  }
}
</style>
