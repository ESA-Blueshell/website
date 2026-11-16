<script lang="ts" setup>
import {computed, onBeforeUnmount, ref} from "vue"
import CutButton from "@/components/island/CutButton.vue"
import FormField from "@/components/island/FormField.vue"
import NoticeBox from "@/components/island/NoticeBox.vue"
import SearchPicker from "@/components/island/SearchPicker.vue"
import TextInput from "@/components/island/TextInput.vue"
import {
  addTrack,
  coverPreviewUrl,
  findGames,
  storedUrl,
  storeGame,
  tracksOf,
  type AnnualVote,
  type CatalogueGame,
  type Track,
} from "../adapters/vote"
import type {BallotPick} from "../ballot"

defineOptions({name: "VotePickFinder"})

const props = defineProps<{vote: AnnualVote}>()

const emit = defineEmits<{pick: [pick: BallotPick]}>()

/** How long typing must pause before the catalogue is asked, so a word is one search. */
const SETTLE_MS = 250

const games = ref<CatalogueGame[]>([])
const searching = ref(false)
const game = ref<CatalogueGame | null>(null)
const tracks = ref<Track[]>([])
const link = ref("")
const busy = ref(false)
const problem = ref("")
let settling: ReturnType<typeof setTimeout> | undefined
let asked = 0

const options = computed(() => games.value.map(one => ({
  key: String(one.igdbId),
  label: one.name,
  note: one.firstReleaseYear ? String(one.firstReleaseYear) : undefined,
  avatar: coverPreviewUrl(one.coverImageId) ?? undefined,
})))

async function ask(term: string) {
  const mine = ++asked
  searching.value = true
  const found = await findGames(term, props.vote.kind, props.vote.year)
  // A slower answer to an older term must not replace a newer one.
  if (mine !== asked) return
  games.value = found
  searching.value = false
}

function onSearch(term: string) {
  clearTimeout(settling)
  settling = setTimeout(() => void ask(term), SETTLE_MS)
}

async function onPickGame(key: string) {
  game.value = games.value.find(one => String(one.igdbId) === key) ?? null
  problem.value = ""
  tracks.value = game.value && props.vote.picksTrack ? await tracksOf(game.value.igdbId) : []
}

function onClear() {
  game.value = null
  tracks.value = []
  problem.value = ""
}

function choose(pick: BallotPick) {
  emit("pick", pick)
  onClear()
  link.value = ""
}

async function pickGame() {
  busy.value = true
  const stored = await storeGame(game.value!.igdbId, props.vote)
  busy.value = false
  if (stored.ok) choose({candidate: stored.saved, track: null})
  else problem.value = stored.reason
}

async function addLink() {
  if (!link.value.trim()) return
  busy.value = true
  const added = await addTrack(game.value!.igdbId, link.value.trim(), props.vote)
  busy.value = false
  if (added.ok) choose({candidate: added.saved.candidate, track: added.saved})
  else problem.value = added.reason
}

onBeforeUnmount(() => clearTimeout(settling))
</script>

<template>
  <div
    class="finder"
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
          empty-note=""
          :labelled-by="labelId"
          :loading="searching"
          :options="options"
          :placeholder="vote.releasedInYear ? `Search for a game released in ${vote.year}` : 'Search for a game'"
          remote
          :selected-key="game ? String(game.igdbId) : null"
          testid-prefix="vote-game"
          @clear="onClear"
          @pick="onPickGame"
          @search="onSearch"
        />
      </template>
    </form-field>

    <div
      v-if="game && !vote.picksTrack"
      class="finder__game"
    >
      <cut-button
        :disabled="busy"
        testid="vote-game-pick"
        tone="solid"
        @click="pickGame"
      >
        Put {{ game.name }} on your ballot
      </cut-button>
    </div>

    <div
      v-if="game && vote.picksTrack"
      class="finder__tracks"
      data-testid="vote-tracks"
    >
      <p class="finder__said">
        {{ tracks.length ? `Tracks members picked for ${game.name}` : `Nobody picked a track for ${game.name} yet.` }}
      </p>
      <ul
        v-if="tracks.length"
        class="finder__list"
      >
        <li
          v-for="track in tracks"
          :key="track.id"
          class="finder__track"
        >
          <img
            v-if="storedUrl(track.thumbnailPath)"
            alt=""
            class="finder__thumb"
            :src="storedUrl(track.thumbnailPath)!"
          >
          <span
            v-else
            class="finder__thumb finder__thumb--none"
          />
          <span class="finder__title">{{ track.title }}</span>
          <cut-button
            small
            :testid="`vote-track-pick-${track.id}`"
            tone="quiet"
            @click="choose({candidate: track.candidate, track})"
          >
            Pick
          </cut-button>
        </li>
      </ul>

      <form
        class="finder__add"
        @submit.prevent="addLink"
      >
        <form-field
          :filled="Boolean(link)"
          hint="A YouTube video or a Spotify track"
          label="Or paste a link"
          variant="inside"
        >
          <template #default="{controlId}">
            <text-input
              v-model="link"
              :control-id="controlId"
              testid="vote-link"
              type="url"
            />
          </template>
        </form-field>
        <cut-button
          :disabled="busy"
          submit
          testid="vote-link-add"
          tone="solid"
        >
          Add
        </cut-button>
      </form>
    </div>

    <notice-box
      v-if="problem"
      testid="vote-finder-problem"
      tone="danger"
    >
      {{ problem }}
    </notice-box>
  </div>
</template>

<style scoped>
.finder,
.finder__tracks {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.finder__tracks {
  gap: 0.75rem;
}

.finder__said {
  font-size: 0.9rem;
  color: var(--color-ash);
}

.finder__list {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
}

.finder__track {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr) auto;
  align-items: center;
  gap: 0.75rem;
  padding: 0.4rem 1rem;
  clip-path: polygon(0.5rem 0, 100% 0, calc(100% - 0.5rem) 100%, 0 100%);
  background-color: var(--color-raised);
}

.finder__thumb {
  width: 3.5rem;
  aspect-ratio: 16 / 9;
  object-fit: cover;
}

.finder__thumb--none {
  background-color: var(--color-surface);
}

.finder__title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.finder__add {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: start;
  gap: 0.75rem;
}
</style>
