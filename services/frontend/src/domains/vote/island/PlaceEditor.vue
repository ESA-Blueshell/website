<script lang="ts" setup>
import {computed, onBeforeUnmount, ref} from "vue"
import FormField from "@/components/island/FormField.vue"
import SearchPicker from "@/components/island/SearchPicker.vue"
import {latestWins} from "@/utils/latestWins"
import {
  addTrack,
  coverPreviewUrl,
  findGames,
  storeGame,
  tracksOf,
  type AnnualVote,
  type CatalogueGame,
  type Track,
} from "../adapters/vote"
import type {BallotPick} from "../ballot"

/**
 * The next free place of a top, filled in where it stands: the game on the first line and, where
 * the vote picks songs, the song on the second, from those members already added or from a pasted
 * YouTube or Spotify link.
 */
defineOptions({name: "VotePlaceEditor"})

const props = defineProps<{vote: AnnualVote}>()

const emit = defineEmits<{pick: [pick: BallotPick], problem: [reason: string]}>()

/** How long typing must pause before the catalogue is asked, so a word is one search. */
const SETTLE_MS = 250
const LINK = "link"
const LOOKS_LIKE_LINK = /^https?:\/\/\S+$/i

const games = ref<CatalogueGame[]>([])
const searching = ref(false)
const searched = ref(false)
const game = ref<CatalogueGame | null>(null)
const tracks = ref<Track[]>([])
const songTerm = ref("")
const busy = ref(false)
let settling: ReturnType<typeof setTimeout> | undefined
// A slower answer to an older term must not replace a newer one.
const searches = latestWins()

const gameOptions = computed(() => games.value.map(one => ({
  key: String(one.igdbId),
  label: one.name,
  note: one.firstReleaseYear ? String(one.firstReleaseYear) : undefined,
  avatar: coverPreviewUrl(one.coverImageId) ?? undefined,
})))

const songOptions = computed(() => {
  const term = songTerm.value.trim()
  if (LOOKS_LIKE_LINK.test(term)) return [{key: LINK, label: `Add ${term}`, note: "New song"}]
  const lower = term.toLowerCase()
  return tracks.value
    .filter(one => one.title.toLowerCase().includes(lower))
    .map(one => ({key: String(one.id), label: one.title, note: one.source === "SPOTIFY" ? "Spotify" : "YouTube"}))
})

const gameNote = computed<string>(() => (searched.value && !searching.value ? "No game matches that." : ""))
const songNote = computed<string>(() => (tracks.value.length
  ? "No song matches. Paste a YouTube or Spotify link to add one."
  : "Nobody added a song for this game yet. Paste a YouTube or Spotify link."))

async function ask(term: string) {
  const current = searches.begin()
  searching.value = true
  const found = await findGames(term, props.vote.kind, props.vote.year)
  if (!current()) return
  games.value = found.ok ? found.saved : []
  searched.value = term.trim() !== ""
  searching.value = false
  if (!found.ok) emit("problem", found.reason)
}

function onSearch(term: string) {
  clearTimeout(settling)
  settling = setTimeout(() => void ask(term), SETTLE_MS)
}

function reset() {
  game.value = null
  tracks.value = []
  songTerm.value = ""
}

async function onPickGame(key: string) {
  game.value = games.value.find(one => String(one.igdbId) === key) ?? null
  emit("problem", "")
  if (!game.value) return
  if (!props.vote.picksTrack) {
    busy.value = true
    const stored = await storeGame(game.value.igdbId, props.vote)
    busy.value = false
    if (stored.ok) {
      emit("pick", {candidate: stored.saved, track: null})
      reset()
    } else emit("problem", stored.reason)
    return
  }
  tracks.value = await tracksOf(game.value.igdbId)
}

async function onPickSong(key: string) {
  emit("problem", "")
  if (key !== LINK) {
    const track = tracks.value.find(one => String(one.id) === key)
    if (track) emit("pick", {candidate: track.candidate, track})
    reset()
    return
  }
  busy.value = true
  const added = await addTrack(game.value!.igdbId, songTerm.value.trim(), props.vote)
  busy.value = false
  if (added.ok) {
    emit("pick", {candidate: added.saved.candidate, track: added.saved})
    reset()
  } else emit("problem", added.reason)
}

onBeforeUnmount(() => clearTimeout(settling))
</script>

<template>
  <div
    class="place-editor"
    data-testid="vote-finder"
  >
    <form-field
      :filled="Boolean(game)"
      label="Game"
      variant="inside"
    >
      <template #default="{controlId, labelId}">
        <search-picker
          :control-id="controlId"
          :disabled="busy"
          :empty-note="gameNote"
          :labelled-by="labelId"
          :loading="searching"
          :options="gameOptions"
          :placeholder="vote.releasedInYear ? `Search games released in ${vote.year}` : 'Search for a game'"
          remote
          :selected-key="game ? String(game.igdbId) : null"
          testid-prefix="vote-game"
          @clear="reset"
          @pick="onPickGame"
          @search="onSearch"
        />
      </template>
    </form-field>
    <form-field
      v-if="vote.picksTrack"
      :filled="false"
      label="Song"
      variant="inside"
    >
      <template #default="{controlId, labelId}">
        <search-picker
          :control-id="controlId"
          :disabled="!game || busy"
          :empty-note="game ? songNote : ''"
          :labelled-by="labelId"
          :options="songOptions"
          :placeholder="game ? 'Pick a song or paste a link' : 'Pick a game first'"
          remote
          testid-prefix="vote-song"
          @pick="onPickSong"
          @search="songTerm = $event"
        />
      </template>
    </form-field>
  </div>
</template>

<style scoped>
.place-editor {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
  min-width: 0;
}
</style>
