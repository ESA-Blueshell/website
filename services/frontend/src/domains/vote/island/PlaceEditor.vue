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

const emit = defineEmits<{pick: [pick: BallotPick], added: [trackId: number], problem: [reason: string]}>()

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
/** Which field the last refusal was about, ruled red until it is typed in again. */
const wrong = ref<"game" | "song" | null>(null)
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


/* Said in the open list, as the Discord picker says nobody was found: only once a search has answered. */
const noGame = computed<boolean>(() => searched.value && !searching.value && games.value.length === 0)
const songNote = computed<string>(() => (tracks.value.length
  ? "No song matches. Paste a YouTube or Spotify link to add one."
  : "Nobody added a song for this game yet. Paste a YouTube or Spotify link."))

/* Ruled red where what is typed matches nothing, or where the last refusal was about the field. */
const gameWrong = computed<boolean>(() => wrong.value === "game" || (noGame.value && !game.value))
const songWrong = computed<boolean>(() => wrong.value === "song" || (songTerm.value.trim() !== "" && songOptions.value.length === 0))

async function ask(term: string) {
  const current = searches.begin()
  searching.value = true
  const found = await findGames(term, props.vote.kind, props.vote.year)
  if (!current()) return
  games.value = found.ok ? found.saved : []
  searched.value = true
  searching.value = false
  wrong.value = found.ok ? null : "game"
  if (!found.ok) emit("problem", found.reason)
}

// Opened before anything is typed, the list shows the games a member is likeliest to look for.
function onOpened() {
  if (!searched.value && !searching.value) void ask("")
}

function onSearch(term: string) {
  clearTimeout(settling)
  settling = setTimeout(() => void ask(term), SETTLE_MS)
}

function reset() {
  wrong.value = null
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
    } else {
      wrong.value = "game"
      emit("problem", stored.reason)
    }
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
    emit("added", added.saved.id)
    emit("pick", {candidate: added.saved.candidate, track: added.saved})
    reset()
  } else {
    wrong.value = "song"
    emit("problem", added.reason)
  }
}

onBeforeUnmount(() => clearTimeout(settling))
</script>

<template>
  <div
    class="place-editor"
    data-testid="vote-finder"
  >
    <form-field
      class="place-editor__game"
      :class="{'place-editor--wrong': gameWrong}"
      :filled="Boolean(game)"
      label="Game"
      variant="inside"
    >
      <template #default="{controlId, labelId}">
        <search-picker
          :control-id="controlId"
          :disabled="busy"
          empty-note=""
          :labelled-by="labelId"
          :loading="searching"
          :options="gameOptions"
          :placeholder="vote.releasedInYear ? `Game released in ${vote.year}` : 'Game'"
          remote
          :selected-key="game ? String(game.igdbId) : null"
          testid-prefix="vote-game"
          @clear="reset"
          @opened="onOpened"
          @pick="onPickGame"
          @search="onSearch"
        >
          <template
            v-if="noGame"
            #missing
          >
            No game found. Try another name.
          </template>
        </search-picker>
      </template>
    </form-field>
    <form-field
      v-if="vote.picksTrack"
      class="place-editor__song"
      :class="{'place-editor--wrong': songWrong}"
      :filled="false"
      label="Song"
      variant="inside"
    >
      <template #default="{controlId, labelId}">
        <search-picker
          :control-id="controlId"
          :disabled="!game || busy"
          empty-note=""
          :labelled-by="labelId"
          :options="songOptions"
          :placeholder="game ? 'Song, or paste a YouTube or Spotify link' : 'Song'"
          remote
          testid-prefix="vote-song"
          @pick="onPickSong"
          @search="songTerm = $event; wrong = null"
        >
          <template
            v-if="game && songOptions.length === 0"
            #missing
          >
            {{ songNote }}
          </template>
        </search-picker>
      </template>
    </form-field>
  </div>
</template>

<style scoped>
/* The fields take the look of what they fill in: the game as a row names it, the song under it as
   a row's track line, on the row's own ground, ruled in the place's colour. */
.place-editor {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
  min-width: 0;
}

/* The words the field says stand in it as its placeholder; the label stays for a screen reader. */
.place-editor :deep(.island-field__label) {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip-path: inset(50%);
}

.place-editor :deep(.island-field__said) {
  display: none;
}

/* No rule at rest: the place's rule runs under the whole row, so the two fields read as its lines.
   A line shows under the one being typed in, green once it holds a choice and red where what is
   typed matches nothing or was refused. */
.place-editor :deep(.picker__field),
.place-editor :deep(.picker__field:focus-within) {
  background: none;
  border-bottom: 1px solid transparent;
}

.place-editor :deep(.picker__field:focus-within) {
  border-bottom-color: color-mix(in oklab, var(--color-chalk) 45%, transparent);
}

.place-editor :deep(.picker__field--chosen) {
  background: none;
  border-bottom-color: var(--color-ok);
}

.place-editor .place-editor--wrong :deep(.picker__field) {
  border-bottom-color: var(--color-danger);
}

.place-editor :deep(.island-field) {
  gap: 0;
}

.place-editor :deep(.island-field.island-field--inside .picker__search) {
  padding: 0.3rem 0.1rem;
}

.place-editor :deep(.island-field.island-field--inside:not(:focus-within) .picker__search::placeholder) {
  color: var(--color-ash);
}

.place-editor :deep(.picker__caret--field) {
  padding-inline: 0.4rem;
}

.place-editor__game :deep(.picker__search) {
  font-size: 1rem;
  font-weight: 700;
}

.place-editor__song :deep(.picker__search) {
  font-size: 0.85rem;
}
</style>
