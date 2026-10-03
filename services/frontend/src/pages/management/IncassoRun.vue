<script lang="ts" setup>
/* Collecting a period's contributions by incasso, as four steps: whose contribution is collected,
   what each pays, the collection date and statement text with a check of every collection, then
   the run waiting for ING. Nobody is emailed before the button on the third step. Opened with a
   run's id, the page shows that run's last step. */
import {computed, onMounted, ref} from "vue"
import {useRoute, useRouter} from "vue-router"
import FormField from "@/components/island/FormField.vue"
import DateInput from "@/components/island/DateInput.vue"
import NoticeBox from "@/components/island/NoticeBox.vue"
import TextInput from "@/components/island/TextInput.vue"
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
  listPeriods,
  maskedIban,
  readIncassoPlan,
  readIncassoRun,
  readOneEmail,
  renamedForIng,
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

const feeOptions = (Object.values(BulkFeeType) as BulkFeeType[]).map((value) => ({title: feeTypeLabels[value], value}))
const tomorrow = new Date(Date.now() + 86_400_000).toISOString().slice(0, 10)
const euro = (amount: number) => `€ ${amount.toFixed(2)}`

const collectable = computed(() => candidates.value.filter((one) => !one.leftOut))
const chosen = computed(() => collectable.value.filter((one) => ticked.value[one.userId]))
const leftOut = computed(() => [
  ...candidates.value.filter((one) => one.leftOut),
  ...collectable.value.filter((one) => !ticked.value[one.userId]),
])
const groups = computed(() => leftOutGroups(candidates.value))
const feeOf = (one: IncassoCandidate) => fees.value[one.userId] ?? one.feeType ?? BulkFeeType.FULL_YEAR_FEE
const amountOf = (one: IncassoCandidate) => effectiveAmount(feeOf(one), period.value) ?? 0
const total = computed(() => chosen.value.reduce((sum, one) => sum + amountOf(one), 0))
const renamed = computed(() => renamedForIng(chosen.value))
const dateValid = computed(() => collectionDate.value >= tomorrow)
const textValid = computed(() => statementText.value.trim() !== "" && statementText.value.length <= STATEMENT_TEXT_MAX)
const withMandate = computed(() => candidates.value.filter((one) => one.ibanLastTwo).length)
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
  URL.revokeObjectURL(url)
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
      runId ? Promise.resolve([]) : readIncassoPlan(periodId.value),
      runId ? readIncassoRun(runId) : Promise.resolve(null),
    ])
    period.value = periods.find((one) => one.id === periodId.value) ?? null
    candidates.value = plan
    ticked.value = Object.fromEntries(plan.filter((one) => !one.leftOut).map((one) => [one.userId, true]))
    if (period.value) statementText.value = defaultStatementText(period.value.startDate, period.value.endDate)
    if (opened) {
      run.value = opened
      step.value = 3
    }
  } catch (error) {
    $handleNetworkError(error)
  } finally {
    loaded.value = true
  }
})
</script>

<template>
  <div
    class="incasso"
    data-testid="incasso-run"
  >
    <router-link
      class="incasso__back"
      :to="back"
    >
      Contributions
    </router-link>
    <h1 class="incasso__title">
      Incassos
    </h1>
    <p class="incasso__note">
      Collect what members on incasso owe: email them the incasso notification, then put the collection in ING for that same date.
    </p>

    <ol
      class="incasso__steps"
      data-testid="incasso-run-steps"
    >
      <li
        v-for="(name, index) in STEPS"
        :key="name"
        :aria-current="step === index ? 'step' : undefined"
        :class="{'incasso__step--on': step === index}"
      >
        {{ index + 1 }}. {{ name }}
      </li>
    </ol>

    <p
      v-if="loaded && step < 3 && candidates.length === 0"
      class="incasso__note"
      data-testid="incasso-run-empty"
    >
      Nobody in this period pays by incasso.
    </p>

    <section
      v-else-if="step === 0"
      data-testid="incasso-run-who"
    >
      <h2>Whose contribution is collected</h2>
      <p class="incasso__tags">
        <span data-testid="incasso-run-with-mandate">{{ withMandate }} with a mandate</span>
        <span
          v-if="withoutDetails"
          class="incasso__warn"
        >{{ withoutDetails }} on incasso without bank details</span>
      </p>
      <ul class="incasso__rows">
        <li
          v-for="one in candidates"
          :key="one.userId"
          :data-testid="`incasso-run-row-${one.userId}`"
        >
          <label v-if="!one.leftOut">
            <input
              v-model="ticked[one.userId]"
              :data-testid="`incasso-run-tick-${one.userId}`"
              type="checkbox"
            >
            {{ one.name }}
          </label>
          <span v-else>{{ one.name }}</span>
          <span class="incasso__account">{{ maskedIban(one) }}</span>
          <span class="incasso__sub">{{ one.mandateReference ? `${one.mandateReference}, signed ${dayName(one.mandateSignedOn)}` : "No mandate" }}</span>
          <span
            v-if="one.leftOut"
            class="incasso__why"
            :data-testid="`incasso-run-left-out-${one.userId}`"
          >
            {{ leftOutLabels[one.leftOut] }}<template v-if="leftOutHelp[one.leftOut]">. {{ leftOutHelp[one.leftOut] }}</template>
          </span>
        </li>
      </ul>
    </section>

    <section
      v-else-if="step === 1"
      data-testid="incasso-run-amounts"
    >
      <h2>What is collected</h2>
      <p
        v-if="period"
        class="incasso__note"
      >
        Half-year cutoff {{ dayName(period.halfYearCutoffDate) }}. A regular membership starting after it pays the half-year fee;
        one starting on it or before pays the full year. Alumni pay the alumni fee.
      </p>
      <ul class="incasso__rows">
        <li
          v-for="one in chosen"
          :key="one.userId"
        >
          <span>{{ one.name }}</span>
          <span class="incasso__sub">Member since {{ dayName(one.memberSince) }}</span>
          <v-select
            density="compact"
            :data-testid="`incasso-run-fee-${one.userId}`"
            hide-details
            :items="feeOptions"
            :model-value="feeOf(one)"
            @update:model-value="fees[one.userId] = $event"
          />
          <span class="incasso__sub">{{ euro(amountOf(one)) }}</span>
        </li>
      </ul>
    </section>

    <section
      v-else-if="step === 2"
      data-testid="incasso-run-check"
    >
      <h2>{{ euro(total) }} will be collected from {{ chosen.length }} member{{ chosen.length === 1 ? "" : "s" }}</h2>
      <div class="incasso__fields">
        <form-field
          v-slot="field"
          :error="collectionDate && !dateValid ? 'The collection date has to be after today.' : ''"
          label="Collection date"
          testid="incasso-run-date"
        >
          <date-input
            v-model="collectionDate"
            :control-id="field.controlId"
            :invalid="field.invalid"
            :min="tomorrow"
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

      <h3>Will collect · {{ chosen.length }}</h3>
      <ul class="incasso__rows">
        <li
          v-for="one in chosen"
          :key="one.userId"
          :data-testid="`incasso-run-collect-${one.userId}`"
        >
          <span>{{ one.name }}</span>
          <span class="incasso__account">{{ maskedIban(one) }}</span>
          <span class="incasso__sub">{{ one.mandateReference }} · signed {{ dayName(one.mandateSignedOn) }}</span>
          <span class="incasso__sub">{{ feeTypeLabels[feeOf(one)] }}, {{ euro(amountOf(one)) }}</span>
          <button
            class="incasso__action"
            :data-testid="`incasso-run-preview-${one.userId}`"
            :disabled="!dateValid"
            type="button"
            @click="previewFor(one)"
          >
            Preview
          </button>
        </li>
      </ul>

      <notice-box
        v-if="renamed.length"
        testid="incasso-run-renamed"
        :title="`${renamed.length} name${renamed.length === 1 ? '' : 's'} go${renamed.length === 1 ? 'es' : ''} to ING without accents`"
        tone="warning"
      >
        {{ renamed.map((one) => `${one.name} as ${one.ingName}`).join(", ") }}. ING refuses accents and other special characters in its file.
      </notice-box>

      <template v-if="leftOut.length">
        <h3>Left out · {{ leftOut.length }}</h3>
        <ul
          class="incasso__rows"
          data-testid="incasso-run-left-out"
        >
          <li
            v-for="group in groups"
            :key="group.reason"
          >
            <span>{{ leftOutLabels[group.reason] }}</span>
            <span class="incasso__sub">{{ group.names.join(", ") }}</span>
          </li>
          <li v-if="collectable.length > chosen.length">
            <span>Not chosen</span>
            <span class="incasso__sub">{{ collectable.filter((one) => !ticked[one.userId]).map((one) => one.name).join(", ") }}</span>
          </li>
        </ul>
      </template>

      <p
        v-if="failure"
        class="incasso__warn"
        data-testid="incasso-run-failure"
        role="alert"
      >
        {{ failure }}
      </p>
    </section>

    <section
      v-else-if="run"
      data-testid="incasso-run-done"
    >
      <h2>{{ run.collections.length }} incasso notification{{ run.collections.length === 1 ? "" : "s" }} sent</h2>
      <template v-if="!run.submittedAt">
        <p
          class="incasso__note"
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
            {{ run.collections.length }} collection{{ run.collections.length === 1 ? "" : "s" }}, {{ euro(run.total) }},
            on {{ dayName(run.collectionDate) }}, Core, doorlopend.
            <template v-if="run.fileParts > 1">
              ING takes at most 1000 collections a file, so there are {{ run.fileParts }}.
            </template>
          </p>
          <div class="incasso__downloads">
            <button
              v-for="part in run.fileParts"
              :key="part"
              class="incasso__action incasso__action--main"
              :data-testid="`incasso-run-download-${part}`"
              :disabled="fetching !== null"
              type="button"
              @click="download(part)"
            >
              {{ run.fileParts > 1 ? `Download file ${part} of ${run.fileParts}` : "Download incasso file" }}
            </button>
          </div>
        </notice-box>
        <ol class="incasso__how">
          <li>In Mijn ING Zakelijk, go to Incasso, then Excel importeren, and upload the file.</li>
          <li>Check it says {{ dayName(run.collectionDate) }} and {{ euro(run.total) }}, then confirm it there.</li>
          <li>Come back and press Submitted to ING.</li>
        </ol>
        <div class="incasso__nav incasso__nav--start">
          <button
            class="incasso__action"
            data-testid="incasso-run-submitted"
            :disabled="submitting"
            type="button"
            @click="submitted"
          >
            Submitted to ING
          </button>
        </div>
      </template>
      <p
        v-else
        class="incasso__note"
        data-testid="incasso-run-in-ing"
      >
        Submitted to ING on {{ dayName(run.submittedAt.slice(0, 10)) }}.
      </p>
      <p
        v-if="doneFailure"
        class="incasso__warn"
        data-testid="incasso-run-done-failure"
        role="alert"
      >
        {{ doneFailure }}
      </p>
      <p class="incasso__sub">
        {{ run.collections.length }} collection{{ run.collections.length === 1 ? "" : "s" }}, {{ euro(run.total) }}, on {{ dayName(run.collectionDate) }}: {{ run.statementText }}
      </p>
      <ul class="incasso__rows">
        <li
          v-for="one in run.collections"
          :key="one.userId"
        >
          <span>{{ one.name }}</span>
          <span class="incasso__account">{{ maskedIban(one) }}</span>
          <span class="incasso__sub">{{ one.mandateReference }} · signed {{ dayName(one.mandateSignedOn) }}</span>
          <span class="incasso__sub">{{ euro(one.amount) }}</span>
        </li>
      </ul>
      <router-link :to="back">
        Back to Contributions
      </router-link>
    </section>

    <div
      v-if="candidates.length > 0 && step < 3"
      class="incasso__nav"
    >
      <button
        v-if="step > 0"
        class="incasso__action"
        data-testid="incasso-run-previous"
        type="button"
        @click="step -= 1"
      >
        Back
      </button>
      <button
        v-if="step < 2"
        class="incasso__action incasso__action--main"
        data-testid="incasso-run-next"
        :disabled="chosen.length === 0"
        type="button"
        @click="step += 1"
      >
        {{ step === 0 ? "Next: amounts" : "Next: date and check" }}
      </button>
      <button
        v-else
        class="incasso__action incasso__action--main"
        data-testid="incasso-run-start"
        :disabled="chosen.length === 0 || !dateValid || !textValid || starting"
        type="button"
        @click="start"
      >
        Email the incasso notification to {{ chosen.length }} member{{ chosen.length === 1 ? "" : "s" }}
      </button>
    </div>

    <email-preview-dialog
      v-model="previewOpen"
      :error="previewError"
      :loading="previewLoading"
      :preview="preview"
      title="Incasso notification"
    />
  </div>
</template>

<style scoped>
.incasso {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  max-width: 60rem;
  padding: 2rem 2.4rem 3rem;
}

.incasso__back {
  align-self: flex-start;
  font-size: 0.84rem;
  color: var(--color-brand);
}

.incasso__title {
  margin: 0;
  font-family: var(--font-display);
  font-size: clamp(1.4rem, 3vw, 2rem);
}

.incasso__steps {
  display: flex;
  flex-wrap: wrap;
  gap: 1rem;
  margin: 0;
  padding: 0;
  list-style: none;
  color: var(--color-ash);
}

.incasso__step--on {
  color: var(--color-chalk);
  box-shadow: inset 0 -2px 0 var(--color-brand);
}

.incasso h2 {
  margin: 0.4rem 0;
  font-size: 1.15rem;
}

.incasso h3 {
  margin: 1rem 0 0.4rem;
  font-size: 0.8rem;
  letter-spacing: 0.2em;
  text-transform: uppercase;
  color: var(--color-ash);
}

.incasso__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 1rem;
  margin: 0 0 0.6rem;
  font-size: 0.86rem;
}

.incasso__fields {
  display: grid;
  grid-template-columns: 16rem minmax(0, 32rem);
  gap: 1rem;
}

.incasso__rows {
  display: flex;
  flex-direction: column;
  margin: 0;
  padding: 0;
  list-style: none;
  border-top: 1px solid var(--color-hairline);
}

.incasso__rows li {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.4rem 1rem;
  padding: 0.6rem 0.4rem;
  border-bottom: 1px solid var(--color-hairline);
}

.incasso__rows li > :first-child {
  flex: 1 1 12rem;
}

.incasso__account {
  font-variant-numeric: tabular-nums;
  letter-spacing: 0.03em;
}

.incasso__sub {
  font-size: 0.84rem;
  color: var(--color-ash);
}

.incasso__why {
  flex-basis: 100%;
  font-size: 0.84rem;
  color: var(--color-warning);
}

.incasso__warn {
  margin: 0;
  color: var(--color-warning);
}

.incasso__note {
  margin: 0;
  color: var(--color-ash);
}

.incasso__nav {
  display: flex;
  gap: 0.6rem;
  justify-content: flex-end;
}

.incasso__nav--start {
  justify-content: flex-start;
}

.incasso__downloads {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  margin-top: 0.6rem;
}

.incasso__how {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  margin: 0;
  padding-left: 1.2rem;
  font-size: 0.92rem;
  color: var(--color-ash);
}

.incasso__action {
  padding: 0.4rem 0.9rem;
  border: 1px solid var(--color-hairline);
  background: none;
  font: inherit;
  font-size: 0.86rem;
  color: var(--color-chalk);
  cursor: pointer;
}

.incasso__action--main {
  border-color: var(--color-brand);
  color: var(--color-brand);
}

.incasso__action:disabled {
  opacity: 0.45;
  cursor: default;
}

@media (max-width: 839px) {
  .incasso {
    padding: 1.2rem 1.1rem 2rem;
  }

  .incasso__fields {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
