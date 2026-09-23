<script lang="ts" setup>
import {computed, onMounted, ref} from "vue"
import {useRoute} from "vue-router"
import {useStore} from "vuex"
import CutButton from "@/components/island/CutButton.vue"
import BandHead from "@/components/island/BandHead.vue"
import FormControl from "@/components/island/FormControl.vue"
import HeaderBand from "@/components/island/HeaderBand.vue"
import Island from "@/components/island/Island.vue"
import LeadBand from "@/components/island/LeadBand.vue"
import NumberBand from "@/domains/association/island/NumberBand.vue"
import type {Figure} from "@/domains/association"
import QuestionResponses from "@/domains/events/island/QuestionResponses.vue"
import SignUpRoster, {type RosterRow} from "@/domains/events/island/SignUpRoster.vue"
import EditSignUpDialog from "@/components/common/modals/EditSignUpDialog.vue"
import RemoveSignUpDialog from "@/components/common/modals/RemoveSignUpDialog.vue"
import {useTableSort} from "@/composables/useTableSort"
import {
  EventSignUpKind,
  type EventResponse,
  type EventSignUpResponse,
  listEventSignUps,
  type QuestionResponse,
  readEvent,
  removeSignUp,
  type SurveyResponse,
  whenOf,
} from "@/domains/events"
import {$handleNetworkError} from "@/plugins/handleNetworkError"
import {compareSignUpKind, type SignUpRow, signUpPerson, toSignUpRows} from "@/utils/eventSignUpRows"
import {buildEventSignUpsCsv, eventSignUpsCsvFilename} from "@/utils/eventSignUpsCsv"

const event = ref<EventResponse>()
const signUps = ref<EventSignUpResponse[]>([])

type RespondentRow = RosterRow

const rows = ref<SignUpRow[]>([])

const {sortedItems, toggleSort, ariaSort} = useTableSort<SignUpRow, "kind">(rows, {
  kind: compareSignUpKind,
})

const respondents = computed<RespondentRow[]>(() =>
  sortedItems.value.map((row: SignUpRow) => ({
    ...row,
    person: signUpPerson(row.signUp),
  })),
)

const route = useRoute()
const store = useStore()

const mayManageSignUps = computed<boolean>(() => store.getters.isBoard)

const eventId = computed<number>(() => Number(route.params.id))

async function loadSignUps(): Promise<void> {
  // A roster that could not be read is not an empty one, so the rows stay as they are.
  const loaded = await listEventSignUps(eventId.value)
  if (loaded == null) return
  signUps.value = loaded
  rows.value = toSignUpRows(signUps.value)
}

onMounted(async () => {
  try {
    const [read] = await Promise.all([readEvent(eventId.value), loadSignUps()])

    event.value = read
  } catch (err) {
    $handleNetworkError(err)
  }
})

const signUpToRemove = ref<RespondentRow | null>(null)
const removeDialogOpen = computed<boolean>({
  get: () => signUpToRemove.value !== null,
  set: (open: boolean) => {
    if (!open) signUpToRemove.value = null
  },
})

const removeTargetName = computed<string>(() => signUpToRemove.value?.person.name ?? "")

const signUpToEdit = ref<EventSignUpResponse | null>(null)
const editDialogOpen = computed<boolean>({
  get: () => signUpToEdit.value !== null,
  set: (open: boolean) => {
    if (!open) signUpToEdit.value = null
  },
})

function askToEdit(row: RespondentRow): void {
  signUpToEdit.value = row.signUp
}

async function onSignUpSaved(): Promise<void> {
  signUpToEdit.value = null
  await loadSignUps()
}

function askToRemove(row: RespondentRow): void {
  signUpToRemove.value = row
}

async function confirmRemove(notify: boolean): Promise<void> {
  const row = signUpToRemove.value
  signUpToRemove.value = null
  if (!row) return

  try {
    await removeSignUp(row.signUp.id, notify)
    await loadSignUps()
  } catch (err) {
    $handleNetworkError(err)
  }
}

const sortedQuestions = computed<QuestionResponse[]>(() => {
  const sf: SurveyResponse | null | undefined = event.value?.signUpForm
  if (!sf?.questions?.length) return []
  return [...sf.questions].sort((a: QuestionResponse, b: QuestionResponse) => a.idx - b.idx)
})

const eventHasForm = computed<boolean>(() => sortedQuestions.value.length > 0)

/* The roster read by what is typed: a name, a handle, an address or a number. */
const search = ref("")
const shown = computed<RespondentRow[]>(() => {
  const term = search.value.trim().toLowerCase()
  if (term === "") return respondents.value
  return respondents.value.filter(row => Object.values(row.person).some(said => said.toLowerCase().includes(term)))
})

/* The kind column's order, in the words its header is named with. */
const SORT_SAID = {none: "as signed up", ascending: "guests first", descending: "members first"} as const
const sortSaid = computed(() => SORT_SAID[ariaSort("kind") as keyof typeof SORT_SAID])

const when = computed(() => (event.value ? whenOf(event.value) : null))

const figures = computed<Figure[]>(() => {
  const count = (kind: EventSignUpKind) => signUps.value.filter(one => one.kind === kind).length
  const limit = event.value?.signUpLimit
  const taken = signUps.value.length
  const split = `${count(EventSignUpKind.MEMBER)} · ${count(EventSignUpKind.NON_MEMBER)} · ${count(EventSignUpKind.GUEST)}`
  return [
    {id: "signed-up", value: taken, exact: true, label: "signed up"},
    ...(limit == null ? [] : [
      {id: "places", value: limit, exact: true, label: "places"},
      {id: "left", value: Math.max(limit - taken, 0), exact: true, label: "places left"},
    ]),
    {id: "split", value: taken, exact: true, label: "members · non-members · guests", text: split},
  ]
})

function exportCsv(): void {
  if (!event.value) return
  const csv = buildEventSignUpsCsv(event.value, signUps.value)
  const blob = new Blob([csv], {type: "text/csv;charset=utf-8"})
  const url = URL.createObjectURL(blob)
  const anchor = document.createElement("a")
  anchor.href = url
  anchor.download = eventSignUpsCsvFilename(event.value.title)
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  URL.revokeObjectURL(url)
}

</script>

<template>
  <v-main>
    <island
      class="signups-page"
      testid="signups-island"
    >
      <header-band>
        <template #head>
          <div class="signups-head">
            <div>
              <p class="signups-head__eyebrow">
                Sign-ups
              </p>
              <h1 class="signups-head__title">
                {{ event?.title ?? "" }}
              </h1>
              <p
                v-if="event && when"
                class="signups-head__body"
              >
                {{ when.day }}, {{ when.hours }}<template v-if="event.location">
                  at {{ event.location }}
                </template>.
              </p>
            </div>
            <div class="signups-head__actions">
              <cut-button
                :disabled="respondents.length === 0"
                testid="export-csv-btn"
                @click="exportCsv"
              >
                Export as CSV
              </cut-button>
              <cut-button
                :href="`/events/edit/${eventId}`"
                testid="signups-edit-event"
                tone="quiet"
              >
                Edit event
              </cut-button>
            </div>
          </div>
        </template>
      </header-band>

      <number-band
        :figures="figures"
        testid="signups-numbers"
      />

      <lead-band
        accent="var(--color-brand)"
        testid="signups-attendees"
      >
        <band-head
          :count="respondents.length"
          count-said="people signed up"
          heading="Attendees"
        >
          <form-control
            v-model="search"
            class="signups-search"
            data-testid="signups-search"
            label="Search attendees"
          />
        </band-head>

        <sign-up-roster
          class="signups-roster"
          :has-form="eventHasForm"
          :may-manage="mayManageSignUps"
          :rows="shown"
          :sort-on="ariaSort('kind') !== 'none'"
          :sort-said="sortSaid"
          @edit="askToEdit"
          @remove="askToRemove"
          @sort="toggleSort('kind')"
        />
        <p
          v-if="shown.length === 0"
          class="signups-empty"
        >
          {{ respondents.length === 0 ? "Nobody has signed up yet." : "Nobody here answers to that." }}
        </p>
      </lead-band>

      <lead-band
        v-if="eventHasForm"
        accent="var(--color-acid)"
        testid="signups-responses"
      >
        <band-head
          eyebrow="From the sign-up form"
          heading="Responses"
        />
        <div class="signups-answers">
          <question-responses
            v-for="question in sortedQuestions"
            :key="question.id"
            :question="question"
            :rows="respondents"
          />
        </div>
      </lead-band>

      <edit-sign-up-dialog
        v-if="event && signUpToEdit"
        v-model="editDialogOpen"
        :event="event"
        :sign-up="signUpToEdit"
        @saved="onSignUpSaved"
      />

      <remove-sign-up-dialog
        v-model="removeDialogOpen"
        :person-name="removeTargetName"
        @confirm="confirmRemove"
      />
    </island>
  </v-main>
</template>

<style scoped>
/* The island root fills a page; the Vuetify main around it already does. */
.signups-page {
  min-height: 0;
}

.signups-head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: 1.5rem 2rem;
  padding-top: 1.5rem;
}

.signups-head__eyebrow {
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 0.3em;
  text-transform: uppercase;
  color: var(--color-eyebrow);
}

.signups-head__title {
  margin-top: 0.7rem;
  font-family: var(--font-display);
  font-size: 3.5rem;
  line-height: 0.95;
  text-transform: uppercase;
  overflow-wrap: break-word;
}

.signups-head__body {
  margin-top: 0.9rem;
  color: var(--color-ash);
}

.signups-head__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.6rem;
}

.signups-search {
  width: 16rem;
}

.signups-roster {
  margin-top: 1rem;
}

.signups-empty {
  padding: 1.5rem 0 0.5rem;
  color: var(--color-ash);
}

.signups-answers {
  display: flex;
  flex-direction: column;
  gap: 2.25rem;
  margin-top: 1.5rem;
}

@media (max-width: 767px) {
  .signups-head {
    padding-top: 0.5rem;
  }

  .signups-head__title {
    font-size: 2.4rem;
  }

  .signups-search {
    width: 10rem;
  }
}
</style>
