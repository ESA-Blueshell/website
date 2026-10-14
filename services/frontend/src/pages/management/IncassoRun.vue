<script lang="ts" setup>
/* Collecting a period's contributions by incasso, as four steps: whose contribution is collected,
   what each pays, the collection date and statement text with a check of every collection, then
   the run waiting for ING. Nobody is emailed before the button on the third step. Opened with a
   run's id, the page shows that run's last step. */
import {computed, onMounted, ref} from "vue"
import {useRoute, useRouter} from "vue-router"
import ConfirmDialog from "@/components/island/ConfirmDialog.vue"
import CutButton from "@/components/island/CutButton.vue"
import DateInput from "@/components/island/DateInput.vue"
import FormField from "@/components/island/FormField.vue"
import ModalDialog from "@/components/island/ModalDialog.vue"
import NoticeBox from "@/components/island/NoticeBox.vue"
import SearchPicker from "@/components/island/SearchPicker.vue"
import StateMark from "@/components/island/StateMark.vue"
import StateTag from "@/components/island/StateTag.vue"
import TextInput from "@/components/island/TextInput.vue"
import ListHead from "@/components/management/ListHead.vue"
import MandatePanel from "@/components/management/MandatePanel.vue"
import ManagementPage from "@/components/management/ManagementPage.vue"
import ManagementTable, {type TableColumn} from "@/components/management/ManagementTable.vue"
import PersonLink from "@/components/management/PersonLink.vue"
import MiniButton from "@/components/management/MiniButton.vue"
import PairList from "@/components/management/PairList.vue"
import RowCheck from "@/components/management/RowCheck.vue"
import StepStrip from "@/components/island/StepStrip.vue"
import EmailPreviewDialog from "@/components/common/modals/EmailPreviewDialog.vue"
import {useEmailPreview} from "@/composables/useEmailPreview"
import {
  ContributionEmailKind,
  type ContributionPeriodResponse,
  IncassoLeftOut,
  type IncassoCandidate,
  type IncassoRunView,
  dayName,
  defaultStatementText,
  fetchIncassoFile,
  incassoFileName,
  leftOutGroups,
  leftOutHelp,
  leftOutLabels,
  localDay,
  listPeriods,
  maskedIban,
  readIncassoPlan,
  readIncassoRun,
  readOneEmail,
  renamedForIng,
  saveStorno,
  saveSubmitted,
  startIncasso,
} from "@/domains/contribution"
import {$handleNetworkError} from "@/plugins/handleNetworkError"
import {BulkFeeType} from "@/utils/bulkRow"
import {effectiveAmount, feeTypeLabels} from "@/utils/feePreview"

defineOptions({name: "IncassoRunPage"})

const STEPS = ["Who", "Amounts", "Date and check", "Done"] as const
const STATEMENT_TEXT_MAX = 140

const route = useRoute()
const router = useRouter()
const periodId = computed(() => Number(route.params.periodId))
const back = computed(() => `/management/contributions/${periodId.value}`)

const period = ref<ContributionPeriodResponse | null>(null)
const candidates = ref<IncassoCandidate[]>([])
const ticked = ref<Record<number, boolean>>({})
const fees = ref<Record<number, BulkFeeType>>({})
const collectionDate = ref("")
const statementText = ref("")
const step = ref(0)
const loaded = ref(false)
const starting = ref(false)
const failure = ref<string | null>(null)
const run = ref<IncassoRunView | null>(null)
const fetching = ref<number | null>(null)
const submitting = ref(false)
const doneFailure = ref<string | null>(null)
// Who could be collected from could not be read, which is not the same as nobody.
const unread = ref(false)
const planFailure = ref<string | null>(null)

const feeOptions = (Object.values(BulkFeeType) as BulkFeeType[]).map((value) => ({key: value, label: feeTypeLabels[value]}))

const byName = {key: "name", label: "Member", sortBy: (one: {name: string}) => one.name}
const byMandate = {key: "mandate", label: "Mandate", sortBy: (one: {mandateReference?: string | null}) => one.mandateReference}
const WHO_COLUMNS: TableColumn<IncassoCandidate>[] = [
  byName,
  {key: "account", label: "Account", sortBy: (one) => maskedIban(one)},
  byMandate,
  {key: "why", label: "Why", wrap: true, sortBy: (one) => (one.leftOut ? leftOutLabels[one.leftOut] : null)},
]
const AMOUNT_COLUMNS: TableColumn<IncassoCandidate>[] = [
  byName,
  {key: "since", label: "Membership started", sortBy: (one) => one.memberSince},
  {key: "fee", label: "Fee type", sortBy: (one) => feeTypeLabels[feeOf(one)]},
  {key: "amount", label: "Amount", sortBy: (one) => amountOf(one)},
]
const COLLECT_COLUMNS: TableColumn<IncassoCandidate>[] = [
  byName,
  {key: "account", label: "Account", sortBy: (one) => maskedIban(one)},
  byMandate,
  {key: "fee", label: "Fee type", sortBy: (one) => feeTypeLabels[feeOf(one)]},
  {key: "amount", label: "Amount", sortBy: (one) => amountOf(one)},
]
const DONE_COLUMNS: TableColumn<IncassoRunView["collections"][number]>[] = [
  byName,
  {key: "account", label: "Account", sortBy: (one) => maskedIban(one)},
  byMandate,
  {key: "amount", label: "Amount", sortBy: (one) => one.amount},
]
// TWIN: `NOTICE_DAYS` in the api's IncassoRuns; members are told at least this long ahead.
const NOTICE_DAYS = 14
const earliest = localDay(NOTICE_DAYS)
const euro = (amount: number) => `€ ${amount.toFixed(2)}`

const collectable = computed(() => candidates.value.filter((one) => !one.leftOut))
const chosen = computed(() => collectable.value.filter((one) => ticked.value[one.userId]))
const leftOut = computed(() => [
  ...candidates.value.filter((one) => one.leftOut),
  ...collectable.value.filter((one) => !ticked.value[one.userId]),
])
const groups = computed(() => leftOutGroups(candidates.value))
const feeOf = (one: IncassoCandidate) => fees.value[one.userId] ?? one.feeType ?? BulkFeeType.FULL_YEAR_FEE
// The api's own amount, unless the board chose another fee type for this member.
const amountOf = (one: IncassoCandidate) =>
  (fees.value[one.userId] ? effectiveAmount(feeOf(one), period.value) : one.amount) ?? 0
const total = computed(() => chosen.value.reduce((sum, one) => sum + amountOf(one), 0))
const renamed = computed(() => renamedForIng(chosen.value))
const dateValid = computed(() => collectionDate.value >= earliest)
const textValid = computed(() => statementText.value.trim() !== "" && statementText.value.length <= STATEMENT_TEXT_MAX)
const withMandate = computed(() => candidates.value.filter((one) => one.ibanLastTwo).length)
/* A member left out for want of bank details gets them here, from their paper mandate, and is
   then read again: with details on file they can be collected from in this run. */
const detailsFor = ref<IncassoCandidate | null>(null)
const detailsAdded = async () => {
  const added = detailsFor.value
  detailsFor.value = null
  const plan = await readIncassoPlan(periodId.value)
  if (!plan) {
    planFailure.value = "The members could not be read again. Reload the page to see the details just added."
    return
  }
  planFailure.value = null
  candidates.value = plan
  const now = plan.find((one) => one.userId === added?.userId)
  if (now && !now.leftOut) ticked.value = {...ticked.value, [now.userId]: true}
}

const withoutDetails = computed(() => candidates.value.filter((one) => one.leftOut === IncassoLeftOut.NO_BANK_DETAILS).length)

const {open: previewOpen, loading: previewLoading, error: previewError, preview, show: showPreview} = useEmailPreview()

/** The notification that member gets, rendered by the api with the date and fee chosen here. */
const previewFor = (one: IncassoCandidate) => showPreview(async () => {
  const {data} = await readOneEmail({
    kind: ContributionEmailKind.INCASSO_NOTIFICATION,
    contributionPeriodId: periodId.value,
    userId: one.userId,
    date: collectionDate.value,
    feeType: feeOf(one),
  })
  return data ? {subject: data.subject, html: data.html, recipientEmail: data.recipientEmail, recipientName: data.recipientName} : null
})

const start = async () => {
  if (starting.value || chosen.value.length === 0 || !dateValid.value || !textValid.value) return
  starting.value = true
  failure.value = null
  const overrides = Object.fromEntries(chosen.value
    .filter((one) => fees.value[one.userId] && fees.value[one.userId] !== one.feeType)
    .map((one) => [one.userId, fees.value[one.userId]!]))
  const answered = await startIncasso(periodId.value, {
    userIds: chosen.value.map((one) => one.userId),
    feeTypeOverrides: overrides,
    collectionDate: collectionDate.value,
    statementText: statementText.value,
  })
  starting.value = false
  if (!answered.ok) {
    failure.value = answered.reason
    return
  }
  run.value = answered.saved
  step.value = 3
  // The address names the run, so going back to it later lands on this step.
  void router.replace(`/management/contributions/${periodId.value}/incasso/${answered.saved.id}`)
}

/** Saves one of the run's files for ING, made by the api as it answers. */
const download = async (part: number) => {
  if (!run.value || fetching.value !== null) return
  fetching.value = part
  doneFailure.value = null
  const answered = await fetchIncassoFile(run.value.id, part)
  fetching.value = null
  if (!answered.ok) {
    doneFailure.value = answered.reason
    return
  }
  const url = URL.createObjectURL(answered.file)
  const anchor = document.createElement("a")
  anchor.href = url
  anchor.download = incassoFileName(run.value.collectionDate, part, run.value.fileParts)
  document.body.appendChild(anchor)
  anchor.click()
  anchor.remove()
  // Later rather than at once: Safari drops a download whose address is revoked before it starts.
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}

/* A storno is recorded once the bank took a collection back; the member owes it again. */
const stornoFor = ref<IncassoRunView["collections"][number] | null>(null)
const recordingStorno = ref(false)
const storno = async () => {
  if (!run.value || !stornoFor.value || recordingStorno.value) return
  recordingStorno.value = true
  doneFailure.value = null
  const answered = await saveStorno(run.value.id, stornoFor.value.userId)
  recordingStorno.value = false
  stornoFor.value = null
  if (!answered.ok) {
    doneFailure.value = answered.reason
    return
  }
  run.value = answered.saved
}

const submitted = async () => {
  if (!run.value || submitting.value) return
  submitting.value = true
  doneFailure.value = null
  const answered = await saveSubmitted(run.value.id)
  submitting.value = false
  if (!answered.ok) {
    doneFailure.value = answered.reason
    return
  }
  run.value = answered.saved
}

onMounted(async () => {
  try {
    const runId = Number(route.params.runId)
    const [periods, plan, opened] = await Promise.all([
      listPeriods(),
      runId ? Promise.resolve([] as IncassoCandidate[]) : readIncassoPlan(periodId.value),
      runId ? readIncassoRun(runId) : Promise.resolve(null),
    ])
    period.value = periods.find((one) => one.id === periodId.value) ?? null
    unread.value = plan == null || (runId > 0 && opened == null)
    candidates.value = plan ?? []
    ticked.value = Object.fromEntries(candidates.value.filter((one) => !one.leftOut).map((one) => [one.userId, true]))
    if (period.value) statementText.value = defaultStatementText(period.value.startDate, period.value.endDate)
    if (opened) {
      run.value = opened
      step.value = 3
    }
  } catch (error) {
    unread.value = true
    $handleNetworkError(error)
  } finally {
    loaded.value = true
  }
})

const leftOutPairs = computed(() => [
  ...groups.value.map((group) => ({label: leftOutLabels[group.reason], value: group.names.join(", ")})),
  ...(collectable.value.length > chosen.value.length
    ? [{label: "Not chosen", value: collectable.value.filter((one) => !ticked.value[one.userId]).map((one) => one.name).join(", ")}]
    : []),
])
</script>


<template>
  <management-page
    :back="{to: back, label: 'Contributions'}"
    eyebrow="Contributions"
    testid="incasso-run"
    title="Incassos"
  >
    <template #lede>
      Collect what members on incasso owe: email them the incasso notification, then put the collection in ING for that
      same date.
    </template>

    <step-strip
      :current="step"
      :steps="STEPS"
      testid="incasso-run-steps"
    />

    <p
      v-if="loaded && unread"
      class="incasso__note"
      data-testid="incasso-run-unread"
      role="alert"
    >
      The incasso could not be read. Try again in a moment.
    </p>
    <p
      v-else-if="loaded && step < 3 && candidates.length === 0"
      class="incasso__note"
      data-testid="incasso-run-empty"
    >
      Nobody in this period pays by incasso.
    </p>

    <section
      v-else-if="step === 0"
      class="incasso__stage"
      data-testid="incasso-run-who"
    >
      <h2 class="incasso__heading">
        Whose contribution is collected
      </h2>
      <p
        v-if="planFailure"
        class="incasso__note"
        data-testid="incasso-run-plan-failure"
        role="alert"
      >
        {{ planFailure }}
      </p>
      <p class="incasso__tags">
        <state-tag
          testid="incasso-run-with-mandate"
          tone="ok"
        >
          {{ withMandate }} with a mandate
        </state-tag>
        <state-tag
          v-if="withoutDetails"
          tone="warn"
        >
          {{ withoutDetails }} on incasso without bank details
        </state-tag>
      </p>
      <management-table
        :columns="WHO_COLUMNS"
        :row-key="(one) => one.userId"
        :row-testid="(one) => `incasso-run-row-${one.userId}`"
        search-label="Search members"
        :search-text="(one) => one.name"
        :rows="candidates"
      >
        <template #check="{row}">
          <row-check
            v-if="!row.leftOut"
            :checked="ticked[row.userId] === true"
            :label="`Include ${row.name}`"
            :testid="`incasso-run-tick-${row.userId}`"
            @toggle="ticked[row.userId] = !ticked[row.userId]"
          />
        </template>
        <template #name="{row}">
          <person-link
            :name="row.name"
            :user-id="row.userId"
          />
        </template>
        <template #account="{row}">
          <span class="incasso__account">{{ maskedIban(row) }}</span>
        </template>
        <template #mandate="{row}">
          <span :class="{'mg-quiet': !row.mandateReference}">{{ row.mandateReference ? `${row.mandateReference}, signed ${dayName(row.mandateSignedOn)}` : "None" }}</span>
        </template>
        <template #why="{row}">
          <span
            v-if="row.leftOut"
            :data-testid="`incasso-run-left-out-${row.userId}`"
          >
            <state-mark kind="not-created">{{ leftOutLabels[row.leftOut] }}</state-mark>
            <span
              v-if="leftOutHelp[row.leftOut]"
              class="mg-why"
            >{{ leftOutHelp[row.leftOut] }}</span>
            <mini-button
              v-if="row.leftOut === IncassoLeftOut.NO_BANK_DETAILS"
              :testid="`incasso-run-add-details-${row.userId}`"
              @click="detailsFor = row"
            >
              Add incasso details
            </mini-button>
          </span>
        </template>
      </management-table>
    </section>

    <section
      v-else-if="step === 1"
      class="incasso__stage"
      data-testid="incasso-run-amounts"
    >
      <h2 class="incasso__heading">
        What is collected
      </h2>
      <notice-box v-if="period">
        Half-year cutoff {{ dayName(period.halfYearCutoffDate) }}. A regular membership starting after it pays the
        half-year fee; one starting on it or before pays the full year. Alumni pay the alumni fee.
      </notice-box>
      <management-table
        :columns="AMOUNT_COLUMNS"
        :row-key="(one) => one.userId"
        search-label="Search members"
        :search-text="(one) => one.name"
        :rows="chosen"
      >
        <template #name="{row}">
          <person-link
            :name="row.name"
            :user-id="row.userId"
          />
        </template>
        <template #since="{row}">
          {{ dayName(row.memberSince) }}
        </template>
        <template #fee="{row}">
          <search-picker
            class="incasso__fee"
            compact
            :options="feeOptions"
            :selected-key="feeOf(row)"
            :testid-prefix="`incasso-run-fee-${row.userId}`"
            @pick="fees[row.userId] = $event as BulkFeeType"
          />
        </template>
        <template #amount="{row}">
          {{ euro(amountOf(row)) }}
        </template>
      </management-table>
    </section>

    <section
      v-else-if="step === 2"
      class="incasso__stage"
      data-testid="incasso-run-check"
    >
      <h2 class="incasso__heading">
        {{ euro(total) }} will be collected from {{ chosen.length }} member{{ chosen.length === 1 ? "" : "s" }}
      </h2>
      <div class="incasso__fields">
        <form-field
          v-slot="field"
          :error="collectionDate && !dateValid ? `Members get the notice at least ${NOTICE_DAYS} days before the collection, so pick ${dayName(earliest)} or later.` : ''"
          label="Collection date"
          testid="incasso-run-date"
        >
          <date-input
            v-model="collectionDate"
            :control-id="field.controlId"
            :invalid="field.invalid"
            :min="earliest"
          />
        </form-field>
        <form-field
          v-slot="field"
          :error="statementText.length > STATEMENT_TEXT_MAX ? `At most ${STATEMENT_TEXT_MAX} characters.` : ''"
          hint="ING leaves out accents and characters it does not take."
          label="On their bank statement"
          testid="incasso-run-statement"
        >
          <text-input
            v-model="statementText"
            :control-id="field.controlId"
            :invalid="field.invalid"
          />
        </form-field>
      </div>

      <list-head :title="`Will collect · ${chosen.length}`">
        {{ dateValid ? `On ${dayName(collectionDate)} · ` : "" }}{{ euro(total) }} in total
      </list-head>
      <management-table
        :columns="COLLECT_COLUMNS"
        :row-key="(one) => one.userId"
        :row-testid="(one) => `incasso-run-collect-${one.userId}`"
        search-label="Search members"
        :search-text="(one) => one.name"
        :rows="chosen"
      >
        <template #name="{row}">
          <person-link
            :name="row.name"
            :user-id="row.userId"
          />
        </template>
        <template #account="{row}">
          <span class="incasso__account">{{ maskedIban(row) }}</span>
        </template>
        <template #mandate="{row}">
          {{ row.mandateReference }} · signed {{ dayName(row.mandateSignedOn) }}
        </template>
        <template #fee="{row}">
          {{ feeTypeLabels[feeOf(row)] }}
        </template>
        <template #amount="{row}">
          {{ euro(amountOf(row)) }}
        </template>
        <template #acts="{row}">
          <mini-button
            :disabled="!dateValid"
            :testid="`incasso-run-preview-${row.userId}`"
            @click="previewFor(row)"
          >
            Preview
          </mini-button>
        </template>
      </management-table>

      <notice-box
        v-if="renamed.length"
        testid="incasso-run-renamed"
        :title="`${renamed.length} name${renamed.length === 1 ? '' : 's'} go${renamed.length === 1 ? 'es' : ''} to ING without accents`"
        tone="warning"
      >
        {{ renamed.map((one) => `${one.name} as ${one.ingName}`).join(", ") }}. ING refuses accents and other special
        characters in its file.
      </notice-box>

      <template v-if="leftOut.length">
        <list-head :title="`Left out · ${leftOut.length}`" />
        <pair-list
          :pairs="leftOutPairs"
          testid="incasso-run-left-out"
        />
      </template>

      <p
        v-if="failure"
        class="incasso__failure"
        data-testid="incasso-run-failure"
        role="alert"
      >
        {{ failure }}
      </p>
    </section>

    <section
      v-else-if="run"
      class="incasso__stage"
      data-testid="incasso-run-done"
    >
      <h2 class="incasso__heading">
        {{ run.collections.length }} incasso notification{{ run.collections.length === 1 ? "" : "s" }} sent
      </h2>
      <template v-if="!run.submittedAt">
        <p
          class="incasso__lede"
          data-testid="incasso-run-waiting"
        >
          One thing left: put the collection in ING, so the money is actually taken on {{ dayName(run.collectionDate) }}.
        </p>
        <notice-box
          testid="incasso-run-file"
          title="Incasso file for ING"
        >
          <p>
            {{ incassoFileName(run.collectionDate, 1, run.fileParts) }} · ING's incasso batch template, filled in:
            {{ run.collections.length }} collection{{ run.collections.length === 1 ? "" : "s" }}, {{ euro(run.total) }}, on
            {{ dayName(run.collectionDate) }}, Core, doorlopend.
            <template v-if="run.fileParts > 1">
              ING takes at most 1000 collections a file, so there are {{ run.fileParts }}.
            </template>
          </p>
          <div class="incasso__acts">
            <cut-button
              v-for="part in run.fileParts"
              :key="part"
              :disabled="fetching !== null"
              :testid="`incasso-run-download-${part}`"
              tone="solid"
              @click="download(part)"
            >
              {{ run.fileParts > 1 ? `Download file ${part} of ${run.fileParts}` : "Download incasso file" }}
            </cut-button>
          </div>
        </notice-box>
        <ol class="incasso__how">
          <li>In Mijn ING Zakelijk, go to Incasso, then Excel importeren, and upload the file.</li>
          <li>Check it says {{ dayName(run.collectionDate) }} and {{ euro(run.total) }}, then confirm it there.</li>
          <li>Come back and press Submitted to ING.</li>
        </ol>
      </template>
      <p
        v-else
        class="incasso__lede"
        data-testid="incasso-run-in-ing"
      >
        Submitted to ING on {{ dayName(run.submittedAt.slice(0, 10)) }}.
      </p>
      <p
        v-if="doneFailure"
        class="incasso__failure"
        data-testid="incasso-run-done-failure"
        role="alert"
      >
        {{ doneFailure }}
      </p>

      <list-head :title="`Collected · ${run.collections.length}`">
        {{ euro(run.total) }} on {{ dayName(run.collectionDate) }}: {{ run.statementText }}
      </list-head>
      <management-table
        :columns="DONE_COLUMNS"
        :row-key="(one) => one.userId"
        search-label="Search members"
        :search-text="(one) => one.name"
        :rows="run.collections"
      >
        <template #name="{row}">
          <person-link
            :name="row.name"
            :user-id="row.userId"
          />
        </template>
        <template #account="{row}">
          <span class="incasso__account">{{ maskedIban(row) }}</span>
        </template>
        <template #mandate="{row}">
          {{ row.mandateReference }} · signed {{ dayName(row.mandateSignedOn) }}
        </template>
        <template #amount="{row}">
          <state-mark
            v-if="row.stornoOn"
            kind="extra"
            :testid="`incasso-run-storno-${row.userId}`"
          >
            Storno on {{ dayName(row.stornoOn) }}
          </state-mark>
          <template v-else>
            {{ euro(row.amount) }}
          </template>
        </template>
        <template #acts="{row}">
          <mini-button
            v-if="run.submittedAt && !row.stornoOn"
            :testid="`incasso-run-storno-btn-${row.userId}`"
            @click="stornoFor = row"
          >
            Record storno
          </mini-button>
        </template>
      </management-table>
      <confirm-dialog
        confirm-label="Record storno"
        :open="stornoFor !== null"
        :question="stornoFor ? `The bank took back the collection from ${stornoFor.name}. They owe the contribution again, and their incasso stays on. You can send them a payment reminder.` : ''"
        testid="incasso-run-storno-dialog"
        title="Record a storno?"
        :working="recordingStorno"
        working-label="Recording"
        @confirm="storno"
        @update:open="(open) => { if (!open) stornoFor = null }"
      />

      <div class="incasso__acts">
        <cut-button
          v-if="!run.submittedAt"
          :disabled="submitting"
          testid="incasso-run-submitted"
          @click="submitted"
        >
          Submitted to ING
        </cut-button>
        <cut-button
          :href="back"
          tone="quiet"
        >
          Back to contributions
        </cut-button>
      </div>
    </section>

    <div
      v-if="candidates.length > 0 && step < 3"
      class="incasso__acts"
    >
      <cut-button
        v-if="step < 2"
        :disabled="chosen.length === 0"
        testid="incasso-run-next"
        tone="solid"
        @click="step += 1"
      >
        {{ step === 0 ? "Next: amounts" : "Next: date and check" }}
      </cut-button>
      <cut-button
        v-else
        :disabled="chosen.length === 0 || !dateValid || !textValid || starting"
        testid="incasso-run-start"
        tone="solid"
        @click="start"
      >
        Email the incasso notification to {{ chosen.length }} member{{ chosen.length === 1 ? "" : "s" }}
      </cut-button>
      <cut-button
        v-if="step > 0"
        testid="incasso-run-previous"
        tone="quiet"
        @click="step -= 1"
      >
        Back
      </cut-button>
      <cut-button
        v-else
        :href="back"
        tone="quiet"
      >
        Cancel
      </cut-button>
    </div>

    <email-preview-dialog
      v-model="previewOpen"
      :error="previewError"
      :loading="previewLoading"
      :preview="preview"
      title="Incasso notification"
    />
    <modal-dialog
      :open="detailsFor !== null"
      testid="incasso-run-details-dialog"
      :title="`Incasso details of ${detailsFor?.name ?? ''}`"
      @update:open="detailsFor = null"
    >
      <mandate-panel
        v-if="detailsFor"
        :user-id="detailsFor.userId"
        start-open
        @changed="detailsAdded"
      />
    </modal-dialog>
  </management-page>
</template>

<style scoped>
.incasso__stage {
  display: flex;
  flex-direction: column;
  gap: 1.15rem;
  padding-top: 1.6rem;
}

.incasso__heading {
  font-size: 2.1rem;
  line-height: 1.02;
  text-transform: uppercase;
}

.incasso__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
}

.incasso__account {
  font-variant-numeric: tabular-nums;
  letter-spacing: 0.03em;
}

.incasso__fields {
  display: grid;
  grid-template-columns: 16rem minmax(0, 32rem);
  gap: 1rem;
}

.incasso__fee {
  min-width: 12rem;
}

.incasso__lede {
  max-width: 38rem;
  font-size: 1.02rem;
  line-height: 1.6;
  color: color-mix(in oklab, var(--color-chalk) 86%, transparent);
}

.incasso__how {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  padding-left: 1.2rem;
  font-size: 0.92rem;
  list-style: decimal;
  color: var(--color-ash);
}

.incasso__acts {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.6rem;
  padding-top: 1rem;
}

.incasso__note {
  margin-top: 1.4rem;
  color: var(--color-ash);
}

.incasso__failure {
  color: var(--color-danger);
}

@media (--phone) {
  .incasso__fields {
    grid-template-columns: minmax(0, 1fr);
  }

  .incasso__heading {
    font-size: 1.5rem;
  }
}
</style>
