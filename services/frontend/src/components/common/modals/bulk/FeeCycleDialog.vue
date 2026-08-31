<script lang="ts" setup>
import {computed, ref, watch} from "vue"
import {DateTime} from "luxon"
import BulkDialogScaffold, {type BulkColumn} from "./BulkDialogScaffold.vue"
import EmailPreviewDialog from "@/components/common/modals/EmailPreviewDialog.vue"
import {useBulkPreview} from "@/composables/useBulkPreview"
import {useEmailPreview} from "@/composables/useEmailPreview"
import {useSubmitFeedback} from "@/composables/formUtils"
import {previewFeeCycle, previewFeeCycleEmail, sendFeeCycle} from "@/services/api"
import type {ContributionPeriodResponse} from "@/services/api"
import {parseBulkRejection, type BulkRejection} from "@/utils/bulkRejection"
import {BulkFeeType, type BulkRow} from "@/utils/bulkRow"
import {changedFeeTypes, countBySide, feeCycleSideLabel, lastAskedLabel, toBulkRows} from "@/utils/feeCycle"
import {effectiveAmount, feeTypeItems, feeTypeLabels} from "@/utils/feePreview"

/**
 * The fee cycle for one contribution period: ask every unpaid member for what they owe.
 *
 * One operation over a partition rather than two sends. The rows come from the api, which
 * decides them once so the preview and the send cannot disagree; the treasurer supplies the
 * two dates and may change a member's fee *type*, never an amount.
 */
defineOptions({name: "FeeCycleDialog", inheritAttrs: false})

interface Props {
  modelValue: boolean
  period: ContributionPeriodResponse | null
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

// The shared preview stack, unchanged: the composable holds the state and takes the fetch as
// a closure, and the dialog is presentational. Read-only here — the table is what the
// treasurer confirms, and this is a spot-check of one recipient rather than the send itself.
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
const loading = ref(false)
const loadError = ref<string | null>(null)
const rejection = ref<BulkRejection | null>(null)

const today = DateTime.now().toFormat("yyyy-MM-dd")

// The two dates are the only free inputs. Both are in the future because both are a promise
// about something that has not happened yet.
const paymentDueRules = [
  (v: string) => !!v || "A payment due date is required.",
  (v: string) => !v || v > today || "The payment due date must be after today.",
]
const debitDateRules = [
  (v: string) => !!v || "A debit date is required.",
  (v: string) => !v || v > today || "The debit date must be after today.",
]

const columns: BulkColumn[] = [
  {key: "name", header: "Member", sortable: true},
  {key: "memberType", header: "Type", sortable: true},
  {key: "group", header: "Pays by", sortable: true, width: "110px"},
  {key: "disposition", header: "Status", sortable: true},
  {key: "memberSince", header: "Member since", sortable: true},
  {key: "fee", header: "Fee type", width: "180px"},
  {key: "amount", header: "Amount", align: "end", sortable: true, width: "90px"},
  {key: "lastAsked", header: "Last asked", align: "center", width: "110px"},
  {key: "note", header: "Note"},
]

const sideCounts = computed(() => countBySide(rows.value))

const periodRange = computed(() => {
  const period = props.period
  if (!period) return null
  const format = (iso: string) => DateTime.fromISO(iso).toFormat("dd/MM/yyyy")
  return `${format(period.startDate)} – ${format(period.endDate)}`
})

/** The live amount for a row, following whichever fee type is selected for it. */
function rowAmount(row: BulkRow): number | null {
  return effectiveAmount(feeTypeSelections.value[row.userId] ?? row.recommendedFeeType, props.period)
}

/** Only a member the cycle will write to has a fee type worth changing. */
function isEditable(row: BulkRow): boolean {
  return row.disposition === "INCLUDED"
}

function feeLabel(row: BulkRow): string {
  return row.recommendedFeeType ? feeTypeLabels[row.recommendedFeeType] : "—"
}

function seedFeeSelections(next: BulkRow[]) {
  const selections: Record<number, BulkFeeType> = {}
  for (const row of next) {
    if (row.recommendedFeeType) selections[row.userId] = row.recommendedFeeType
  }
  feeTypeSelections.value = selections
}

/**
 * The cycle is read from the api rather than computed here: it is over every unpaid member
 * of the period, not over a selection this page holds.
 */
async function loadCycle() {
  const periodId = props.period?.id
  if (periodId == null) return
  loading.value = true
  loadError.value = null
  const {data} = await previewFeeCycle({query: {contributionPeriodId: periodId}})
  loading.value = false
  if (!data) {
    loadError.value = "The fee cycle could not be read."
    setRows([])
    return
  }
  const mapped = toBulkRows(data.rows)
  seedFeeSelections(mapped)
  setRows(mapped)
}

/**
 * Who can be read for. Only a member the cycle writes to has an email, and both sides of
 * the partition are offered so each statement can be checked.
 */
const previewRecipients = computed(() =>
  rows.value
    .filter((row) => row.disposition === "INCLUDED")
    .map((row) => ({value: row.userId, title: `${row.name} — ${feeCycleSideLabel(row.group)}`})),
)

/** Both dates reach the email, so neither can be missing when one is read. */
const canPreviewEmail = computed(() =>
  previewRecipients.value.length > 0 && !!paymentDueDate.value && !!debitDate.value,
)

function openEmailPreview() {
  if (!canPreviewEmail.value) return
  if (previewRecipientId.value == null) previewRecipientId.value = previewRecipients.value[0]!.value
  void renderEmailFor(previewRecipientId.value)
}

/**
 * The api decides which of the two statements this member gets and builds it with the same
 * builder the send uses, so what is read here is what goes out.
 */
async function renderEmailFor(userId: number) {
  const periodId = props.period?.id
  if (periodId == null) return
  await showEmailPreview(async () => {
    const {data} = await previewFeeCycleEmail({
      query: {
        contributionPeriodId: periodId,
        userId,
        paymentDueDate: paymentDueDate.value,
        debitDate: debitDate.value,
        feeType: feeTypeSelections.value[userId],
      },
    })
    return data ?? null
  })
}

// Changing the recipient re-renders, which is what the dialog's recipient slot is for.
watch(previewRecipientId, (userId) => {
  if (userId != null && emailPreviewOpen.value) void renderEmailFor(userId)
})

// A recipient who dropped out of the cycle cannot stay selected.
watch(previewRecipients, (options) => {
  const current = previewRecipientId.value
  if (options.length === 0) {
    previewRecipientId.value = null
  } else if (current == null || !options.some((option) => option.value === current)) {
    previewRecipientId.value = options[0]!.value
  }
})

const canConfirm = computed(() => includedUserIds.value.length > 0 && !submitting.value)

async function onConfirm() {
  const periodId = props.period?.id
  if (!canConfirm.value || periodId == null) return
  rejection.value = null
  const ok = await submit(async () => {
    const response = await sendFeeCycle({
      body: {
        contributionPeriodId: periodId,
        paymentDueDate: paymentDueDate.value,
        debitDate: debitDate.value,
        feeTypeOverrides: changedFeeTypes(rows.value, feeTypeSelections.value),
      },
    })
    // The generated client returns a refusal rather than throwing, so a try/catch here
    // would report success on a send that wrote nothing.
    const refused = parseBulkRejection(response)
    if (refused) {
      rejection.value = refused
      await loadCycle()
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

/** Names the refused rows where the table still knows them, so ids are a fallback. */
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
      void loadCycle()
    } else {
      rejection.value = null
      loadError.value = null
      feeTypeSelections.value = {}
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
  previewRecipientId,
  previewRecipients,
  rowAmount,
  loadCycle,
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
      title: 'Open the fee cycle',
      body:
        'Asks every member of the selected period who has not paid for this year\'s contribution. '
        + 'Members who pay by direct debit are told what will be debited and when; the rest are asked '
        + 'to transfer what they owe by the due date. Which of the two a member receives follows from '
        + 'their direct-debit flag, so it is not a choice here. Honorary members and members with no '
        + 'email address are shown but never written to. You can change a member\'s fee type and the '
        + 'amount follows from the period; there is no field for typing an amount. One confirmation '
        + 'sends both statements, and each send is recorded.',
    }"
    icon="mdi-email-fast"
    :included-count="includedUserIds.length"
    info-box-label="Contribution period"
    :rows="rows"
    :show-submit-status="showSubmitStatus"
    :submit-state="submitState"
    :submitting="submitting"
    title="Fee cycle"
    @cancel="emit('update:modelValue', false)"
    @confirm="onConfirm"
  >
    <!-- Reading one member's email before sending to a hundred. -->
    <template #footer-actions>
      <v-btn
        data-testid="fee-cycle-preview-email-btn"
        :disabled="!canPreviewEmail"
        prepend-icon="mdi-email-search-outline"
        variant="text"
        @click="openEmailPreview"
      >
        Read an email
      </v-btn>
    </template>

    <template #form>
      <div class="mb-4 d-flex fee-cycle-dates">
        <v-text-field
          v-model="paymentDueDate"
          data-testid="fee-cycle-payment-due-date"
          hide-details="auto"
          label="Payment due date"
          placeholder="YYYY-MM-DD"
          prepend-inner-icon="mdi-calendar"
          :rules="paymentDueRules"
          type="date"
        />
        <v-text-field
          v-model="debitDate"
          data-testid="fee-cycle-debit-date"
          hide-details="auto"
          label="Debit date"
          placeholder="YYYY-MM-DD"
          prepend-inner-icon="mdi-calendar-arrow-right"
          :rules="debitDateRules"
          type="date"
        />
      </div>

      <v-alert
        v-if="loadError"
        class="mb-4"
        data-testid="fee-cycle-load-error"
        density="compact"
        type="error"
        variant="tonal"
      >
        {{ loadError }}
      </v-alert>

      <v-alert
        v-if="rejection"
        class="mb-4"
        data-testid="fee-cycle-rejection"
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
        data-testid="fee-cycle-period-info"
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
          data-testid="fee-cycle-count-direct-debit"
          prepend-icon="mdi-bank-transfer-out"
          size="small"
          variant="tonal"
        >
          {{ sideCounts.DIRECT_DEBIT }} by direct debit
        </v-chip>
        <v-chip
          data-testid="fee-cycle-count-transfer"
          prepend-icon="mdi-bank-transfer-in"
          size="small"
          variant="tonal"
        >
          {{ sideCounts.TRANSFER }} by transfer
        </v-chip>
        <v-progress-circular
          v-if="loading"
          data-testid="fee-cycle-loading"
          indeterminate
          size="18"
          width="2"
        />
      </div>
    </template>

    <!-- Which statement a member receives, not a choice: the flag decides it. -->
    <template #cell.group="{row}">
      <span
        class="text-caption"
        :data-testid="`fee-cycle-group-${row.userId}`"
      >{{ feeCycleSideLabel(row.group) }}</span>
    </template>

    <template #cell.fee="{row}">
      <v-select
        v-if="isEditable(row)"
        v-model="feeTypeSelections[row.userId]"
        class="fee-cycle-feetype-select"
        :data-testid="`fee-cycle-feetype-${row.userId}`"
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
        :data-testid="`fee-cycle-feetype-fixed-${row.userId}`"
      >{{ feeLabel(row) }}</span>
    </template>

    <template #cell.amount="{row}">
      <span
        v-if="rowAmount(row) != null"
        class="text-caption"
        :data-testid="`fee-cycle-amount-${row.userId}`"
      >€ {{ rowAmount(row)!.toFixed(2) }}</span>
      <span
        v-else
        class="text-medium-emphasis"
      >—</span>
    </template>

    <template #cell.lastAsked="{row}">
      <span
        class="text-caption text-medium-emphasis"
        :data-testid="`fee-cycle-last-asked-${row.userId}`"
      >{{ lastAskedLabel(row.lastSentOn) }}</span>
    </template>
  </bulk-dialog-scaffold>

  <!--
    The shared preview dialog, read-only: no confirmLabel, because the table above is what
    the treasurer confirms and this shows one recipient out of a hundred.
  -->
  <email-preview-dialog
    v-model="emailPreviewOpen"
    :error="emailPreviewError"
    :loading="emailPreviewLoading"
    :preview="emailPreview"
    title="Fee cycle email"
  >
    <template #recipient>
      <v-select
        v-model="previewRecipientId"
        data-testid="fee-cycle-preview-recipient"
        density="compact"
        hide-details
        item-title="title"
        item-value="value"
        :items="previewRecipients"
        label="Read as"
      />
    </template>
  </email-preview-dialog>
</template>

<style lang="scss" scoped>
// The two dates share the row, as one statement about when money moves.
.fee-cycle-dates {
  gap: 12px;
}
</style>
