<script lang="ts" setup>
import {computed, ref, watch} from "vue"
import {DateTime} from "luxon"
import BaseModal from "@/components/common/modals/BaseModal.vue"
import EmailPreviewDialog from "@/components/common/modals/EmailPreviewDialog.vue"
import InfoBox from "@/components/common/panels/InfoBox.vue"
import PaymentEmailFeesStep from "./PaymentEmailFeesStep.vue"
import PaymentEmailMembersStep from "./PaymentEmailMembersStep.vue"
import PaymentEmailReviewStep from "./PaymentEmailReviewStep.vue"
import {useEmailPreview} from "@/composables/useEmailPreview"
import {useSubmitFeedback} from "@/composables/formUtils"
import {
  ContributionEmailKind,
  previewBulkContributionEmail,
  readContributionEmail,
  sendPaymentEmails,
} from "@/services/api"
import type {ContributionPeriodResponse} from "@/services/api"
import {parseBulkRejection, type BulkRejection} from "@/utils/bulkRejection"
import type {BulkFeeType, BulkRow} from "@/utils/bulkRow"
import {
  changedFeeTypes,
  changedKinds,
  countByKind,
  forcedUserIds,
  isSelectable,
  kindFor,
  paymentDateProblem,
  seedSendTo,
  summarise,
  toBulkRows,
  willSend,
} from "@/utils/contributionEmail"

/**
 * Sending a period's payment emails, as three questions asked one at a time: who the batch
 * writes to, what each of them gets, and what is about to go out.
 *
 * The api decides what it would do with the selection; the treasurer decides who is in it.
 * Nothing here is sent until the confirmation on top of step 3 is answered.
 */
defineOptions({name: "PaymentEmailWizard", inheritAttrs: false})

interface Props {
  modelValue: boolean
  period: ContributionPeriodResponse | null
  /** The ids ticked in the manager. This action is over the selection, nothing more. */
  userIds: number[]
}

const props = defineProps<Props>()
const emit = defineEmits<{
  (e: "update:modelValue", value: boolean): void
  (e: "done"): void
}>()

const open = computed({
  get: () => props.modelValue,
  set: (v) => emit("update:modelValue", v),
})

const {submitState, showSubmitStatus, setSubmitResult} = useSubmitFeedback()
const {
  open: emailPreviewOpen,
  loading: emailPreviewLoading,
  error: emailPreviewError,
  preview: emailPreview,
  show: showEmailPreview,
  reset: resetEmailPreview,
} = useEmailPreview()

const STEPS = [
  {value: 1, title: "Members"},
  {value: 2, title: "Fees & emails"},
  {value: 3, title: "What will be sent"},
] as const

/** Which step owns each request field, so a refusal lands where it can be corrected. */
const FIELD_STEPS: Record<string, number> = {
  userIds: 1,
  forciblyIncludedUserIds: 1,
  kindOverrides: 2,
  feeTypeOverrides: 2,
  paymentDueDate: 3,
  debitDate: 3,
}

type DateField = "paymentDueDate" | "debitDate"

const NO_DATE_REFUSALS: Record<DateField, string | null> = {paymentDueDate: null, debitDate: null}

const step = ref(1)
/** The furthest step reached, which is how far back the header stays clickable. */
const reached = ref(1)
const rows = ref<BulkRow[]>([])
const sendTo = ref<Record<number, boolean>>({})
const feeTypeSelections = ref<Record<number, BulkFeeType>>({})
const kindSelections = ref<Record<number, ContributionEmailKind>>({})
const paymentDueDate = ref("")
const debitDate = ref("")
const loading = ref(false)
const loadError = ref<string | null>(null)
const rejection = ref<BulkRejection | null>(null)
/** Rows the api named, by the sentence it refused them with, so step 1 marks them. */
const refusedRows = ref<Record<number, string>>({})
const refusedDates = ref<Record<DateField, string | null>>({...NO_DATE_REFUSALS})
const confirmOpen = ref(false)
const submitting = ref(false)
const helpOpen = ref(false)

const today = DateTime.now().toFormat("yyyy-MM-dd")

const recipients = computed(() => rows.value.filter((row) => willSend(row, sendTo.value)))
const unreachable = computed(() => rows.value.filter((row) => !isSelectable(row)).length)

const kindCounts = computed(() => countByKind(rows.value, kindSelections.value, sendTo.value))
const sendsReminders = computed(() => kindCounts.value[ContributionEmailKind.REMINDER] > 0)
const sendsNotifications = computed(
  () => kindCounts.value[ContributionEmailKind.INCASSO_NOTIFICATION] > 0,
)

/**
 * Each date is required only when some recipient is getting the email that quotes it. A
 * date the api refused says so instead, until the treasurer changes it.
 */
const dateProblems = computed(() => ({
  paymentDueDate: dateProblem("paymentDueDate", paymentDueDate.value, sendsReminders.value, "payment due date"),
  debitDate: dateProblem("debitDate", debitDate.value, sendsNotifications.value, "debit date"),
}))

function dateProblem(field: DateField, iso: string, needed: boolean, noun: string): string | null {
  const refused = refusedDates.value[field]
  if (refused) return refused
  if (needed && !iso) return `A ${noun} is required.`
  return paymentDateProblem(iso, props.period, today)
}

function setDate(field: DateField, value: string) {
  if (field === "paymentDueDate") paymentDueDate.value = value
  else debitDate.value = value
  refusedDates.value = {...refusedDates.value, [field]: null}
}

const canAdvance = computed(() => {
  if (recipients.value.length === 0) return false
  if (step.value < 3) return true
  return !dateProblems.value.paymentDueDate && !dateProblems.value.debitDate && !submitting.value
})

const nextLabel = computed(() => (step.value === 3 ? "Send" : "Next"))
const nextIcon = computed(() => (step.value === 3 ? "mdi-email-fast" : "mdi-arrow-right"))

const periodRange = computed(() => {
  const period = props.period
  if (!period) return null
  const format = (iso: string) => DateTime.fromISO(iso).toFormat("dd/MM/yyyy")
  return `${format(period.startDate)} – ${format(period.endDate)}`
})

function seedSelections(next: BulkRow[]) {
  const fees: Record<number, BulkFeeType> = {}
  const kinds: Record<number, ContributionEmailKind> = {}
  for (const row of next) {
    if (row.recommendedFeeType) fees[row.userId] = row.recommendedFeeType
    if (row.defaultKind) kinds[row.userId] = row.defaultKind
  }
  feeTypeSelections.value = fees
  kindSelections.value = kinds
  sendTo.value = seedSendTo(next)
}

async function loadRows() {
  const periodId = props.period?.id
  if (periodId == null || props.userIds.length === 0) return
  loading.value = true
  loadError.value = null
  const {data} = await previewBulkContributionEmail({
    body: {contributionPeriodId: periodId, userIds: props.userIds},
  })
  loading.value = false
  if (!data) {
    loadError.value = "The selection could not be read."
    rows.value = []
    seedSelections([])
    return
  }
  const mapped = toBulkRows(data.rows)
  seedSelections(mapped)
  rows.value = mapped
}

function goTo(next: number) {
  step.value = next
  reached.value = Math.max(reached.value, next)
}

function onNext() {
  if (!canAdvance.value) return
  if (step.value < 3) {
    goTo(step.value + 1)
    return
  }
  clearRefusal()
  confirmOpen.value = true
}

function clearRefusal() {
  rejection.value = null
  refusedRows.value = {}
  refusedDates.value = {...NO_DATE_REFUSALS}
}

/**
 * Puts the treasurer back on the step that owns the field the api refused, with the rows
 * or the input it named marked. The earliest such step wins, because correcting it is
 * what the later ones are read against.
 */
function routeRefusal(refused: BulkRejection) {
  const rows: Record<number, string> = {}
  const dates: Record<DateField, string | null> = {...NO_DATE_REFUSALS}
  let landing: number | null = null

  for (const reason of refused.reasons) {
    const owner = FIELD_STEPS[reason.field]
    if (owner == null) continue
    landing = landing == null ? owner : Math.min(landing, owner)
    if (owner === 1) for (const id of reason.userIds) rows[id] = reason.message
    if (reason.field === "paymentDueDate" || reason.field === "debitDate") {
      dates[reason.field] = reason.message
    }
  }

  refusedRows.value = rows
  refusedDates.value = dates
  if (landing != null) step.value = landing
}

/** The date the member's own email quotes, which is what the preview has to render with. */
function dateFor(row: BulkRow): string {
  return kindFor(row, kindSelections.value) === ContributionEmailKind.INCASSO_NOTIFICATION
    ? debitDate.value
    : paymentDueDate.value
}

async function onPreview(userId: number) {
  const periodId = props.period?.id
  const row = rows.value.find((r) => r.userId === userId)
  if (periodId == null || !row) return
  await showEmailPreview(async () => {
    const {data} = await readContributionEmail({
      query: {
        kind: kindFor(row, kindSelections.value),
        contributionPeriodId: periodId,
        userId,
        date: dateFor(row),
        feeType: feeTypeSelections.value[userId],
      },
    })
    return data ?? null
  })
}

const sendSummary = computed(() =>
  summarise(rows.value, kindSelections.value, feeTypeSelections.value, sendTo.value),
)

interface SummaryDate {
  label: string
  value: string
  icon: string
  testid: string
}

/** The dates that reach somebody, as the confirmation restates them. */
const summaryDates = computed<SummaryDate[]>(() => {
  const format = (iso: string) => (iso ? DateTime.fromISO(iso).toFormat("dd/MM/yyyy") : "—")
  return [
    sendsReminders.value
      ? {
          label: "Pay by",
          value: format(paymentDueDate.value),
          icon: "mdi-calendar",
          testid: "payment-emails-confirm-pay-by",
        }
      : null,
    sendsNotifications.value
      ? {
          label: "Debited on",
          value: format(debitDate.value),
          icon: "mdi-calendar-arrow-right",
          testid: "payment-emails-confirm-debited-on",
        }
      : null,
  ].filter((entry): entry is SummaryDate => entry !== null)
})

const hasOverrides = computed(() => {
  const {forced, switched, reCharged, alreadySent} = sendSummary.value
  return forced + switched + reCharged + alreadySent > 0
})

async function onFinalSend() {
  const periodId = props.period?.id
  if (!canAdvance.value || periodId == null) return
  submitting.value = true
  let ok = false
  try {
    const response = await sendPaymentEmails({
      body: {
        contributionPeriodId: periodId,
        userIds: recipients.value.map((row) => row.userId),
        forciblyIncludedUserIds: forcedUserIds(rows.value, sendTo.value),
        kindOverrides: changedKinds(recipients.value, kindSelections.value),
        paymentDueDate: sendsReminders.value ? paymentDueDate.value : undefined,
        debitDate: sendsNotifications.value ? debitDate.value : undefined,
        feeTypeOverrides: changedFeeTypes(recipients.value, feeTypeSelections.value),
      },
    })
    // The generated client returns a refusal rather than throwing.
    const refused = parseBulkRejection(response)
    if (refused) {
      rejection.value = refused
      // The refusal is about the batch, and the summary cannot show it.
      confirmOpen.value = false
      // A conflict means the table has moved, so it is read again before it is marked.
      if (refused.status === 409) await loadRows()
      routeRefusal(refused)
    } else {
      ok = response.data != null
    }
  } catch {
    ok = false
  } finally {
    submitting.value = false
  }
  setSubmitResult(ok)
  if (ok) {
    confirmOpen.value = false
    setTimeout(() => {
      emit("update:modelValue", false)
      emit("done")
    }, 1200)
  }
}

function namesFor(userIds: number[]): string {
  return userIds
    .map((id) => rows.value.find((row) => row.userId === id)?.name ?? `#${id}`)
    .join(", ")
}

watch(
  () => props.modelValue,
  (isOpen) => {
    if (isOpen) {
      void loadRows()
      return
    }
    step.value = 1
    reached.value = 1
    rows.value = []
    sendTo.value = {}
    feeTypeSelections.value = {}
    kindSelections.value = {}
    paymentDueDate.value = ""
    debitDate.value = ""
    clearRefusal()
    loadError.value = null
    helpOpen.value = false
    resetEmailPreview()
  },
  {immediate: true},
)
</script>

<template>
  <base-modal
    v-model="open"
    max-width="1400"
    :save-disabled="!canAdvance"
    :save-icon="nextIcon"
    :save-label="nextLabel"
    save-testid="payment-emails-next-btn"
    show-cancel
    show-save
    scrollable
    testid="payment-emails-wizard"
    title="Send payment emails"
    @cancel="emit('update:modelValue', false)"
    @save="onNext"
  >
    <template #title-append>
      <v-btn
        aria-label="Help"
        data-testid="payment-emails-help-btn"
        icon="mdi-help-circle-outline"
        size="small"
        variant="text"
        @click="helpOpen = !helpOpen"
      />
    </template>

    <template #actions-prepend>
      <v-btn
        v-if="step > 1"
        data-testid="payment-emails-back-btn"
        prepend-icon="mdi-arrow-left"
        variant="text"
        @click="step -= 1"
      >
        Back
      </v-btn>
    </template>

    <template #body-header>
      <v-expand-transition>
        <info-box
          v-if="helpOpen"
          class="mb-4"
          label="Send payment emails"
          testid="payment-emails-help-panel"
        >
          Asks the ticked members for what they owe for the chosen contribution period.
          Members on direct debit are told what will be taken and when; everybody else is
          asked to transfer by the due date. Which one a member gets follows from their
          direct-debit flag and can be changed on the second step. Members who have already
          paid, or who were not members during the period, start unticked; members who owe no
          contribution, deleted accounts and members with no email address cannot be emailed
          at all. Changing a fee type re-prices the row from the period; there is no field for
          typing an amount. Sending again is allowed as often as you need to chase: every send
          is recorded on its own.
        </info-box>
      </v-expand-transition>

      <v-alert
        v-if="loadError"
        class="mb-4"
        data-testid="payment-emails-load-error"
        density="compact"
        type="error"
        variant="tonal"
      >
        {{ loadError }}
      </v-alert>

      <v-alert
        v-if="rejection"
        class="mb-4"
        data-testid="payment-emails-rejection"
        density="compact"
        type="warning"
        variant="tonal"
      >
        <div class="font-weight-medium mb-1">
          Nothing was sent.
        </div>
        <div
          v-for="reason in rejection.reasons"
          :key="reason.code"
          class="text-body-2"
        >
          {{ reason.message }}
          <span v-if="reason.userIds.length"> {{ namesFor(reason.userIds) }}</span>
        </div>
      </v-alert>

      <info-box
        class="mb-3 elevation-2"
        label="Contribution period"
      >
        <div
          class="d-flex flex-wrap ga-2 align-center"
          data-testid="payment-emails-period-info"
        >
          <v-chip
            v-if="periodRange"
            color="primary"
            prepend-icon="mdi-calendar-range"
            size="small"
            variant="tonal"
          >
            {{ periodRange }}
          </v-chip>
          <v-chip
            data-testid="payment-emails-count-recipients"
            prepend-icon="mdi-account-check-outline"
            size="small"
            variant="tonal"
          >
            {{ recipients.length }} of {{ rows.length }} selected
          </v-chip>
          <v-chip
            data-testid="payment-emails-count-reminders"
            prepend-icon="mdi-email-fast"
            size="small"
            variant="tonal"
          >
            {{ kindCounts.REMINDER }} contribution
            {{ kindCounts.REMINDER === 1 ? "reminder" : "reminders" }}
          </v-chip>
          <v-chip
            data-testid="payment-emails-count-notifications"
            prepend-icon="mdi-bank-transfer-out"
            size="small"
            variant="tonal"
          >
            {{ kindCounts.INCASSO_NOTIFICATION }} incasso
            {{ kindCounts.INCASSO_NOTIFICATION === 1 ? "notification" : "notifications" }}
          </v-chip>
          <v-chip
            v-if="unreachable > 0"
            color="error"
            data-testid="payment-emails-count-excluded"
            prepend-icon="mdi-account-off-outline"
            size="small"
            variant="tonal"
          >
            {{ unreachable }} cannot be emailed
          </v-chip>
          <v-progress-circular
            v-if="loading"
            data-testid="payment-emails-loading"
            indeterminate
            size="18"
            width="2"
          />
        </div>
      </info-box>

      <!-- Header only: the step bodies render below, in the region that scrolls. -->
      <v-stepper
        v-model="step"
        class="payment-email-stepper"
        flat
      >
        <v-stepper-header>
          <template
            v-for="(item, index) in STEPS"
            :key="item.value"
          >
            <v-divider v-if="index > 0" />
            <v-stepper-item
              :complete="step > item.value"
              :data-testid="`payment-emails-step-${item.value}`"
              :editable="item.value <= reached"
              :title="item.title"
              :value="item.value"
            />
          </template>
        </v-stepper-header>
      </v-stepper>
    </template>

    <payment-email-members-step
      v-if="step === 1"
      :refusals="refusedRows"
      :rows="rows"
      :send-to="sendTo"
      @update:send-to="(v) => (sendTo = v)"
    />
    <payment-email-fees-step
      v-else-if="step === 2"
      :fees="feeTypeSelections"
      :kinds="kindSelections"
      :period="period"
      :rows="recipients"
      @update:fees="(v) => (feeTypeSelections = v)"
      @update:kinds="(v) => (kindSelections = v)"
    />
    <payment-email-review-step
      v-else
      :date-problems="dateProblems"
      :debit-date="debitDate"
      :fees="feeTypeSelections"
      :kinds="kindSelections"
      :payment-due-date="paymentDueDate"
      :period="period"
      :rows="recipients"
      :sends-notifications="sendsNotifications"
      :sends-reminders="sendsReminders"
      @preview="onPreview"
      @update:debit-date="(v) => setDate('debitDate', v)"
      @update:payment-due-date="(v) => setDate('paymentDueDate', v)"
    />
  </base-modal>

  <!--
    The last stop before a hundred emails leave. Everything here is a count the treasurer
    would otherwise have to take off a long list by eye.
  -->
  <base-modal
    v-model="confirmOpen"
    cancel-label="Back"
    cancel-testid="payment-emails-confirm-back-btn"
    max-width="520"
    save-icon="mdi-email-fast"
    :save-label="`Send ${sendSummary.total} ${sendSummary.total === 1 ? 'email' : 'emails'}`"
    :save-loading="submitting"
    save-testid="payment-emails-confirm-send-btn"
    :save-show-status="showSubmitStatus"
    :save-submit-state="submitState"
    show-cancel
    show-save
    testid="payment-emails-confirm-dialog"
    title="Send these emails?"
    @cancel="confirmOpen = false"
    @save="onFinalSend"
  >
    <div data-testid="payment-emails-confirm-summary">
      <div class="d-flex flex-wrap ga-2">
        <v-chip
          v-if="sendSummary.reminders > 0"
          color="primary"
          data-testid="payment-emails-confirm-reminders"
          prepend-icon="mdi-email-fast"
          variant="tonal"
        >
          {{ sendSummary.reminders }} contribution
          {{ sendSummary.reminders === 1 ? "reminder" : "reminders" }}
        </v-chip>
        <v-chip
          v-if="sendSummary.incassoNotifications > 0"
          color="primary"
          data-testid="payment-emails-confirm-notifications"
          prepend-icon="mdi-bank-transfer-out"
          variant="tonal"
        >
          {{ sendSummary.incassoNotifications }} incasso
          {{ sendSummary.incassoNotifications === 1 ? "notification" : "notifications" }}
        </v-chip>
        <v-chip
          v-for="date in summaryDates"
          :key="date.label"
          :data-testid="date.testid"
          :prepend-icon="date.icon"
          variant="tonal"
        >
          {{ date.label }} {{ date.value }}
        </v-chip>
        <v-chip
          v-if="sendSummary.notEmailed > 0"
          data-testid="payment-emails-confirm-not-emailed"
          prepend-icon="mdi-account-off-outline"
          variant="tonal"
        >
          {{ sendSummary.notEmailed }} of the selected
          {{ sendSummary.notEmailed === 1 ? "members is" : "members are" }} left alone
        </v-chip>
      </div>

      <!-- Each line is somewhere the treasurer overruled something; grouped so they read as
           one thing to check rather than four loose sentences. -->
      <v-alert
        v-if="hasOverrides"
        class="mt-4"
        data-testid="payment-emails-confirm-overrides"
        density="compact"
        icon="mdi-alert-outline"
        type="warning"
        variant="tonal"
      >
        <div class="text-body-2 font-weight-medium mb-1">
          Worth checking before you send
        </div>
        <ul class="payment-email-overrides text-body-2">
          <li
            v-if="sendSummary.forced > 0"
            data-testid="payment-emails-confirm-forced"
          >
            {{ sendSummary.forced }} ticked back in despite a warning
          </li>
          <li
            v-if="sendSummary.switched > 0"
            data-testid="payment-emails-confirm-switched"
          >
            {{ sendSummary.switched }} moved off the email their direct-debit flag chose
          </li>
          <li
            v-if="sendSummary.reCharged > 0"
            data-testid="payment-emails-confirm-recharged"
          >
            {{ sendSummary.reCharged }} charged a fee type other than the one that applies
          </li>
          <li
            v-if="sendSummary.alreadySent > 0"
            data-testid="payment-emails-confirm-already-sent"
          >
            {{ sendSummary.alreadySent }} already had this email for this period
          </li>
        </ul>
      </v-alert>

      <p class="text-caption text-medium-emphasis mt-4 mb-0">
        Sending the emails will happen immediately and cannot be undone.
      </p>
    </div>
  </base-modal>

  <email-preview-dialog
    v-model="emailPreviewOpen"
    :error="emailPreviewError"
    :loading="emailPreviewLoading"
    :preview="emailPreview"
    title="Payment email"
  />
</template>

<style lang="scss" scoped>
// The stepper is here for its header alone, so it brings no surface of its own.
.payment-email-stepper {
  background: transparent;
}

// Inside a v-alert, so the marker sits in the alert's own padding rather than outdenting.
.payment-email-overrides {
  list-style: disc;
  padding-left: 18px;
  margin: 0;

  li + li {
    margin-top: 2px;
  }
}
</style>
