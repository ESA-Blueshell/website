<script lang="ts" setup>
import {computed, onMounted, ref, watch} from "vue"
import CutButton from "@/components/island/CutButton.vue"
import DateTimeInput from "@/components/island/DateTimeInput.vue"
import FormField from "@/components/island/FormField.vue"
import NoticeBox from "@/components/island/NoticeBox.vue"
import {countOf} from "@/utils/countOf"
import {
  createVote,
  loadStandings,
  mergeTrack,
  scheduleVote,
  tracksOf,
  type AnnualVote,
  type Placing,
  type Track,
  type VoteKind,
} from "../adapters/vote"
import {decemberDates, isoOf, localOf, type VoteDates} from "../dates"

defineOptions({name: "VoteBoardTools"})

const props = defineProps<{
  kind: VoteKind
  /** The latest vote of the kind, or null before the first. */
  vote: AnnualVote | null
  /** The year a new vote is started for. */
  year: number
}>()

const emit = defineEmits<{changed: [vote: AnnualVote]}>()

const standings = ref<Placing[]>([])
const problem = ref("")
const busy = ref(false)
const dates = ref<VoteDates>(decemberDates(props.year))
/** The game whose tracks are listed for merging, and those tracks. */
const opened = ref<number | null>(null)
const openedTracks = ref<Track[]>([])

/** A new vote is started before the first, and once the last one is revealed and its year is past. */
const starting = computed<boolean>(() => !props.vote || (props.vote.state === "PUBLISHED" && props.vote.year < props.year))
const editing = computed<boolean>(() => !starting.value && props.vote!.state !== "PUBLISHED")

const FIELDS: Array<{key: keyof VoteDates, label: string}> = [
  {key: "opensAt", label: "Voting opens"},
  {key: "closesAt", label: "Voting closes"},
  {key: "revealAt", label: "Results revealed"},
]

function reset() {
  const vote = props.vote
  dates.value = starting.value || !vote
    ? decemberDates(props.year)
    : {opensAt: localOf(vote.opensAt), closesAt: localOf(vote.closesAt), revealAt: localOf(vote.revealAt)}
}

async function refresh() {
  reset()
  const vote = props.vote
  standings.value = vote && vote.state !== "PUBLISHED" ? await loadStandings(vote.kind, vote.year) : []
}

async function save() {
  const opensAt = isoOf(dates.value.opensAt)
  const closesAt = isoOf(dates.value.closesAt)
  const revealAt = isoOf(dates.value.revealAt)
  problem.value = ""
  if (!opensAt || !closesAt || !revealAt) {
    problem.value = "Fill in all three dates."
    return
  }
  busy.value = true
  const schedule = {opensAt, closesAt, revealAt}
  const done = starting.value ? await createVote(props.kind, props.year, schedule) : await scheduleVote(props.kind, props.vote!.year, schedule)
  busy.value = false
  if (done.ok) emit("changed", done.saved)
  else problem.value = done.reason
}

async function showTracks(igdbId: number) {
  if (opened.value === igdbId) {
    opened.value = null
    return
  }
  opened.value = igdbId
  openedTracks.value = await tracksOf(igdbId)
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
    <h3 class="tools__title">
      Board
    </h3>

    <form
      v-if="starting || editing"
      class="tools__dates"
      data-testid="vote-board-dates"
      @submit.prevent="save"
    >
      <p class="tools__said">
        {{ starting ? `Start the ${year} vote. It runs on these dates by itself: nobody has to press anything on the day.` : "The vote runs on these dates. You can move them until the results are revealed." }}
      </p>
      <div class="tools__fields">
        <form-field
          v-for="field in FIELDS"
          :key="field.key"
          :filled="Boolean(dates[field.key])"
          :label="field.label"
        >
          <template #default="{controlId}">
            <date-time-input
              v-model="dates[field.key]"
              :control-id="controlId"
              :testid="`vote-board-${field.key}`"
            />
          </template>
        </form-field>
      </div>
      <cut-button
        :disabled="busy"
        submit
        testid="vote-board-save"
        tone="solid"
      >
        {{ starting ? `Start the ${year} vote` : "Save the dates" }}
      </cut-button>
    </form>

    <notice-box
      v-if="problem"
      testid="vote-board-problem"
      tone="danger"
    >
      {{ problem }}
    </notice-box>

    <div
      v-if="vote && vote.state !== 'PUBLISHED'"
      class="standings"
      data-testid="vote-standings"
    >
      <p class="tools__said">
        Standings so far, seen only by the board.
        <template v-if="vote.picksTrack">
          Open a game to merge a duplicate track into the one that leads it.
        </template>
      </p>
      <p
        v-if="standings.length === 0"
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
              <span>{{ track.title }}</span>
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
  margin-top: 2.5rem;
  padding-top: 1.5rem;
  border-top: 1px solid var(--color-hairline);
}

.tools__title {
  font-family: var(--font-display);
  font-size: 1.25rem;
  text-transform: uppercase;
}

.tools__dates {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 1rem;
}

.tools__fields {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(14rem, 1fr));
  gap: 1rem;
  width: 100%;
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
