<script lang="ts" setup>
import {computed, ref, watch} from "vue"
import {useRouter} from "vue-router"
import BandHead from "@/components/island/BandHead.vue"
import BandRule from "@/components/island/BandRule.vue"
import CutButton from "@/components/island/CutButton.vue"
import HeaderBand from "@/components/island/HeaderBand.vue"
import Island from "@/components/island/Island.vue"
import LeadBand from "@/components/island/LeadBand.vue"
import LoadingSpinner from "@/components/island/LoadingSpinner.vue"
import NoticeBox from "@/components/island/NoticeBox.vue"
import SegmentedChoice from "@/components/island/SegmentedChoice.vue"
import StateTag from "@/components/island/StateTag.vue"
import TaskLayout from "@/components/island/TaskLayout.vue"
import Timeline from "@/components/island/Timeline.vue"
import type {Stop} from "@/components/island/stripAxis"
import BallotList from "@/domains/vote/island/BallotList.vue"
import BoardSongs from "@/domains/vote/island/BoardSongs.vue"
import BoardTools from "@/domains/vote/island/BoardTools.vue"
import VoteDatesDialog from "@/domains/vote/island/VoteDatesDialog.vue"
import ResultList from "@/domains/vote/island/ResultList.vue"
import {
  castBallot,
  loadBallot,
  loadVote,
  loadYearVote,
  loadYears,
  moved,
  movedTo,
  renameTrack,
  said,
  saidShort,
  samePicks,
  useVoteRights,
  without,
  withPick,
  type AnnualVote,
  type Ballot,
  type BallotPick,
  type VoteKindPage,
  type VoteState,
} from "@/domains/vote"
import {BRAND_ACCENT} from "@/utils/brand"
import {ASSOCIATION_ZONE, yearIn} from "@/utils/datetime"
import {latestWins} from "@/utils/latestWins"
import {loginHref} from "@/utils/loginRedirect"

defineOptions({name: "AnnualVotePage"})

const router = useRouter()

const props = defineProps<{
  page: VoteKindPage
  /** A past year to look back on; left out, the kind's current vote. */
  year?: number
}>()

/** The year a new vote is for: this one, in the association's time. */
const THIS_YEAR = yearIn(ASSOCIATION_ZONE)

/** The Help topic on taking part, which the aside links to. */
const HELP_TOPIC = "events-annual-vote"

const vote = ref<AnnualVote | null>(null)
/** The kind's current vote, which the board's band works on whatever year is shown. */
const latest = ref<AnnualVote | null>(null)
/** The years the kind has a vote for, newest first, to move between. */
const years = ref<number[]>([])
const loaded = ref(false)
/** Whether the year shown is the current one, the only one the board's band acts on. */
// The years the vote ran, oldest first, as stops on the site's timeline; the current one is Now.
const yearStops = computed<Stop[]>(() => [...years.value].reverse().map(year => ({
  id: year,
  name: `${props.page.title} ${year}`,
  label: String(year),
  sublabel: year === latest.value?.year ? TAIL[latest.value.state] : year < (latest.value?.year ?? year) ? "results" : "coming",
  mark: year === latest.value?.year ? "Now" : "",
})))

/** A revealed vote shows its results, or the member's own top as they cast it. */
const VIEWS = [{key: "results", label: "Results"}, {key: "mine", label: "Your top"}]
const view = ref("results")

// The board starts and changes a year's vote in a dialog, opened from the timeline or its band.
const dialogOpen = ref(false)
const dialogVote = ref<AnnualVote | null>(null)
const dialogYear = ref(THIS_YEAR)

// One vote a year, started for this one: where it already has one, that vote is opened instead.
async function startVote() {
  if (years.value.includes(THIS_YEAR)) return editYear(THIS_YEAR)
  dialogVote.value = null
  dialogYear.value = THIS_YEAR
  dialogOpen.value = true
}

async function editYear(year: number) {
  const chosen = year === latest.value?.year ? latest.value : await loadYearVote(props.page.kind, year)
  if (!chosen) return
  dialogVote.value = chosen
  dialogOpen.value = true
}

function onYear(year: number) {
  void router.push(yearHref(year))
}

const showingLatest = computed<boolean>(() => !vote.value || vote.value.year === latest.value?.year)
const yearHref = (year: number): string => (year === latest.value?.year ? props.page.path : `${props.page.path}/${year}`)
const ballot = ref<Ballot | null>(null)
/** Whether the member's ballot was asked for and could not be read: nothing is offered to cast then. */
const unread = ref(false)
const picks = ref<BallotPick[]>([])
const saving = ref(false)
const saved = ref(false)
const problem = ref("")
/** Songs this member added since the page read their ballot, which they may rename too. */
const added = ref<number[]>([])

const {signedIn, isMember, isBoard} = useVoteRights()

// The board renames any song; a member the ones they added. TWIN: `VoteCandidateUseCases.rename`.
const renamable = computed<number[]>(() => {
  const shown = [...picks.value, ...(ballot.value?.picks ?? [])].flatMap(pick => (pick.track ? [pick.track.id] : []))
  if (isBoard.value) return shown
  return [...(ballot.value?.renamableTrackIds ?? []), ...added.value]
})

async function onRename(trackId: number, title: string) {
  problem.value = ""
  const renamed = await renameTrack(trackId, title)
  if (!renamed.ok) {
    problem.value = renamed.reason
    return
  }
  const retitled = (pick: BallotPick): BallotPick => (pick.track?.id === trackId ? {...pick, track: renamed.saved} : pick)
  picks.value = picks.value.map(retitled)
  if (ballot.value) ballot.value = {...ballot.value, picks: ballot.value.picks.map(retitled)}
}
const changed = computed<boolean>(() => !samePicks(picks.value, ballot.value?.picks ?? []))
const full = computed<boolean>(() => picks.value.length >= (vote.value?.points.length ?? 0))

// The heading's own words for each state; the refusals say the same states as "The vote …".
const TAIL: Record<VoteState, string> = {
  UPCOMING: "coming soon",
  VOTING: "voting has started",
  CLOSED: "voting has closed",
  REVEALED: "the results are in",
}

const tail = computed<string>(() => {
  if (!loaded.value) return ""
  return vote.value ? TAIL[vote.value.state] : "no vote yet"
})

const onceSaid = computed<string>(() => vote.value?.picksTrack
  ? "A game is in your top once. Pick another track for it and the new track takes the old one's place."
  : "A game is in your top once.")

const FULL_SAID = "Your top is full. Take a game off to add another."

// Read while voting, to change it, and after, for the member to look back on how they voted.
async function readBallot() {
  if (!vote.value || vote.value.state === "UPCOMING" || !isMember.value) return
  ballot.value = await loadBallot(vote.value!.kind, vote.value!.year)
  unread.value = ballot.value === null
  picks.value = [...(ballot.value?.picks ?? [])]
}

async function onChanged(next: AnnualVote) {
  if (!latest.value || next.year >= latest.value.year) latest.value = next
  // The page shows what the board changed where it showed that year, or the new current vote.
  if (!vote.value || next.year === vote.value.year || (!props.year && next === latest.value)) vote.value = next
  years.value = await loadYears(props.page.kind)
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

function onReorder(from: number, to: number) {
  picks.value = movedTo(picks.value, from, to)
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
  // A first ballot moves the count everyone sees; an answer for a kind left meanwhile is dropped.
  const kind = props.page.kind
  const fresh = await loadVote(kind)
  if (fresh && kind === props.page.kind) vote.value = fresh
}

// Both kinds share this component and the router reuses it between them, so the vote is read
// again for each kind rather than once on mount, and a read for a kind left behind is dropped.
const reads = latestWins()
watch(() => [props.page.kind, props.year] as const, async ([kind, year]) => {
  const current = reads.begin()
  loaded.value = false
  ballot.value = null
  unread.value = false
  picks.value = []
  saved.value = false
  problem.value = ""
  view.value = "results"
  const [now, shown, listed] = await Promise.all([loadVote(kind), year ? loadYearVote(kind, year) : null, loadYears(kind)])
  if (!current()) return
  latest.value = now
  years.value = listed
  vote.value = year ? shown : now
  loaded.value = true
  await readBallot()
}, {immediate: true})
</script>

<template>
  <main class="site-main">
    <island testid="vote-island">
      <header-band
        :body="page.intro"
        :eyebrow="vote ? `${page.title} · ${vote.year}` : page.title"
        :heading="`${page.title},`"
        :heading-tail="tail"
      >
        <template
          v-if="vote"
          #acts
        >
          <state-tag
            v-if="vote.state === 'UPCOMING'"
            testid="vote-opens"
          >
            Opens {{ saidShort(vote.opensAt) }}
          </state-tag>
          <state-tag
            v-if="vote.state === 'UPCOMING' || vote.state === 'VOTING'"
            testid="vote-closes"
            tone="warn"
          >
            Closes {{ saidShort(vote.closesAt) }}
          </state-tag>
          <state-tag
            v-if="vote.state !== 'REVEALED'"
            testid="vote-reveals"
            tone="brand"
          >
            Results {{ saidShort(vote.revealAt) }}
          </state-tag>
          <state-tag
            v-if="vote.state !== 'UPCOMING'"
            testid="vote-ballots"
            tone="quiet"
          >
            {{ vote.ballots }} {{ vote.ballots === 1 ? "vote" : "votes" }}
          </state-tag>
        </template>
      </header-band>

      <!-- The years the vote ran as a line, the one shown lit: a slice of its own under the head. -->
      <section
        v-if="years.length > 0"
        class="w-full"
        data-testid="vote-timeline"
      >
        <timeline
          :accent="BRAND_ACCENT"
          pan-back-label="Show earlier years"
          pan-on-label="Show later years"
          :selected-id="vote?.year ?? null"
          add-label="Start a vote"
          :may-edit="isBoard"
          :stops="yearStops"
          testid-prefix="vote-year"
          @add="startVote"
          @edit="editYear"
          @select="onYear"
        />
      </section>

      <band-rule testid="vote-rule" />

      <lead-band
        class="vote-band"
        testid="vote-band"
      >
        <task-layout
          aside-title="How it works"
          wide
        >
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

          <section
            v-else-if="vote.state === 'VOTING'"
            class="vote__section"
            data-testid="vote-ballot-panel"
          >
            <band-head
              :heading="`Your top ${vote.points.length}`"
              testid="vote-ballot-head"
            />

            <notice-box
              v-if="!signedIn"
              testid="vote-sign-in"
            >
              Only members can vote.
              <cut-button
                :href="loginHref(page.path)"
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
              Only members can vote. Become a member and your top counts too.
              <cut-button
                href="/membership"
                small
                testid="vote-become-member"
                tone="solid"
              >
                Become a member
              </cut-button>
            </notice-box>
            <notice-box
              v-else-if="unread"
              testid="vote-ballot-unread"
              tone="danger"
            >
              Your top could not be read, so nothing can be saved over it. Reload the page to try again.
            </notice-box>

            <template v-else>
              <ballot-list
                :picks="picks"
                :renamable="renamable"
                :vote="vote"
                @added="added.push($event)"
                @move="onMove"
                @pick="onPick"
                @problem="problem = $event"
                @remove="onRemove"
                @rename="onRename"
                @reorder="onReorder"
              >
                <template #foot>
                  <notice-box
                    v-if="problem"
                    class="vote__problem"
                    testid="vote-problem"
                    tone="danger"
                  >
                    {{ problem }}
                  </notice-box>
                  <span
                    v-if="saved && !changed"
                    class="form-save__words"
                    data-testid="vote-saved"
                  >Saved. You can change it until voting closes.</span>
                  <span
                    v-else-if="full"
                    class="form-save__words"
                    data-testid="vote-ballot-full"
                  >{{ FULL_SAID }}</span>
                  <cut-button
                    :disabled="saving || !changed || picks.length === 0"
                    testid="vote-save"
                    tone="solid"
                    @click="save"
                  >
                    {{ ballot?.version == null ? "Cast your vote" : `Save your top ${vote.points.length}` }}
                  </cut-button>
                </template>
              </ballot-list>
            </template>
          </section>

          <notice-box
            v-else-if="vote.state === 'CLOSED'"
            testid="vote-closed"
          >
            Voting has closed after {{ vote.ballots }} {{ vote.ballots === 1 ? "vote" : "votes" }}. The results are revealed {{ said(vote.revealAt) }}.
          </notice-box>

          <section
            v-else
            class="vote__section"
          >
            <div class="vote__results-head">
              <band-head
                :heading="view === 'mine' ? `Your top ${vote.points.length}` : `${page.title} of ${vote.year}`"
                testid="vote-results-head"
              />
              <segmented-choice
                v-if="ballot?.picks.length"
                v-model="view"
                :options="VIEWS"
                testid-prefix="vote-view"
              />
            </div>
            <result-list
              v-if="view === 'results' || !ballot?.picks.length"
              :placings="vote.results"
            />
            <ballot-list
              v-else
              data-testid="vote-mine"
              :picks="ballot.picks"
              readonly
              :renamable="renamable"
              :vote="vote"
              @rename="onRename"
            />
          </section>

          <section
            v-if="vote && vote.state === 'CLOSED' && ballot?.picks.length"
            class="vote__section"
            data-testid="vote-mine"
          >
            <band-head :heading="`Your top ${vote.points.length}`" />
            <ballot-list
              :picks="ballot.picks"
              readonly
              :renamable="renamable"
              :vote="vote"
              @rename="onRename"
            />
          </section>

          <template #aside>
            <p v-if="vote">
              Every member ranks their {{ vote.points.length }} favourites. Each place is worth the points beside it, and
              the points of every ballot add up to the {{ page.title }}.
            </p>
            <p v-if="vote?.picksTrack">
              Pick a game, then the one track that sums up its soundtrack. Pick a track others already added, or paste a
              YouTube or Spotify link.
            </p>
            <p v-if="vote">
              {{ onceSaid }} Drag a game by its handle to change its place.
            </p>
            <p v-if="vote">
              You can change your top until voting closes. It stays here after the reveal for you to look back on;
              nobody else sees how you voted.
            </p>
            <p v-if="signedIn">
              <router-link
                data-testid="vote-help"
                :to="`/account/help#help-${HELP_TOPIC}`"
              >
                More in Help
              </router-link>
            </p>
          </template>
        </task-layout>
      </lead-band>

      <template v-if="loaded && isBoard && showingLatest">
        <band-rule
          mirrored
          testid="vote-board-rule"
        />
        <lead-band testid="vote-board-band">
          <board-tools
            :vote="vote"
            @edit="editYear(vote!.year)"
            @start="startVote"
          />
        </lead-band>
        <band-rule testid="vote-songs-rule" />
        <lead-band
          class="vote-songs-band"
          testid="vote-songs-band"
        >
          <board-songs />
        </lead-band>
      </template>
      <vote-dates-dialog
        v-if="isBoard"
        v-model:open="dialogOpen"
        :kind="page.kind"
        :vote="dialogVote"
        :year="dialogYear"
        @changed="onChanged"
      />
    </island>
  </main>
</template>

<style scoped>
/* The top stands on a ground of its own, tinted blue, so its rows read as bars laid on it. */
.vote-band {
  --band-ground: color-mix(in oklab, var(--color-brand) 5%, var(--color-ground));
}

/* The top takes the room, the aside a reading column beside it, and the band's own padding is the
   only gap over the heading. */
.vote-band :deep(.task) {
  grid-template-columns: minmax(0, 1fr) minmax(0, 18rem);
  gap: 2.5rem;
  padding-top: 0;
}

@media (max-width: 1023px) {
  .vote-band :deep(.task) {
    grid-template-columns: minmax(0, 1fr);
  }
}

.vote__problem {
  flex-basis: 100%;
}

/* The results' heading with the switch to the member's own top at its right. */
.vote__results-head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: 1rem;
}

.vote__section {
  display: flex;
  flex-direction: column;
  gap: 1.1rem;
}
</style>
