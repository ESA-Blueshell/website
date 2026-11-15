<script lang="ts" setup>
import {computed, onMounted, ref} from "vue"
import BandHead from "@/components/island/BandHead.vue"
import CutButton from "@/components/island/CutButton.vue"
import HeaderBand from "@/components/island/HeaderBand.vue"
import Island from "@/components/island/Island.vue"
import LeadBand from "@/components/island/LeadBand.vue"
import LoadingSpinner from "@/components/island/LoadingSpinner.vue"
import NoticeBox from "@/components/island/NoticeBox.vue"
import BallotList from "@/domains/vote/island/BallotList.vue"
import BoardTools from "@/domains/vote/island/BoardTools.vue"
import PickFinder from "@/domains/vote/island/PickFinder.vue"
import ResultList from "@/domains/vote/island/ResultList.vue"
import {
  castBallot,
  loadBallot,
  loadVote,
  moved,
  said,
  samePicks,
  without,
  withPick,
  type AnnualVote,
  type Ballot,
  type BallotPick,
  type VoteKindPage,
} from "@/domains/vote"
import {useIsBoard} from "@/composables/useIsBoard"
import store from "@/plugins/store"

defineOptions({name: "AnnualVotePage"})

const props = defineProps<{page: VoteKindPage}>()

/** The year a new vote is for: this one. */
const THIS_YEAR = new Date().getFullYear()

const vote = ref<AnnualVote | null>(null)
const loaded = ref(false)
const ballot = ref<Ballot | null>(null)
const picks = ref<BallotPick[]>([])
const saving = ref(false)
const saved = ref(false)
const problem = ref("")

const isBoard = useIsBoard()
const signedIn = computed<boolean>(() => store.getters.isLoggedIn === true)
const isMember = computed<boolean>(() => store.getters.isMember === true)
const voting = computed<boolean>(() => vote.value?.state === "VOTING")
const changed = computed<boolean>(() => !samePicks(picks.value, ballot.value?.picks ?? []))
const full = computed<boolean>(() => picks.value.length >= (vote.value?.points.length ?? 0))

const tail = computed<string>(() => {
  switch (vote.value?.state) {
    case "VOTING": return "voting is open"
    case "COUNTING": return "the votes are being counted"
    case "PUBLISHED": return "the results are in"
    default: return "coming soon"
  }
})

const scoring = computed<string>(() => {
  const points = vote.value?.points ?? []
  return `Your first pick is worth ${points[0]} points, then ${points.slice(1, -1).join(", ")} and ${points.at(-1)}.`
})

const onceSaid = computed<string>(() => vote.value?.picksTrack
  ? "A game can be on your ballot once; pick another track for it and it takes the old one's place."
  : "A game can be on your ballot once.")

const fullSaid = computed<string>(() => vote.value?.picksTrack
  ? "Your ballot is full. Take a game off to add another, or pick a new track for a game already on it."
  : "Your ballot is full. Take a game off to add another.")

async function readBallot() {
  if (!voting.value || !isMember.value) return
  ballot.value = await loadBallot(vote.value!.kind, vote.value!.year)
  picks.value = [...ballot.value.picks]
}

async function onChanged(next: AnnualVote) {
  vote.value = next
  await readBallot()
}

function onPick(pick: BallotPick) {
  picks.value = withPick(picks.value, pick, vote.value!.points.length)
  saved.value = false
}

function onMove(index: number, step: -1 | 1) {
  picks.value = moved(picks.value, index, step)
  saved.value = false
}

function onRemove(index: number) {
  picks.value = without(picks.value, index)
  saved.value = false
}

async function save() {
  saving.value = true
  problem.value = ""
  const cast = await castBallot(vote.value!.kind, vote.value!.year, picks.value, ballot.value?.version ?? null)
  saving.value = false
  if (!cast.ok) {
    problem.value = cast.reason
    return
  }
  ballot.value = cast.saved
  picks.value = [...cast.saved.picks]
  saved.value = true
  // A first ballot moves the count everyone sees.
  vote.value = (await loadVote(props.page.kind)) ?? vote.value
}

onMounted(async () => {
  vote.value = await loadVote(props.page.kind)
  loaded.value = true
  await readBallot()
})
</script>

<template>
  <main class="site-main">
    <island testid="vote-island">
      <header-band
        :body="page.intro"
        :eyebrow="vote ? `${page.title} · ${vote.year}` : page.title"
        :heading="`${page.title},`"
        :heading-tail="tail"
      />

      <lead-band testid="vote-band">
        <loading-spinner
          v-if="!loaded"
          label="Loading the vote"
        />

        <notice-box
          v-else-if="!vote"
          testid="vote-none"
        >
          There is no vote yet. Keep an eye on the events page.
        </notice-box>

        <notice-box
          v-else-if="vote.state === 'UPCOMING'"
          testid="vote-upcoming"
        >
          Voting opens {{ said(vote.opensAt) }} and closes {{ said(vote.closesAt) }}.
        </notice-box>

        <template v-else-if="vote.state === 'VOTING'">
          <band-head
            :count="vote.ballots"
            count-said="ballots cast"
            eyebrow="Your ballot"
            :heading="`Rank your top ${vote.points.length}`"
            testid="vote-ballot-head"
          />
          <p class="said">
            {{ scoring }} {{ onceSaid }}
            Voting closes {{ said(vote.closesAt) }}, and the results are revealed {{ said(vote.revealAt) }}.
          </p>

          <notice-box
            v-if="!signedIn"
            testid="vote-sign-in"
          >
            Only members can vote.
            <cut-button
              :href="`/login?redirect=${page.path}`"
              small
              testid="vote-sign-in-link"
              tone="solid"
            >
              Log in to vote
            </cut-button>
          </notice-box>
          <notice-box
            v-else-if="!isMember"
            testid="vote-not-member"
          >
            Only members can vote. Become a member and your ballot counts too.
          </notice-box>

          <div
            v-else
            class="ballot"
          >
            <ballot-list
              v-if="picks.length"
              :picks="picks"
              :points="vote.points"
              @move="onMove"
              @remove="onRemove"
            />
            <p
              v-else
              class="said"
              data-testid="vote-ballot-empty"
            >
              Your ballot is empty. Find a game below to start.
            </p>

            <pick-finder
              :vote="vote"
              @pick="onPick"
            />
            <p
              v-if="full"
              class="said"
              data-testid="vote-ballot-full"
            >
              {{ fullSaid }}
            </p>

            <div class="ballot__save">
              <cut-button
                :disabled="saving || !changed || picks.length === 0"
                testid="vote-save"
                tone="solid"
                @click="save"
              >
                {{ ballot?.version == null ? "Cast your ballot" : "Save your ballot" }}
              </cut-button>
              <span
                v-if="saved && !changed"
                class="said"
                data-testid="vote-saved"
              >Saved. You can change it until voting closes.</span>
            </div>
            <notice-box
              v-if="problem"
              testid="vote-problem"
              tone="danger"
            >
              {{ problem }}
            </notice-box>
          </div>
        </template>

        <notice-box
          v-else-if="vote.state === 'COUNTING'"
          testid="vote-counting"
        >
          Voting has closed after {{ vote.ballots }} ballots. The results are revealed {{ said(vote.revealAt) }}.
        </notice-box>

        <template v-else>
          <band-head
            :count="vote.results.length"
            count-said="games in the top"
            :eyebrow="`From ${vote.ballots} ballots`"
            :heading="`${page.title} of ${vote.year}`"
            testid="vote-results-head"
          />
          <result-list :placings="vote.results" />
        </template>

        <board-tools
          v-if="loaded && isBoard"
          :kind="page.kind"
          :vote="vote"
          :year="THIS_YEAR"
          @changed="onChanged"
        />
      </lead-band>
    </island>
  </main>
</template>

<style scoped>
.said {
  margin: 1rem 0;
  font-size: 0.95rem;
  color: var(--color-ash);
}

.ballot {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
  margin-top: 1.25rem;
}

.ballot__save {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 1rem;
}
</style>
