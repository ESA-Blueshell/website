<script lang="ts" setup>
import {computed, ref, watch} from "vue"
import IslandDialog from "./IslandDialog.vue"
import ImagePicker from "./ImagePicker.vue"
import {
  dropBanner,
  loadBanners,
  setBanner,
  type EsportsBanner,
  type Game,
  type Season,
} from "../adapters/esports"

/**
 * The banners behind a game's pages, at each of the levels one can be set for.
 *
 * The levels are shown together rather than one at a time because which of them a page ends
 * up with is decided by comparing them: an upload against the game is invisible on a team
 * that has its own, and that is only obvious with both in front of you.
 */
defineOptions({name: "BannerDialog"})

const props = defineProps<{
  open: boolean
  game: Game
  /** The season on show, which is the one a season-level banner would be set for. */
  season: Season | null
  /** The teams fielded in that season, each of which can carry its own. */
  teams: Array<{id: number; name: string}>
  accent?: string
}>()

const emit = defineEmits<{
  (event: "update:open", open: boolean): void
  (event: "changed"): void
}>()

interface Level {
  key: string
  label: string
  /** What this level says about the season, which is nothing where it carries every season. */
  seasonId?: number
  /** What it says about the team, which is nothing where it carries every team. */
  teamId?: number
}

const banners = ref<EsportsBanner[]>([])
const loading = ref(false)
const busy = ref<string | null>(null)
const failure = ref<string | null>(null)

/**
 * Every level in the order the resolution reads them, least specific first, so the list is
 * also an explanation of which one wins.
 */
const levels = computed<Level[]>(() => {
  const season = props.season
  const rows: Level[] = [{key: "game", label: "Every season, every team"}]
  if (season) rows.push({key: `season-${season.id}`, label: season.name, seasonId: season.id})
  for (const team of props.teams) {
    rows.push({key: `team-${team.id}`, label: `${team.name}, every season`, teamId: team.id})
    if (season) {
      rows.push({
        key: `team-${team.id}-season-${season.id}`,
        label: `${team.name} in ${season.name}`,
        seasonId: season.id,
        teamId: team.id,
      })
    }
  }
  return rows
})

/** The banner set at exactly this level, which is not necessarily the one the level shows. */
const at = (level: Level): EsportsBanner | undefined =>
  banners.value.find(one =>
    (one.seasonId ?? undefined) === level.seasonId && (one.teamId ?? undefined) === level.teamId)

const read = async () => {
  loading.value = true
  failure.value = null
  try {
    banners.value = await loadBanners(props.game)
  } finally {
    loading.value = false
  }
}

watch(() => [props.open, props.game] as const, ([open]) => {
  if (open) void read()
}, {immediate: true})

const upload = async (level: Level, file: File) => {
  if (busy.value) return
  busy.value = level.key
  failure.value = null
  try {
    await setBanner(props.game, file, level.seasonId, level.teamId)
    await read()
    emit("changed")
  } catch {
    failure.value = "That banner could not be uploaded."
  } finally {
    busy.value = null
  }
}

const remove = async (level: Level) => {
  const banner = at(level)
  if (!banner || busy.value) return
  busy.value = level.key
  failure.value = null
  try {
    await dropBanner(banner.id)
    await read()
    emit("changed")
  } catch {
    failure.value = "That banner could not be removed."
  } finally {
    busy.value = null
  }
}
</script>

<template>
  <island-dialog
    :accent="accent"
    :open="open"
    testid="banner-dialog"
    title="The banners behind this game"
    @update:open="emit('update:open', $event)"
  >
    <p class="banners__note">
      A page takes the most narrowly set banner that applies to it. One set here for the game
      carries every page that has nothing of its own.
    </p>

    <p
      v-if="loading"
      class="banners__note"
    >
      Reading the banners…
    </p>

    <div
      v-for="level in levels"
      v-else
      :key="level.key"
      class="banners__level"
    >
      <image-picker
        :busy="busy === level.key"
        :label="level.label"
        :testid="`banner-${level.key}`"
        :url="at(level)?.url ?? null"
        @clear="remove(level)"
        @pick="upload(level, $event)"
      />
    </div>

    <p
      v-if="failure"
      class="banners__failure"
      data-testid="banner-failure"
    >
      {{ failure }}
    </p>
  </island-dialog>
</template>

<style scoped>
.banners__note {
  margin-bottom: 0.9rem;
  font-family: var(--font-body);
  font-size: 0.78rem;
  line-height: 1.5;
  color: var(--color-ash);
}

.banners__level {
  padding: 0.7rem 0;
  border-top: 1px solid color-mix(in srgb, var(--color-ash) 22%, transparent);
}

.banners__failure {
  margin-top: 0.8rem;
  font-family: var(--font-body);
  font-size: 0.78rem;
  color: var(--color-warning, #ff8a80);
}
</style>
