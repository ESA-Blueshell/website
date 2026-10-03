<script lang="ts" setup>
import Island from "@/components/island/Island.vue"
import PagePlaceholder from "@/components/island/PagePlaceholder.vue"
import {computed, ref, shallowRef} from "vue"
import {useRoute, useRouter} from "vue-router"
import TeamEditor from "@/domains/esports/components/TeamEditor.vue"
import {type Season, useGames, useSeasons, useTeamToEdit} from "@/domains/esports"
import {useReturnTo} from "@/composables/useReturnTo"
import NotFound from "@/pages/NotFound.vue"
import {BRAND_ACCENT} from "@/utils/brand"

defineOptions({name: "TeamEditPage"})

/**
 * A team added to one game in one season, or its line-up there corrected, on its own page. It goes
 * back to the game page it was opened from, on the same season, or to Management's teams.
 */
const route = useRoute()
const router = useRouter()
const {ready: gamesReady, bySlug} = useGames()
const {seasons, ready: seasonsReady, newest} = useSeasons()

const slug = String(route.params.slug)
const teamId = route.params.team == null ? null : Number(route.params.team)
const seasonId = route.query.season == null ? null : Number(route.query.season)
// Inside Management the editor goes back to Management's list of teams.
const back = useReturnTo(route.meta.portal ?? (seasonId == null ? `/competition/${slug}` : `/competition/${slug}?season=${seasonId}`))

const game = computed(() => bySlug(slug))
const answered = ref(false)
const loaded = shallowRef<ReturnType<typeof useTeamToEdit> | null>(null)
void Promise.all([gamesReady, seasonsReady]).then(async () => {
  if (game.value) {
    const read = useTeamToEdit(game.value.code, teamId, seasonId)
    await read.answered
    loaded.value = read
  }
  answered.value = true
})

/** The season asked for, which a game that fielded nobody in it does not answer with itself. */
const season = computed<Season | null>(() => loaded.value?.season.value
  ?? seasons.value.find(one => one.id === seasonId)
  ?? newest.value)
const team = computed(() => loaded.value?.team.value ?? null)
const found = computed(() => answered.value && game.value != null && (teamId == null || team.value != null))
</script>

<template>
  <team-editor
    v-if="found && game"
    :accent="game.accent || BRAND_ACCENT"
    :already-fielded="loaded?.fielded.value.map(one => one.id) ?? []"
    :back="back"
    :game="game.code"
    :game-name="game.name"
    :season="season"
    :team-banner="team?.banner ?? null"
    :team-icon="team?.icon ?? null"
    :team-id="teamId"
    :team-name="team?.name ?? ''"
    @cancel="router.replace(back)"
    @removed="router.replace(back)"
    @saved="router.replace(back)"
  />
  <not-found v-else-if="answered" />
  <v-main v-else>
    <island>
      <page-placeholder testid="team-edit-placeholder" />
    </island>
  </v-main>
</template>
