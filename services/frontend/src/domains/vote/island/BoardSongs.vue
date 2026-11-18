<script lang="ts" setup>
import {computed, onBeforeUnmount, onMounted, ref} from "vue"
import FormField from "@/components/island/FormField.vue"
import NoticeBox from "@/components/island/NoticeBox.vue"
import SearchPicker from "@/components/island/SearchPicker.vue"
import TextInput from "@/components/island/TextInput.vue"
import {latestWins} from "@/utils/latestWins"
import {loadSongs, mergeTrack, renameTrack, type Track} from "../adapters/vote"
import TrackTitle from "./TrackTitle.vue"

/**
 * Every song members added, game by game, for the board to tend: found by its title or its game,
 * renamed where it stands, and merged into another song of the same game when two are the same.
 */
defineOptions({name: "VoteBoardSongs"})

/** How long typing must pause before the songs are asked for again. */
const SETTLE_MS = 250
const SERVICE = {YOUTUBE: "YouTube", SPOTIFY: "Spotify"} as const

const term = ref("")
/** The songs found, or null where they could not be read. */
const songs = ref<Track[] | null>([])
const problem = ref("")
let settling: ReturnType<typeof setTimeout> | undefined
const reads = latestWins()

const games = computed(() => {
  const byGame = new Map<number, {name: string; songs: Track[]}>()
  for (const song of songs.value ?? []) {
    const game = byGame.get(song.candidate.id) ?? {name: song.candidate.name, songs: []}
    game.songs.push(song)
    byGame.set(song.candidate.id, game)
  }
  return [...byGame.entries()].map(([id, game]) => ({id, ...game}))
})

async function read() {
  const current = reads.begin()
  const found = await loadSongs(term.value)
  if (current()) songs.value = found
}

function onType(next: string) {
  term.value = next
  clearTimeout(settling)
  settling = setTimeout(() => void read(), SETTLE_MS)
}

async function rename(song: Track, title: string) {
  problem.value = ""
  const renamed = await renameTrack(song.id, title)
  if (renamed.ok) await read()
  else problem.value = renamed.reason
}

async function merge(song: Track, intoKey: string) {
  problem.value = ""
  const merged = await mergeTrack(song.id, Number(intoKey))
  if (merged.ok) await read()
  else problem.value = merged.reason
}

/** The other songs of the same game, which a song can be merged into. */
const others = (song: Track, all: Track[]) =>
  all.filter(one => one.id !== song.id).map(one => ({key: String(one.id), label: one.title}))

onMounted(read)
onBeforeUnmount(() => clearTimeout(settling))
</script>

<template>
  <section
    class="songs"
    data-testid="vote-songs"
  >
    <h3 class="songs__title">
      Songs
    </h3>
    <p class="songs__said">
      Every song members added, game by game. Press a title to rename it. Merge a song into another of the same game
      when the two are the same song, and its votes count toward that one.
    </p>

    <form-field
      :filled="Boolean(term)"
      label="Find a song or a game"
      variant="inside"
    >
      <template #default="{controlId}">
        <text-input
          :control-id="controlId"
          :model-value="term"
          testid="vote-songs-search"
          @update:model-value="onType"
        />
      </template>
    </form-field>

    <notice-box
      v-if="problem"
      testid="vote-songs-problem"
      tone="danger"
    >
      {{ problem }}
    </notice-box>
    <notice-box
      v-if="songs === null"
      testid="vote-songs-unread"
      tone="danger"
    >
      The songs could not be read. Reload the page to try again.
    </notice-box>
    <p
      v-else-if="games.length === 0"
      class="songs__said"
      data-testid="vote-songs-none"
    >
      {{ term ? "No song or game matches that." : "Nobody added a song yet." }}
    </p>

    <section
      v-for="game in games"
      :key="game.id"
      class="songs__game"
      :data-testid="`vote-songs-game-${game.id}`"
    >
      <h4 class="songs__name">
        {{ game.name }}
      </h4>
      <ul class="songs__list">
        <li
          v-for="song in game.songs"
          :key="song.id"
          class="songs__song"
          :data-testid="`vote-songs-song-${song.id}`"
        >
          <track-title
            class="songs__words"
            renamable
            :testid="`vote-songs-title-${song.id}`"
            :title="song.title"
            @rename="rename(song, $event)"
          />
          <a
            class="songs__link"
            :href="song.url"
            rel="noopener"
            target="_blank"
          >{{ SERVICE[song.source] }}</a>
          <search-picker
            v-if="game.songs.length > 1"
            class="songs__merge"
            empty-note=""
            :options="others(song, game.songs)"
            placeholder="Merge into another song"
            :testid-prefix="`vote-songs-merge-${song.id}`"
            @pick="merge(song, $event)"
          />
        </li>
      </ul>
    </section>
  </section>
</template>

<style scoped>
.songs {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  margin-top: 2.5rem;
  padding-top: 1.5rem;
  border-top: 1px solid var(--color-hairline);
}

.songs__title {
  font-family: var(--font-display);
  font-size: 1.25rem;
  text-transform: uppercase;
}

.songs__said {
  font-size: 0.9rem;
  color: var(--color-ash);
}

.songs__game {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}

.songs__name {
  font-weight: 700;
}

.songs__list {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
}

.songs__song {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto 12rem;
  align-items: center;
  gap: 1rem;
  padding: 0.45rem 1rem;
  background-color: var(--color-raised);
}

.songs__link {
  font-size: 0.85rem;
  color: var(--color-brand-ink);
}

@media (max-width: 600px) {
  .songs__song {
    grid-template-columns: minmax(0, 1fr) auto;
  }

  .songs__merge {
    grid-column: 1 / -1;
  }
}
</style>
