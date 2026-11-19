<script lang="ts" setup>
import {onMounted, ref, watch} from "vue"
import BandHead from "@/components/island/BandHead.vue"
import CutButton from "@/components/island/CutButton.vue"
import NoticeBox from "@/components/island/NoticeBox.vue"
import {countOf} from "@/utils/countOf"
import {loadStandings, mergeTrack, renameTrack, tracksOf, type AnnualVote, type Placing, type Track} from "../adapters/vote"
import TrackTitle from "./TrackTitle.vue"

defineOptions({name: "VoteBoardTools"})

const props = defineProps<{
  /** The current vote of the kind, or null before the first. */
  vote: AnnualVote | null
}>()

defineEmits<{
  /** The board asks to move the current vote's dates or picks. */
  edit: []
  /** The board asks to start a new vote. */
  start: []
}>()

/** The standings so far, or null where they could not be read. */
const standings = ref<Placing[] | null>([])
const problem = ref("")
/** The game whose tracks are listed for merging, and those tracks. */
const opened = ref<number | null>(null)
const openedTracks = ref<Track[]>([])

async function refresh() {
  const vote = props.vote
  standings.value = vote && vote.state !== "REVEALED" ? await loadStandings(vote.kind, vote.year) : []
}

async function showTracks(igdbId: number) {
  if (opened.value === igdbId) {
    opened.value = null
    return
  }
  opened.value = igdbId
  openedTracks.value = await tracksOf(igdbId)
}

async function rename(track: Track, title: string) {
  problem.value = ""
  const renamed = await renameTrack(track.id, title)
  if (!renamed.ok) {
    problem.value = renamed.reason
    return
  }
  openedTracks.value = await tracksOf(track.candidate.igdbId)
  standings.value = await loadStandings(props.vote!.kind, props.vote!.year)
}

async function merge(track: Track, into: Track) {
  problem.value = ""
  const merged = await mergeTrack(track.id, into.id)
  if (!merged.ok) {
    problem.value = merged.reason
    return
  }
  openedTracks.value = await tracksOf(into.candidate.igdbId)
  standings.value = await loadStandings(props.vote!.kind, props.vote!.year)
}

onMounted(refresh)
watch(() => props.vote, refresh)
</script>

<template>
  <section
    class="tools"
    data-testid="vote-board"
  >
    <div class="tools__head">
      <band-head heading="Board" />
      <cut-button
        small
        :testid="vote && vote.state !== 'REVEALED' ? 'vote-board-edit' : 'vote-board-start'"
        tone="plain"
        @click="vote && vote.state !== 'REVEALED' ? $emit('edit') : $emit('start')"
      >
        {{ vote && vote.state !== 'REVEALED' ? "Dates and picks" : "Start a vote" }}
      </cut-button>
    </div>

    <notice-box
      v-if="problem"
      testid="vote-board-problem"
      tone="danger"
    >
      {{ problem }}
    </notice-box>

    <div
      v-if="vote && vote.state !== 'REVEALED'"
      class="standings"
      data-testid="vote-standings"
    >
      <p class="tools__said">
        Standings so far, seen only by the board.
        <template v-if="vote.picksTrack">
          Open a game to merge a duplicate track into the one that leads it.
        </template>
      </p>
      <notice-box
        v-if="standings === null"
        testid="vote-standings-unread"
        tone="danger"
      >
        The standings could not be read. Reload the page to try again.
      </notice-box>
      <p
        v-else-if="standings.length === 0"
        class="tools__said"
      >
        No ballots yet.
      </p>
      <ol v-else>
        <li
          v-for="placing in standings"
          :key="placing.candidate.id"
          class="standing"
          data-testid="vote-standing"
        >
          <button
            class="standing__row"
            :data-testid="`vote-standing-open-${placing.candidate.igdbId}`"
            :disabled="!placing.track"
            type="button"
            @click="showTracks(placing.candidate.igdbId)"
          >
            <span class="standing__place">{{ placing.place }}</span>
            <span class="standing__game">{{ placing.candidate.name }}</span>
            <span class="standing__track">{{ placing.track?.title }}</span>
            <span class="standing__points">{{ placing.points }} pts · {{ countOf(placing.voters, "voter", "voters") }}</span>
          </button>
          <ul
            v-if="placing.track && opened === placing.candidate.igdbId"
            class="standing__tracks"
          >
            <li
              v-for="track in openedTracks"
              :key="track.id"
              class="standing__dup"
            >
              <track-title
                renamable
                :testid="`vote-track-title-${track.id}`"
                :title="track.title"
                @rename="rename(track, $event)"
              />
              <cut-button
                v-if="track.id !== placing.track.id"
                small
                :testid="`vote-merge-${track.id}`"
                tone="quiet"
                @click="merge(track, placing.track)"
              >
                Merge into the leading track
              </cut-button>
              <span
                v-else
                class="tools__said"
              >leading</span>
            </li>
          </ul>
        </li>
      </ol>
    </div>
  </section>
</template>

<style scoped>
.tools {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.tools__head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: 1rem;
}

.tools__said {
  font-size: 0.9rem;
  color: var(--color-ash);
}

.standings ol {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  margin-top: 0.75rem;
}

.standing__row {
  display: grid;
  grid-template-columns: 2rem minmax(0, 1fr) minmax(0, 1.4fr) auto;
  gap: 0.75rem;
  width: 100%;
  padding: 0.45rem 1rem;
  text-align: left;
  background-color: var(--color-raised);
  color: var(--color-chalk);
}

.standing__track {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--color-ash);
}

.standing__points {
  font-variant-numeric: tabular-nums;
}

.standing__tracks {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  padding: 0.5rem 0 0.5rem 2.75rem;
}

.standing__dup {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
}
</style>
