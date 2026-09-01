<script lang="ts" setup>
import {computed, ref, watch} from "vue"
import {DateTime} from "luxon"
import BulkDialogScaffold, {type BulkColumn} from "./BulkDialogScaffold.vue"
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

const columns: BulkColumn[] = [
  {key: "name", header: "Member", sortable: true},
  {key: "memberType", header: "Type", sortable: true},
  {key: "disposition", header: "Status", sortable: true},
  {key: "memberSince", header: "Member since", sortable: true},
  {key: "kind", header: "Gets", width: "190px"},
  {key: "fee", header: "Fee type", width: "170px"},
  {key: "amount", header: "Amount", align: "end", sortable: true, width: "90px"},
  {key: "lastSent", header: "Last sent", align: "center", width: "105px"},
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

async function onConfirm() {
  const periodId = props.period?.id
  if (!canConfirm.value || periodId == null) return
  rejection.value = null
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
      await loadRows()
      return false
    }
    return response.data != null
  })
  setSubmitResult(ok)
  if (ok) {
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
          prepend-icon="mdi-close-circle-outline"
          size="small"
          variant="tonal"
        >
          {{ counts.excluded }} not written to
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
</style>
