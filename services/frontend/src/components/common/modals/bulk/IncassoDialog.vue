<script lang="ts" setup>
import {computed, ref, watch} from "vue"
import BulkDialogScaffold from "./BulkDialogScaffold.vue"
import {useBulkPreview} from "@/composables/useBulkPreview"
import {useSubmitFeedback} from "@/composables/formUtils"
import {executeBulkIncassoNotification} from "@/services/api/blueshell/sdk.gen"
import {computeIncassoRows} from "@/utils/bulkCompute"
import type {BulkRow} from "@/utils/bulkRow"
import type {ContributionPeriodResponse} from "@/services/api"
import {effectiveAmount, feeTypeItems, type FeeType} from "@/utils/feePreview"
import type {BulkTarget} from "@/utils/bulkTarget"

/**
 * Incasso-notification per-action dialog. FE preview: as reminder plus incasso checks.
 * The operator can re-include WARNING rows and override each included row's fee type.
 * No server preview call.
 * See docs/proposals/bulk-actions/REDESIGN.md §5.2.
 */

defineOptions({name: "IncassoDialog", inheritAttrs: false})

interface Props {
  modelValue: boolean
  targets: BulkTarget[]
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

const expectedIncassoDate = ref("")
const cutoffDate = ref("")

// Per-row fee-type selections, defaulting to the server's recommendation per row.
const feeTypeSelections = ref<Record<number, FeeType>>({})

const {rows, counts, includedUserIds, reincludeOverrides, submitting, setRows, submit, reset} =
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

// Compute rows reactively from targets, period, and cutoffDate
const computedRows = computed(() => computeIncassoRows(props.targets, props.period, cutoffDate.value))

function rowAmount(row: BulkRow): number | null {
  const selected = feeTypeSelections.value[row.userId] ?? row.recommendedFeeType
  return effectiveAmount(selected, props.period)
}

const canConfirm = computed(
  () =>
    !cutoffError.value
    && !!props.period
    && !!expectedIncassoDate.value
    && !!cutoffDate.value
    && includedUserIds.value.length > 0
    && !submitting.value,
)

async function onConfirm() {
  if (!canConfirm.value || !props.period) return
  // Build overrides for all included users
  const overrides: Record<string, FeeType> = {}
  for (const userId of includedUserIds.value) {
    const feeType = feeTypeSelections.value[userId]
    if (feeType) overrides[String(userId)] = feeType
  }
  const ok = await submit(async () => {
    const resp = await executeBulkIncassoNotification({
      body: {
        userIds: props.targets.map((t) => t.userId),
        contributionPeriodId: props.period!.id,
        includedUserIds: includedUserIds.value,
        cutoffDate: cutoffDate.value,
        expectedIncassoDate: expectedIncassoDate.value,
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

// Initialize dates when dialog opens, compute rows reactively
watch(
  () => props.modelValue,
  (isOpen) => {
    if (isOpen) {
      expectedIncassoDate.value = ""
      cutoffDate.value = props.period?.startDate ?? ""
      feeTypeSelections.value = {}
      setRows(computedRows.value)
    } else {
      reset()
      feeTypeSelections.value = {}
    }
  },
  {immediate: true},
)

// Seed fee selections from computed rows' recommended types
watch(computedRows, (newRows) => {
  if (open.value) {
    const selections: Record<number, FeeType> = {}
    for (const row of newRows) {
      if (row.recommendedFeeType) selections[row.userId] = row.recommendedFeeType
    }
    feeTypeSelections.value = selections
    setRows(newRows)
  }
})
</script>

<template>
  <bulk-dialog-scaffold
    v-model="open"
    v-model:reinclude-overrides="reincludeOverrides"
    :can-confirm="canConfirm"
    confirm-label="Send incasso"
    :counts="counts"
    icon="mdi-bank-transfer"
    :included-count="includedUserIds.length"
    :rows="rows"
    :show-fee-column="true"
    :show-submit-status="showSubmitStatus"
    :submit-state="submitState"
    :submitting="submitting"
    title="Send incasso notification"
    @cancel="emit('update:modelValue', false)"
    @confirm="onConfirm"
  >
    <template #form>
      <div class="mb-4 d-flex flex-wrap gap-3">
        <v-text-field
          v-model="expectedIncassoDate"
          data-testid="bulk-action-expected-incasso-date"
          density="comfortable"
          hide-details="auto"
          label="Expected incasso date"
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
