<script lang="ts" setup>
import {computed, ref, watch} from "vue"
import BulkDialogScaffold from "./BulkDialogScaffold.vue"
import {useBulkPreview} from "@/composables/useBulkPreview"
import {useSubmitFeedback} from "@/composables/formUtils"
import {previewBulkReminder, executeBulkReminder} from "@/services/api/blueshell/sdk.gen"
import type {BulkPreviewRow} from "@/services/api/blueshell/types.gen"
import type {ContributionPeriodResponse} from "@/services/api"
import {effectiveAmount, feeTypeItems, type FeeType} from "@/utils/feePreview"

/**
 * Contribution-reminder per-action dialog. SERVER preview (fee tier needs the latest
 * membership start, alreadyPaid, and lastSentOn from audit — none reliably in the FE).
 * The operator can re-include already-paid WARNING rows and override each included row's
 * fee type; the € shown updates live via feePreview.effectiveAmount (no extra preview
 * round-trip). Only includedUserIds + feeTypeOverrides are sent to execute — preview is
 * immutable server truth. See docs/proposals/bulk-actions/REDESIGN.md §3 & §5.2.
 */

defineOptions({name: "ReminderDialog"})

interface Props {
  modelValue: boolean
  userIds: number[]
  contributionPeriodId: number | null
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

const paymentDueDate = ref("")
const cutoffDate = ref("")

// Per-row fee-type selections, defaulting to the server's recommendation per row.
const feeTypeSelections = ref<Record<number, FeeType>>({})

const {rows, counts, includedUserIds, reincludeOverrides, loading, error, submitting, loadPreview, submit, reset} =
  useBulkPreview()
const {submitState, showSubmitStatus, setSubmitResult} = useSubmitFeedback()

/** Client-side validation: cutoff must fall within the selected period. */
const cutoffError = computed<string | null>(() => {
  if (!cutoffDate.value || !props.period) return null
  if (cutoffDate.value < props.period.startDate || cutoffDate.value > props.period.endDate) {
    return "Cutoff date must fall within the selected contribution period."
  }
  return null
})

async function load() {
  if (props.contributionPeriodId == null || !paymentDueDate.value || !cutoffDate.value || cutoffError.value) {
    return
  }
  await loadPreview(async () => {
    const resp = await previewBulkReminder({
      body: {
        userIds: props.userIds,
        contributionPeriodId: props.contributionPeriodId as number,
        cutoffDate: cutoffDate.value,
        paymentDueDate: paymentDueDate.value,
      },
    })
    return {rows: resp.data?.rows ?? []}
  })
  // Seed fee selections from each row's recommended type.
  const selections: Record<number, FeeType> = {}
  for (const row of rows.value) {
    if (row.recommendedFeeType) selections[row.userId] = row.recommendedFeeType
  }
  feeTypeSelections.value = selections
}

function rowAmount(row: BulkPreviewRow): number | null {
  const selected = feeTypeSelections.value[row.userId] ?? row.recommendedFeeType
  return effectiveAmount(selected, props.period)
}

const canConfirm = computed(
  () =>
    !loading.value
    && !error.value
    && !cutoffError.value
    && !!paymentDueDate.value
    && !!cutoffDate.value
    && includedUserIds.value.length > 0
    && !submitting.value,
)

async function onConfirm() {
  if (!canConfirm.value || props.contributionPeriodId == null) return
  // Build overrides only for included users whose selection differs is not required —
  // send the selection for every included user so execute records the operator's choice.
  const overrides: Record<string, FeeType> = {}
  for (const userId of includedUserIds.value) {
    const feeType = feeTypeSelections.value[userId]
    if (feeType) overrides[String(userId)] = feeType
  }
  const ok = await submit(async () => {
    const resp = await executeBulkReminder({
      body: {
        userIds: props.userIds,
        contributionPeriodId: props.contributionPeriodId as number,
        cutoffDate: cutoffDate.value,
        paymentDueDate: paymentDueDate.value,
        includedUserIds: includedUserIds.value,
        feeTypeOverrides: overrides,
      },
    })
    return resp.data != null
  })
  setSubmitResult(ok)
  if (ok) {
    setTimeout(() => {
      emit("update:modelValue", false)
      emit("done")
    }, 1200)
  }
}

watch(
  () => props.modelValue,
  async (isOpen) => {
    if (isOpen) {
      paymentDueDate.value = ""
      cutoffDate.value = props.period?.startDate ?? ""
      feeTypeSelections.value = {}
      reset()
    } else {
      reset()
      feeTypeSelections.value = {}
    }
  },
  // The host swaps in this component via `<component :is>` with modelValue already
  // true, so a non-immediate watch would never fire on the initial mount and the
  // date defaults would not seed. `immediate` guarantees the open path runs.
  {immediate: true},
)

// Reload the server preview when the dates change (and are valid).
watch([paymentDueDate, cutoffDate], async () => {
  if (props.modelValue) await load()
})
</script>

<template>
  <bulk-dialog-scaffold
    v-model="open"
    v-model:reinclude-overrides="reincludeOverrides"
    :can-confirm="canConfirm"
    confirm-label="Send reminder"
    :counts="counts"
    :error="error"
    icon="mdi-email-fast"
    :included-count="includedUserIds.length"
    :loading="loading"
    :rows="rows"
    :show-fee-column="true"
    :show-submit-status="showSubmitStatus"
    :submit-state="submitState"
    :submitting="submitting"
    title="Send contribution reminder"
    @cancel="emit('update:modelValue', false)"
    @confirm="onConfirm"
  >
    <template #form>
      <div class="mb-4 d-flex flex-wrap gap-3">
        <v-text-field
          v-model="paymentDueDate"
          data-testid="bulk-action-payment-due-date"
          density="comfortable"
          hide-details="auto"
          label="Payment due date"
          placeholder="YYYY-MM-DD"
          prepend-inner-icon="mdi-calendar"
          style="max-width: 240px"
          type="date"
        />
        <v-text-field
          v-model="cutoffDate"
          data-testid="bulk-action-cutoff-date"
          density="comfortable"
          :error-messages="cutoffError ? [cutoffError] : []"
          hide-details="auto"
          label="Half-year cutoff date"
          placeholder="YYYY-MM-DD"
          prepend-inner-icon="mdi-calendar-end"
          style="max-width: 240px"
          type="date"
        />
      </div>
    </template>

    <template #fee-cell="{row}">
      <template v-if="row.disposition === 'INCLUDED' || (row.disposition === 'WARNING' && reincludeOverrides[row.userId])">
        <div class="d-flex align-center gap-2">
          <v-select
            v-model="feeTypeSelections[row.userId]"
            :data-testid="`bulk-preview-feetype-${row.userId}`"
            density="compact"
            hide-details
            :items="feeTypeItems"
            style="min-width: 150px; max-width: 180px"
          />
          <span
            v-if="rowAmount(row) != null"
            class="text-caption text-medium-emphasis"
          >€ {{ rowAmount(row) }}</span>
        </div>
      </template>
      <span
        v-else-if="row.amount != null"
        class="text-medium-emphasis"
      >€ {{ row.amount }}</span>
      <span
        v-else
        class="text-medium-emphasis"
      >—</span>
    </template>
  </bulk-dialog-scaffold>
</template>

<style lang="scss" scoped>
.gap-3 {
  gap: 12px;
}

.gap-2 {
  gap: 8px;
}
</style>
