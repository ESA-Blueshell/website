<script lang="ts" setup>
import {computed, ref, watch} from "vue"
import CountInput from "@/components/island/CountInput.vue"
import CutButton from "@/components/island/CutButton.vue"
import DateTimeInput from "@/components/island/DateTimeInput.vue"
import FormField from "@/components/island/FormField.vue"
import ModalDialog from "@/components/island/ModalDialog.vue"
import NoticeBox from "@/components/island/NoticeBox.vue"
import {createVote, rescheduleVote, type AnnualVote, type VoteKind, type VoteSchedule} from "../adapters/vote"
import {decemberDates, isoOf, localOf} from "../dates"

/**
 * The board starts a year's vote, or moves one's dates and number of picks, in a dialog opened from
 * the year on the timeline. A revealed vote is shown as it ran: its dates no longer move.
 */
defineOptions({name: "VoteDatesDialog"})

const props = defineProps<{
  open: boolean
  kind: VoteKind
  /** The vote being changed, or null to start [year]'s. */
  vote: AnnualVote | null
  /** The year a new vote is started for. */
  year: number
}>()

const emit = defineEmits<{"update:open": [open: boolean], changed: [vote: AnnualVote]}>()

/** What a new vote's ballot holds unless the board says otherwise. TWIN: `VoteKind.defaultPicks` in the api. */
const DEFAULT_PICKS = 5

const FIELDS: Array<{key: Exclude<keyof VoteSchedule, "picks">, label: string}> = [
  {key: "opensAt", label: "Voting opens"},
  {key: "closesAt", label: "Voting closes"},
  {key: "revealAt", label: "Results revealed"},
]

const dates = ref<VoteSchedule>(decemberDates(props.year))
/** The number of picks a ballot holds, as the form holds it; empty sends none. */
const picks = ref("")
const problem = ref("")
const busy = ref(false)

const starting = computed<boolean>(() => props.vote === null)
const revealed = computed<boolean>(() => props.vote?.state === "REVEALED")
/** The number of picks can change until the first vote is cast. TWIN: `VotePicksFixed` in the api. */
const picksOpen = computed<boolean>(() => starting.value || props.vote!.ballots === 0)
const shownYear = computed<number>(() => props.vote?.year ?? props.year)

const title = computed<string>(() => (starting.value ? `Start the ${props.year} vote` : `The ${shownYear.value} vote`))
const said = computed<string>(() => {
  if (starting.value) return "It runs on these dates, in Amsterdam time, by itself: nobody has to press anything on the day."
  if (revealed.value) return "Its results are revealed, so its dates and picks stay as they were."
  return "The vote runs on these dates, in Amsterdam time. You can move them until the results are revealed."
})

// Filled in afresh each time it opens, from the vote it is opened on.
watch(() => props.open, open => {
  if (!open) return
  problem.value = ""
  const vote = props.vote
  picks.value = String(vote ? vote.points.length : DEFAULT_PICKS)
  dates.value = vote
    ? {opensAt: localOf(vote.opensAt), closesAt: localOf(vote.closesAt), revealAt: localOf(vote.revealAt)}
    : decemberDates(props.year)
}, {immediate: true})

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
  const schedule = {opensAt, closesAt, revealAt, ...(picksOpen.value && picks.value ? {picks: Number(picks.value)} : {})}
  const done = props.vote
    ? await rescheduleVote(props.kind, props.vote.year, schedule, props.vote.version)
    : await createVote(props.kind, props.year, schedule)
  busy.value = false
  if (!done.ok) {
    problem.value = done.reason
    return
  }
  emit("changed", done.saved)
  emit("update:open", false)
}
</script>

<template>
  <modal-dialog
    cancel-testid="vote-dates-cancel"
    :open="open"
    testid="vote-dates-dialog"
    :title="title"
    wide
    @update:open="emit('update:open', $event)"
  >
    <form
      id="vote-dates-form"
      class="dates"
      data-testid="vote-board-dates"
      @submit.prevent="save"
    >
      <p class="dates__said">
        {{ said }}
      </p>
      <div class="dates__fields">
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
              :disabled="revealed"
              :testid="`vote-board-${field.key}`"
            />
          </template>
        </form-field>
      </div>
      <form-field
        v-if="picksOpen && !revealed"
        class="dates__picks"
        :filled="Boolean(picks)"
        label="Picks per vote"
      >
        <template #default="{controlId}">
          <count-input
            v-model="picks"
            :control-id="controlId"
            :min="1"
            testid="vote-board-picks"
          />
        </template>
      </form-field>
      <notice-box
        v-if="problem"
        testid="vote-board-problem"
        tone="danger"
      >
        {{ problem }}
      </notice-box>
    </form>

    <template
      v-if="!revealed"
      #footer
    >
      <cut-button
        :disabled="busy"
        form="vote-dates-form"
        submit
        testid="vote-board-save"
        tone="solid"
      >
        {{ starting ? `Start the ${year} vote` : "Save" }}
      </cut-button>
    </template>
  </modal-dialog>
</template>

<style scoped>
.dates {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.dates__said {
  font-size: 0.9rem;
  color: var(--color-ash);
}

.dates__fields {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(15rem, 1fr));
  gap: 1rem;
}

.dates__picks {
  max-width: 12rem;
}
</style>
