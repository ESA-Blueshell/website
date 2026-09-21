<script lang="ts" setup>
import {computed, onMounted, ref} from "vue"
import {useRoute} from "vue-router"
import {useStore} from "vuex"
import TopBanner from "@/components/common/banners/TopBanner.vue"
import EditSignUpDialog from "@/components/common/modals/EditSignUpDialog.vue"
import RemoveSignUpDialog from "@/components/common/modals/RemoveSignUpDialog.vue"
import {
  type EventResponse,
  type EventSignUpResponse,
  findEventById,
  type QuestionResponse,
  QuestionType,
  type SurveyResponse,
} from "@/services/api"
import {listEventSignUps, removeSignUp} from "@/domains/events"
import {$handleNetworkError} from "@/plugins/handleNetworkError"
import {
  type KindSort,
  type SignUpPerson,
  type SignUpRow,
  signUpKindLabel,
  signUpPerson,
  sortRowsByKind,
  toSignUpRows,
} from "@/utils/eventSignUpRows"
import {buildEventSignUpsCsv, eventSignUpsCsvFilename} from "@/utils/eventSignUpsCsv"

const event = ref<EventResponse>()
const signUps = ref<EventSignUpResponse[]>([])

type RespondentRow = SignUpRow & {person: SignUpPerson};

const rows = ref<SignUpRow[]>([])

const kindSort = ref<KindSort>(null)

const respondents = computed<RespondentRow[]>(() =>
  sortRowsByKind(rows.value, kindSort.value).map((row: SignUpRow) => ({
    ...row,
    person: signUpPerson(row.signUp),
  })),
)

/** Guests first, then members first, then back to the order the signups arrived in. */
function toggleKindSort(): void {
  kindSort.value = kindSort.value === null ? "asc" : kindSort.value === "asc" ? "desc" : null
}

const kindSortIcon = computed<string>(() =>
  kindSort.value === "asc"
    ? "mdi-sort-ascending"
    : kindSort.value === "desc"
      ? "mdi-sort-descending"
      : "mdi-sort",
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
    const [eventResp] = await Promise.all([
      findEventById({path: {id: eventId.value}}),
      loadSignUps(),
    ])

    event.value = eventResp.data
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

function totalForQuestion(question: QuestionResponse): number[] | undefined {
  if (!question) return
  if (question.type === QuestionType.CHECKBOX || question.type === QuestionType.RADIO) {
    const numOptions = question.choiceLabels?.length ?? 0
    const counts = Array.from({length: numOptions}, () => 0)

    rows.value.forEach((r: SignUpRow) => {
      const selections: boolean[] = r.answers.get(question.id!)?.optionSelections ?? []
      for (let i = 0; i < numOptions; i++) {
        if (selections[i]) {
          counts[i] = (counts[i] ?? 0) + 1
        }
      }
    })

    return counts
  }
}

function hasAnswerForQuestion(row: SignUpRow, question: QuestionResponse): boolean {
  return row.answers.has(question.id!)
}

function selectionState(
  row: SignUpRow,
  question: QuestionResponse,
  optionIdx: number,
): "checked" | "unchecked" | "missing" {
  const answer = row.answers.get(question.id!)
  if (!answer) return "missing"
  const selections = answer.optionSelections ?? []
  if (selections.length !== (question.choiceLabels?.length ?? 0)) return "missing"
  return selections[optionIdx] ? "checked" : "unchecked"
}

function isOpenAnswerEmpty(row: SignUpRow, question: QuestionResponse): boolean {
  const answer = row.answers.get(question.id!)
  const text = answer?.textResponse
  return !answer || typeof text !== "string" || text.trim().length === 0
}

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
    <top-banner :title="event?.title ? event.title + ' sign-ups' : 'Sign-ups'" />

    <div class="mx-3">
      <div
        class="mx-auto my-10"
        style="max-width: 1100px"
      >
        <div class="d-flex justify-end mb-4">
          <v-btn
            color="primary"
            data-testid="export-csv-btn"
            :disabled="respondents.length === 0"
            prepend-icon="mdi-download"
            variant="flat"
            @click="exportCsv"
          >
            Export as CSV
          </v-btn>
        </div>

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

        <v-card class="mb-10">
          <v-card-title class="text-h5">
            Respondents
          </v-card-title>
          <v-card-text>
            <v-table
              class="rounded-lg attendees-table"
              density="comfortable"
            >
              <thead>
                <tr>
                  <th class="w-1/10">
                    #
                  </th>
                  <th class="w-3/10">
                    Name
                  </th>
                  <th class="w-2/10">
                    <button
                      class="kind-sort"
                      data-testid="signups-kind-sort"
                      type="button"
                      @click="toggleKindSort"
                    >
                      Kind
                      <v-icon
                        :icon="kindSortIcon"
                        size="16"
                      />
                    </button>
                  </th>
                  <th class="w-2/10">
                    Discord
                  </th>
                  <th class="w-2/10">
                    Email
                  </th>
                  <th class="w-2/10">
                    Phone
                  </th>
                  <th
                    v-if="mayManageSignUps"
                    class="w-1/10 text-right"
                  >
                    Actions
                  </th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="(row, idx) in respondents"
                  :key="row.signUp.id"
                >
                  <td>{{ idx + 1 }}</td>
                  <td>{{ row.person.name }}</td>
                  <td :data-testid="`signup-kind-${row.signUp.id}`">
                    {{ signUpKindLabel(row.signUp.kind) }}
                  </td>
                  <td class="font-mono">
                    {{ row.person.discord }}
                  </td>
                  <td class="font-mono">
                    {{ row.person.email }}
                  </td>
                  <td class="font-mono">
                    {{ row.person.phoneNumber }}
                  </td>
                  <td
                    v-if="mayManageSignUps"
                    class="text-right"
                  >
                    <v-btn
                      :data-testid="`signup-edit-btn-${row.signUp.id}`"
                      density="comfortable"
                      icon="mdi-pencil"
                      size="small"
                      variant="text"
                      @click="askToEdit(row)"
                    />
                    <v-btn
                      color="error"
                      :data-testid="`signup-remove-btn-${row.signUp.id}`"
                      density="comfortable"
                      icon="mdi-delete"
                      size="small"
                      variant="text"
                      @click="askToRemove(row)"
                    />
                  </td>
                </tr>
              </tbody>
            </v-table>
          </v-card-text>
        </v-card>

        <v-card
          v-for="question in sortedQuestions"
          :key="question.id!"
          class="mb-8"
        >
          <v-card-title
            class="text-h6 text-wrap flex items-center gap-2"
            style="word-break: break-word"
          >
            <span>{{ question.idx + 1 }}. {{ question.label }}</span>
          </v-card-title>

          <v-card-text>
            <!-- OPEN questions -->
            <v-table
              v-if="question.type === QuestionType.OPEN"
              class="rounded-lg open-table"
              density="comfortable"
            >
              <thead>
                <tr>
                  <th class="w-1/4">
                    Name
                  </th>
                  <th>Answer</th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="row in respondents"
                  :key="question.id! + '-' + row.signUp.id"
                >
                  <td>{{ row.person.name }}</td>
                  <td class="whitespace-pre-wrap">
                    <template v-if="!hasAnswerForQuestion(row, question)">
                      <span class="text-medium-emphasis font-italic">— not yet answered —</span>
                    </template>
                    <template v-else-if="isOpenAnswerEmpty(row, question)">
                      <span class="text-medium-emphasis font-italic">(left blank)</span>
                    </template>
                    <template v-else>
                      {{ row.answers.get(question.id!)?.textResponse }}
                    </template>
                  </td>
                </tr>
              </tbody>
            </v-table>

            <!-- Choice questions: RADIO or CHECKBOX -->
            <v-table
              v-else
              class="rounded-lg radio-table"
              density="compact"
            >
              <thead>
                <tr>
                  <th class="sticky-col">
                    Name
                  </th>
                  <th
                    v-for="(opt, idx) in (question.choiceLabels ?? [])"
                    :key="idx"
                    class="text-center choice-col"
                  >
                    <v-tooltip
                      :text="opt"
                      location="bottom"
                    >
                      <template #activator="{ props }">
                        <span
                          v-bind="props"
                          class="choice-label"
                        >{{ opt }}</span>
                      </template>
                    </v-tooltip>
                  </th>
                </tr>
              </thead>

              <tbody>
                <tr
                  v-for="row in respondents"
                  :key="question.id! + '-' + row.signUp.id"
                >
                  <td class="sticky-col">
                    {{ row.person.name }}
                  </td>
                  <td
                    v-for="(opt, idx) in (question.choiceLabels ?? [])"
                    :key="idx"
                    class="text-center check-cell"
                  >
                    <template v-if="selectionState(row, question, idx) === 'checked'">
                      <v-icon
                        icon="mdi-check-bold"
                        size="18"
                        color="success"
                      />
                    </template>
                    <template v-else-if="selectionState(row, question, idx) === 'unchecked'">
                      <v-icon
                        icon="mdi-close-thick"
                        size="18"
                        class="text-medium-emphasis"
                      />
                    </template>
                    <template v-else>
                      <v-tooltip
                        text="No answer yet — respondent hasn't edited their sign-up since this question was added"
                        location="top"
                      >
                        <template #activator="{ props }">
                          <v-icon
                            v-bind="props"
                            icon="mdi-minus"
                            size="18"
                            class="text-medium-emphasis"
                          />
                        </template>
                      </v-tooltip>
                    </template>
                  </td>
                </tr>
              </tbody>

              <tfoot>
                <tr>
                  <td class="font-weight-bold sticky-col">
                    Totals
                  </td>
                  <td
                    v-for="(opt, idx) in (question.choiceLabels ?? [])"
                    :key="'t-' + idx"
                    class="text-center font-weight-bold"
                  >
                    {{ (totalForQuestion(question) ?? [])[idx] ?? 0 }}
                  </td>
                </tr>
              </tfoot>
            </v-table>
          </v-card-text>
        </v-card>
      </div>
    </div>
  </v-main>
</template>

<style lang="scss" scoped>
.attendees-table, .radio-table, .open-table {
  thead th {
    position: sticky;
    top: 0;
    background: rgb(var(--v-theme-surface));
    z-index: 2;
  }
}

.radio-table .check-cell,
.radio-table .choice-col {
  width: 96px;
  max-width: 96px;
  overflow: hidden;
}

.choice-label {
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
  cursor: default;
}

.sticky-col {
  position: sticky;
  left: 0;
  z-index: 1;
  background: rgb(var(--v-theme-surface));
}

.kind-sort {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font: inherit;
  cursor: pointer;
}

.whitespace-pre-wrap {
  white-space: pre-wrap;
}

tbody tr:nth-child(odd) {
  background: rgba(0, 0, 0, 0.02);
}
</style>
