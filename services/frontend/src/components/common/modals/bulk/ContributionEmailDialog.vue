<script lang="ts" setup>
import {computed, ref, watch} from "vue"
import {DateTime} from "luxon"
import BulkDialogScaffold, {type BulkColumn} from "./BulkDialogScaffold.vue"
import BaseModal from "@/components/common/modals/BaseModal.vue"
import EmailPreviewDialog from "@/components/common/modals/EmailPreviewDialog.vue"
import {useBulkPreview} from "@/composables/useBulkPreview"
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
import {reasonLabel} from "@/utils/bulkDisposition"
import {BulkFeeType, type BulkRow} from "@/utils/bulkRow"
import {
  changedFeeTypes,
  changedKinds,
  contributionEmailItems,
  contributionEmailLabels,
  countByKind,
  isSwitched,
  kindFor,
  lastSentLabel,
  lastSentOn,
  summarise,
  switchedNote,
  toBulkRows,
} from "@/utils/contributionEmail"
import {effectiveAmount, feeTypeItems, feeTypeLabels} from "@/utils/feePreview"

/**
 * Sending a period's payment emails to the members selected in the manager.
 *
 * One send, two statements: each row shows which email that member gets, chosen by their
 * direct-debit flag and switchable per row. The rows come from the api, which decides them
 * once so what is confirmed is what is sent.
 */
defineOptions({name: "ContributionEmailDialog", inheritAttrs: false})

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

const {rows, counts, includedUserIds, reincludeOverrides, submitting, setRows, submit, reset} =
  useBulkPreview()
const {submitState, showSubmitStatus, setSubmitResult} = useSubmitFeedback()
const {
  open: emailPreviewOpen,
  loading: emailPreviewLoading,
  error: emailPreviewError,
  preview: emailPreview,
  show: showEmailPreview,
  reset: resetEmailPreview,
} = useEmailPreview()

const previewRecipientId = ref<number | null>(null)
const paymentDueDate = ref("")
const debitDate = ref("")
const feeTypeSelections = ref<Record<number, BulkFeeType>>({})
const kindSelections = ref<Record<number, ContributionEmailKind>>({})
const loading = ref(false)
const loadError = ref<string | null>(null)
const rejection = ref<BulkRejection | null>(null)
const confirmOpen = ref(false)

const today = DateTime.now().toFormat("yyyy-MM-dd")

const kindCounts = computed(() =>
  countByKind(rows.value, kindSelections.value, reincludeOverrides.value),
)
const sendsReminders = computed(() => kindCounts.value[ContributionEmailKind.REMINDER] > 0)
const sendsNotifications = computed(
  () => kindCounts.value[ContributionEmailKind.INCASSO_NOTIFICATION] > 0,
)

// Each date is required only when some row is getting that email.
const paymentDueRules = computed(() => [
  (v: string) => !sendsReminders.value || !!v || "A payment due date is required.",
  (v: string) => !v || v > today || "The payment due date must be after today.",
])
const debitDateRules = computed(() => [
  (v: string) => !sendsNotifications.value || !!v || "A debit date is required.",
  (v: string) => !v || v > today || "The debit date must be after today.",
])

// No widths: the columns take what their content needs, which is the only thing that knows
// how long "Incasso notification" is.
const columns: BulkColumn[] = [
  {key: "name", header: "Member", sortable: true},
  {key: "memberType", header: "Type", sortable: true},
  {key: "disposition", header: "Status", sortable: true},
  {key: "memberSince", header: "Member since", sortable: true},
  {key: "kind", header: "Gets"},
  {key: "fee", header: "Fee type"},
  {key: "amount", header: "Amount", align: "end", sortable: true},
  {key: "lastSent", header: "Last sent", align: "center"},
  {key: "note", header: "Note"},
]

const periodRange = computed(() => {
  const period = props.period
  if (!period) return null
  const format = (iso: string) => DateTime.fromISO(iso).toFormat("dd/MM/yyyy")
  return `${format(period.startDate)} – ${format(period.endDate)}`
})

function rowAmount(row: BulkRow): number | null {
  return effectiveAmount(feeTypeSelections.value[row.userId] ?? row.recommendedFeeType, props.period)
}

/** A member this send will never write to has nothing worth changing. */
function isEditable(row: BulkRow): boolean {
  return row.disposition !== "EXCLUDED"
}

function feeLabel(row: BulkRow): string {
  return row.recommendedFeeType ? feeTypeLabels[row.recommendedFeeType] : "—"
}

function rowKind(row: BulkRow): ContributionEmailKind {
  return kindFor(row, kindSelections.value)
}

function rowSwitched(row: BulkRow): boolean {
  return isSwitched(row, kindSelections.value)
}

function rowLastSent(row: BulkRow): string {
  return lastSentLabel(lastSentOn(row, kindSelections.value))
}

function seedSelections(next: BulkRow[]) {
  const fees: Record<number, BulkFeeType> = {}
  const kinds: Record<number, ContributionEmailKind> = {}
  for (const row of next) {
    if (row.recommendedFeeType) fees[row.userId] = row.recommendedFeeType
    if (row.defaultKind) kinds[row.userId] = row.defaultKind
  }
  feeTypeSelections.value = fees
  kindSelections.value = kinds
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
    setRows([])
    return
  }
  const mapped = toBulkRows(data.rows)
  seedSelections(mapped)
  setRows(mapped)
}

/** A warned member may yet be ticked back in, so their email is readable too. */
const previewRecipients = computed(() =>
  rows.value
    .filter((row) => row.disposition !== "EXCLUDED")
    .map((row) => ({
      value: row.userId,
      title: `${row.name} — ${contributionEmailLabels[rowKind(row)]}`,
    })),
)

/** The date reaches the email, so the one this member needs cannot be missing. */
function dateFor(userId: number): string {
  const row = rows.value.find((r) => r.userId === userId)
  if (!row) return ""
  return rowKind(row) === ContributionEmailKind.INCASSO_NOTIFICATION ? debitDate.value : paymentDueDate.value
}

const canPreviewEmail = computed(
  () => previewRecipients.value.length > 0
    && !!dateFor(previewRecipientId.value ?? previewRecipients.value[0]!.value),
)

function openEmailPreview() {
  if (!canPreviewEmail.value) return
  if (previewRecipientId.value == null) previewRecipientId.value = previewRecipients.value[0]!.value
  void renderEmailFor(previewRecipientId.value)
}

async function renderEmailFor(userId: number) {
  const periodId = props.period?.id
  const row = rows.value.find((r) => r.userId === userId)
  if (periodId == null || !row) return
  await showEmailPreview(async () => {
    const {data} = await readContributionEmail({
      query: {
        kind: rowKind(row),
        contributionPeriodId: periodId,
        userId,
        date: dateFor(userId),
        feeType: feeTypeSelections.value[userId],
      },
    })
    return data ?? null
  })
}

watch(previewRecipientId, (userId) => {
  if (userId != null && emailPreviewOpen.value) void renderEmailFor(userId)
})

watch(previewRecipients, (options) => {
  const current = previewRecipientId.value
  if (options.length === 0) {
    previewRecipientId.value = null
  } else if (current == null || !options.some((option) => option.value === current)) {
    previewRecipientId.value = options[0]!.value
  }
})

const canConfirm = computed(() => includedUserIds.value.length > 0 && !submitting.value)

const forciblyIncludedUserIds = computed(() =>
  rows.value
    .filter((row) => row.disposition === "WARNING" && reincludeOverrides.value[row.userId])
    .map((row) => row.userId),
)

const sendSummary = computed(() =>
  summarise(rows.value, kindSelections.value, feeTypeSelections.value, reincludeOverrides.value),
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

/**
 * Send opens the summary rather than sending. Nothing here can be undone once the jobs are
 * queued, and the table is long enough that its totals are not read off it by eye.
 */
function onConfirm() {
  if (!canConfirm.value || props.period?.id == null) return
  rejection.value = null
  confirmOpen.value = true
}

async function onFinalSend() {
  const periodId = props.period?.id
  if (!canConfirm.value || periodId == null) return
  const ok = await submit(async () => {
    const response = await sendPaymentEmails({
      body: {
        contributionPeriodId: periodId,
        userIds: props.userIds,
        forciblyIncludedUserIds: forciblyIncludedUserIds.value,
        kindOverrides: changedKinds(rows.value, kindSelections.value),
        paymentDueDate: sendsReminders.value ? paymentDueDate.value : undefined,
        debitDate: sendsNotifications.value ? debitDate.value : undefined,
        feeTypeOverrides: changedFeeTypes(rows.value, feeTypeSelections.value),
      },
    })
    // The generated client returns a refusal rather than throwing.
    const refused = parseBulkRejection(response)
    if (refused) {
      rejection.value = refused
      // Back to the table: the refusal names rows, and the summary cannot show them.
      confirmOpen.value = false
      await loadRows()
      return false
    }
    return response.data != null
  })
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
      paymentDueDate.value = ""
      debitDate.value = ""
      rejection.value = null
      void loadRows()
    } else {
      rejection.value = null
      loadError.value = null
      feeTypeSelections.value = {}
      kindSelections.value = {}
      previewRecipientId.value = null
      resetEmailPreview()
      reset()
    }
  },
  {immediate: true},
)

defineExpose({
  paymentDueDate,
  debitDate,
  feeTypeSelections,
  kindSelections,
  previewRecipientId,
  previewRecipients,
  forciblyIncludedUserIds,
  kindCounts,
  confirmOpen,
  sendSummary,
  rowAmount,
  loadRows,
})
</script>

<template>
  <bulk-dialog-scaffold
    v-model="open"
    v-model:reinclude-overrides="reincludeOverrides"
    :columns="columns"
    confirm-label="Send"
    :counts="counts"
    :get-row-amount="rowAmount"
    :help="{
      title: 'Send payment emails',
      body:
        'Asks the selected members for what they owe for the chosen contribution period. '
        + 'Members on direct debit are told what will be taken and when; everybody else is '
        + 'asked to transfer by the due date. Which one a member gets follows from their '
        + 'direct-debit flag and can be changed per row — a switched row is flagged in its '
        + 'note. Members who have already paid, or who were not members during the period, '
        + 'are left out by default; tick Forcibly include to send anyway. Honorary members, '
        + 'deleted accounts and members with no email address are shown but never written to. '
        + 'You can change a member\'s fee type and the amount follows from the period; there '
        + 'is no field for typing an amount. Sending again is allowed as often as you need to '
        + 'chase: every send is recorded on its own.',
    }"
    icon="mdi-email-fast"
    include-label="Forcibly include"
    :included-count="includedUserIds.length"
    info-box-label="Contribution period"
    max-width="1700"
    :rows="rows"
    :show-submit-status="showSubmitStatus"
    :submit-state="submitState"
    :submitting="submitting"
    title="Send payment emails"
    @cancel="emit('update:modelValue', false)"
    @confirm="onConfirm"
  >
    <template #footer-actions>
      <v-btn
        data-testid="payment-emails-preview-btn"
        :disabled="!canPreviewEmail"
        prepend-icon="mdi-email-search-outline"
        variant="text"
        @click="openEmailPreview"
      >
        Preview email
      </v-btn>
    </template>

    <template #form>
      <div class="mb-4 d-flex payment-email-dates">
        <v-text-field
          v-model="paymentDueDate"
          data-testid="payment-emails-payment-due-date"
          :hint="sendsReminders ? undefined : 'Nobody in this selection is being asked to transfer.'"
          hide-details="auto"
          label="Payment due date"
          persistent-hint
          placeholder="YYYY-MM-DD"
          prepend-inner-icon="mdi-calendar"
          :rules="paymentDueRules"
          type="date"
        />
        <v-text-field
          v-model="debitDate"
          data-testid="payment-emails-debit-date"
          :hint="sendsNotifications ? undefined : 'Nobody in this selection is on direct debit.'"
          hide-details="auto"
          label="Debit date"
          persistent-hint
          placeholder="YYYY-MM-DD"
          prepend-inner-icon="mdi-calendar-arrow-right"
          :rules="debitDateRules"
          type="date"
        />
      </div>

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
    </template>

    <template #info-box>
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
          v-if="counts.excluded > 0"
          color="error"
          data-testid="payment-emails-count-excluded"
          prepend-icon="mdi-account-off-outline"
          size="small"
          variant="tonal"
        >
          {{ counts.excluded }} get no email
        </v-chip>
        <v-progress-circular
          v-if="loading"
          data-testid="payment-emails-loading"
          indeterminate
          size="18"
          width="2"
        />
      </div>
    </template>

    <template #cell.kind="{row}">
      <v-select
        v-if="isEditable(row)"
        v-model="kindSelections[row.userId]"
        class="payment-email-kind-select"
        :data-testid="`payment-emails-kind-${row.userId}`"
        density="compact"
        hide-details
        item-title="title"
        item-value="value"
        :items="contributionEmailItems"
        variant="plain"
      />
      <span
        v-else
        class="text-medium-emphasis"
      >—</span>
    </template>

    <template #cell.fee="{row}">
      <v-select
        v-if="isEditable(row)"
        v-model="feeTypeSelections[row.userId]"
        class="payment-email-feetype-select"
        :data-testid="`payment-emails-feetype-${row.userId}`"
        density="compact"
        hide-details
        item-title="title"
        item-value="value"
        :items="feeTypeItems"
        variant="plain"
      />
      <span
        v-else
        class="text-caption text-medium-emphasis"
        :data-testid="`payment-emails-feetype-fixed-${row.userId}`"
      >{{ feeLabel(row) }}</span>
    </template>

    <template #cell.amount="{row}">
      <span
        v-if="rowAmount(row) != null"
        class="text-caption"
        :data-testid="`payment-emails-amount-${row.userId}`"
      >€ {{ rowAmount(row)!.toFixed(2) }}</span>
      <span
        v-else
        class="text-medium-emphasis"
      >—</span>
    </template>

    <template #cell.lastSent="{row}">
      <span
        class="text-caption"
        :class="lastSentOn(row, kindSelections) ? 'text-warning' : 'text-medium-emphasis'"
        :data-testid="`payment-emails-last-sent-${row.userId}`"
      >{{ rowLastSent(row) }}</span>
    </template>

    <!-- The warning and the switch share the column; the switch takes a second line. -->
    <template #cell.note="{row}">
      <div
        v-if="row.reason"
        class="text-caption"
        :class="row.disposition === 'EXCLUDED' ? 'text-error' : 'text-warning'"
        :data-testid="`bulk-preview-note-${row.userId}`"
      >
        {{ reasonLabel(row.reason) }}
      </div>
      <div
        v-if="rowSwitched(row)"
        class="text-caption text-warning d-flex align-center ga-1"
        :data-testid="`payment-emails-switched-${row.userId}`"
      >
        <v-icon
          icon="mdi-alert-outline"
          size="14"
        />
        Switched — {{ switchedNote(row) }}
      </div>
    </template>
  </bulk-dialog-scaffold>

  <!--
    The last stop before a hundred emails leave. Everything here is a count the operator
    would otherwise have to take off a long table by eye.
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
          v-if="sendSummary.notWrittenTo > 0"
          data-testid="payment-emails-confirm-not-written-to"
          prepend-icon="mdi-account-off-outline"
          variant="tonal"
        >
          {{ sendSummary.notWrittenTo }}
          {{ sendSummary.notWrittenTo === 1 ? "member" : "members" }} will not have an email
          sent to them
        </v-chip>
      </div>

      <!-- Each line is somewhere the operator overruled something; grouped so they read as
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
            {{ sendSummary.forced }} forcibly included despite a warning
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
  >
    <template #recipient>
      <v-select
        v-model="previewRecipientId"
        data-testid="payment-emails-preview-recipient"
        density="compact"
        hide-details
        item-title="title"
        item-value="value"
        :items="previewRecipients"
        label="Preview as"
      />
    </template>
  </email-preview-dialog>
</template>

<style lang="scss" scoped>
.payment-email-dates {
  gap: 12px;
}

// The table lays out automatically, so it compresses whatever it can to fit. Left alone
// that lands on the two pickers, which are the cells that cannot afford it: a wrapped
// "Incasso notification" is unreadable, where a wrapped member name is fine. Asking for
// their content width makes the wrappable columns give way instead.
// Inside a v-alert, so the marker sits in the alert's own padding rather than outdenting.
.payment-email-overrides {
  list-style: disc;
  padding-left: 18px;
  margin: 0;

  li + li {
    margin-top: 2px;
  }
}

.payment-email-kind-select,
.payment-email-feetype-select {
  min-width: max-content;

  :deep(.v-field__input) {
    white-space: nowrap;
  }
}
</style>
