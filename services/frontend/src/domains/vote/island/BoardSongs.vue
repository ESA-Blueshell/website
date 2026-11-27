<script lang="ts" setup>
import {computed, onBeforeUnmount, onMounted, ref} from "vue"
import BandHead from "@/components/island/BandHead.vue"
import FormField from "@/components/island/FormField.vue"
import NoticeBox from "@/components/island/NoticeBox.vue"
import SearchPicker from "@/components/island/SearchPicker.vue"
import TextInput from "@/components/island/TextInput.vue"
import {latestWins} from "@/utils/latestWins"
import {imageAttrs, loadSongs, mergeTrack, renameTrack, type Track} from "../adapters/vote"
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
  const byGame = new Map<number, {name: string; cover: Track["candidate"]["cover"]; songs: Track[]}>()
  for (const song of songs.value ?? []) {
    const game = byGame.get(song.candidate.id) ?? {name: song.candidate.name, cover: song.candidate.cover, songs: []}
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
    <div class="songs__head">
      <band-head
        :count="songs?.length ?? 0"
        count-said="songs"
        heading="Songs"
      />
      <p class="songs__said">
        Every song members added, game by game. Press a title to rename it. Merge a song into another of the same game
        when the two links are the same song, and its votes count toward that one.
      </p>
    </div>

    <form-field
      class="songs__search"
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
      <header class="songs__game-head">
        <img
          v-if="game.cover"
          v-bind="imageAttrs(game.cover)"
          alt=""
          class="songs__cover"
          sizes="2.25rem"
        >
        <span
          v-else
          class="songs__cover songs__cover--none"
        />
        <h4 class="songs__name">
          {{ game.name }}
        </h4>
        <span class="songs__count">{{ game.songs.length }} {{ game.songs.length === 1 ? "song" : "songs" }}</span>
      </header>
      <ul class="songs__list">
        <li
          v-for="song in game.songs"
          :key="song.id"
          class="songs__song"
          :data-testid="`vote-songs-song-${song.id}`"
        >
          <span
            aria-hidden="true"
            class="songs__glyph"
            :class="`songs__glyph--${song.source.toLowerCase()}`"
          >
            <svg
              v-if="song.source === 'YOUTUBE'"
              fill="currentColor"
              viewBox="0 0 24 24"
            ><path d="M3 7.5C3 6 4 5 5.5 5h13C20 5 21 6 21 7.5v9c0 1.5-1 2.5-2.5 2.5h-13C4 19 3 18 3 16.5zM10 9v6l5-3z" /></svg>
            <svg
              v-else
              fill="none"
              stroke="currentColor"
              stroke-linecap="round"
              stroke-width="1.8"
              viewBox="0 0 24 24"
            ><circle
              cx="12"
              cy="12"
              r="9"
            /><path d="M7.5 9.5c3-1 6.5-.7 9 .8M8 12.6c2.5-.7 5.2-.4 7.3.7M8.7 15.5c2-.5 4-.3 5.6.5" /></svg>
          </span>
          <span class="songs__words">
            <track-title
              class="songs__title"
              renamable
              :testid="`vote-songs-title-${song.id}`"
              :title="song.title"
              @rename="rename(song, $event)"
            />
            <span class="songs__meta">{{ SERVICE[song.source] }}</span>
          </span>
          <span class="songs__end">
            <search-picker
              v-if="game.songs.length > 1"
              class="songs__merge"
              empty-note=""
              :options="others(song, game.songs)"
              placeholder="Merge into another song"
              :testid-prefix="`vote-songs-merge-${song.id}`"
              @pick="merge(song, $event)"
            />
            <a
              :aria-label="`Open ${song.title} on ${SERVICE[song.source]}`"
              class="songs__open"
              :href="song.url"
              rel="noopener"
              target="_blank"
            >
              <svg
                aria-hidden="true"
                fill="none"
                stroke="currentColor"
                stroke-linecap="round"
                stroke-linejoin="round"
                stroke-width="1.6"
                viewBox="0 0 24 24"
              ><path d="M14 5h5v5M19 5l-8 8M17 14v5H5V7h5" /></svg>
            </a>
          </span>
        </li>
      </ul>
    </section>
  </section>
</template>

<style scoped>
.songs {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

.songs__head {
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
}

.songs__said {
  max-width: 44rem;
  font-size: 0.9rem;
  color: var(--color-ash);
}

.songs__search {
  max-width: 28rem;
}

.songs__game {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

/* A game heads its songs: its cover, its name and how many songs it has, on a hairline. */
.songs__game-head {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  padding-bottom: 0.5rem;
  border-bottom: 1px solid var(--color-hairline);
}

.songs__cover {
  flex: none;
  width: 2.25rem;
  aspect-ratio: 3 / 4;
  object-fit: cover;
}

.songs__cover--none {
  background-color: var(--color-raised);
}

.songs__name {
  font-family: var(--font-display);
  font-size: 1.05rem;
  text-transform: uppercase;
}

.songs__count {
  margin-left: auto;
  font-size: 0.75rem;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--color-ash);
}

.songs__list {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
}

/* A list row as CutRow draws one: its glyph, its words and what it offers, cut on both sides at
   12°, so its cut is its own height times tan 12°. */
.songs__song {
  --row-h: 4.2rem;
  --cut: calc(var(--row-h) * 0.2126);
  display: grid;
  grid-template-columns: 2.5rem minmax(0, 1fr) auto;
  align-items: center;
  gap: 0 1.1rem;
  min-height: var(--row-h);
  padding: 0.4rem calc(var(--cut) + 0.8rem) 0.4rem calc(var(--cut) + 0.9rem);
  clip-path: polygon(var(--cut) 0, 100% 0, calc(100% - var(--cut)) 100%, 0 100%);
  background-color: var(--color-raised);
  transition: background-color 220ms ease;
}

.songs__song:hover {
  background-color: color-mix(in oklab, var(--color-raised) 80%, var(--color-chalk) 6%);
}

.songs__glyph {
  display: grid;
  place-items: center;
  width: 2.5rem;
  height: 2.5rem;
  background: color-mix(in oklab, var(--color-chalk) 5%, transparent);
}

.songs__glyph svg {
  width: 1.3rem;
  height: 1.3rem;
}

.songs__glyph--youtube {
  color: var(--color-danger);
}

.songs__glyph--spotify {
  color: var(--color-ok);
}

.songs__words {
  display: flex;
  flex-direction: column;
  gap: 0.1rem;
  min-width: 0;
}

.songs__title {
  font-weight: 700;
}

.songs__meta {
  font-size: 0.75rem;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--color-ash);
}

.songs__end {
  display: flex;
  align-items: center;
  gap: 0.6rem;
}

.songs__merge {
  width: 14rem;
}

.songs__open {
  display: grid;
  place-items: center;
  width: 2.25rem;
  height: 2.25rem;
  color: var(--color-ash);
}

.songs__open:hover {
  color: var(--color-chalk);
}

.songs__open svg {
  width: 1.1rem;
  height: 1.1rem;
}

@media (max-width: 600px) {
  .songs__song {
    --row-h: 6.6rem;
    grid-template-columns: 2.5rem minmax(0, 1fr) auto;
  }

  .songs__end {
    display: contents;
  }

  .songs__merge {
    grid-column: 1 / -1;
    grid-row: 2;
    width: 100%;
    margin-top: 0.4rem;
  }
}
</style>
