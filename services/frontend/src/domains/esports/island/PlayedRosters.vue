<script lang="ts" setup>
import {computed, onMounted, ref, watch} from "vue"
import CountBadge from "@/components/island/CountBadge.vue"
import CutRow from "@/components/island/CutRow.vue"
import {TeamRole, type PlayedRosterResponse} from "@/services/api"
import {loadPlayedRosters} from "../adapters/esports"
import {useGames} from "./useGames"

/**
 * The seasons and teams somebody played for, grouped by season, newest first. Each opens that
 * game's page on that season, where the roster is.
 */
defineOptions({name: "PlayedRosters"})

const {userId} = defineProps<{
  userId: number
}>()

const {identityOf, recordOf} = useGames()

const ROLE_NAMES: Record<TeamRole, string> = {
  [TeamRole.PLAYER]: "Player",
  [TeamRole.SUBSTITUTE]: "Substitute",
  [TeamRole.COACH]: "Coach",
}

const played = ref<PlayedRosterResponse[]>([])
const loaded = ref(false)

const seasons = computed(() => {
  const groups: {id: number, name: string, spots: PlayedRosterResponse[]}[] = []
  for (const spot of played.value) {
    const last = groups.at(-1)
    if (last?.id === spot.seasonId) last.spots.push(spot)
    else groups.push({id: spot.seasonId, name: spot.seasonName, spots: [spot]})
  }
  return groups
})

const pageOf = (spot: PlayedRosterResponse) => {
  const slug = recordOf(spot.game)?.slug
  return slug ? `/esports/${slug}?season=${spot.seasonId}` : ""
}

const metaOf = (spot: PlayedRosterResponse) =>
  [identityOf(spot.game).name, spot.roleTitle || ROLE_NAMES[spot.role]].filter(Boolean).join(" · ")

const load = async () => {
  played.value = await loadPlayedRosters(userId)
  loaded.value = true
}

watch(() => userId, load)
onMounted(load)
</script>

<template>
  <section
    class="played"
    data-testid="played-rosters"
  >
    <h2 class="played__head">
      Where you played<count-badge
        v-if="played.length"
        :count="played.length"
        said="roster spots"
      />
    </h2>
    <p
      v-if="loaded && !played.length"
      class="played__none"
      data-testid="played-rosters-none"
    >
      No roster lists you yet. Once a team you play for links you on its roster, it shows here.
    </p>
    <template
      v-for="season in seasons"
      :key="season.id"
    >
      <h3 class="played__season">
        {{ season.name }}
      </h3>
      <div class="played__rows">
        <cut-row
          v-for="spot in season.spots"
          :key="`${spot.game}-${spot.teamId}`"
          :meta="metaOf(spot)"
          testid="played-roster"
          :title="spot.teamName"
          :to="pageOf(spot)"
        >
          <template #glyph>
            <img
              v-if="identityOf(spot.game).icon"
              alt=""
              class="played__icon"
              :src="identityOf(spot.game).icon!"
              :srcset="identityOf(spot.game).iconSrcset"
              sizes="22px"
            >
            <span v-else>{{ identityOf(spot.game).name.charAt(0) }}</span>
          </template>
        </cut-row>
      </div>
    </template>
  </section>
</template>

<style scoped>
.played__head {
  margin: 2.5rem 0 0.4rem;
  font-family: var(--font-display);
  font-size: 1.3rem;
  line-height: 1.1;
  text-transform: uppercase;
}

.played__none {
  margin-top: 0.6rem;
  font-size: 0.9rem;
  color: var(--color-ash);
}

.played__season {
  display: flex;
  align-items: center;
  gap: 1rem;
  padding: 1.2rem 0 0.6rem;
  font-family: var(--font-display);
  font-size: 0.95rem;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

.played__season::after {
  content: "";
  flex: 1 1 auto;
  height: 1px;
  background: var(--color-hairline);
}

.played__rows {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.played__icon {
  width: 22px;
  height: 22px;
  object-fit: contain;
}
</style>
