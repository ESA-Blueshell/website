<script lang="ts" setup>
import {computed, ref, shallowRef} from "vue"
import {useRoute, useRouter} from "vue-router"
import TeamEditor from "@/domains/esports/components/TeamEditor.vue"
import {type Season, useGames, useSeasons, useTeamToEdit} from "@/domains/esports"
import {useReturnTo} from "@/composables/useReturnTo"
import NotFound from "@/pages/NotFound.vue"

defineOptions({name: "TeamEditPage"})

/**
 * A team added to one game in one season, or its line-up there corrected, on its own page. It goes
 * back to the game page it was opened from, on the same season.
 */
const route = useRoute()
const router = useRouter()
const {ready: gamesReady, bySlug} = useGames()
const {seasons, ready: seasonsReady, newest} = useSeasons()

const slug = String(route.params.slug)
const teamId = route.params.team == null ? null : Number(route.params.team)
const seasonId = route.query.season == null ? null : Number(route.query.season)
const back = useReturnTo(seasonId == null ? `/competition/${slug}` : `/competition/${slug}?season=${seasonId}`)

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
    :accent="game.accent || 'var(--color-brand)'"
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
</template>
