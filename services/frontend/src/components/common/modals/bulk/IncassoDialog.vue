<script lang="ts" setup>
import {computed, ref, watch} from "vue"
import BulkDialogScaffold, {type BulkColumn} from "./BulkDialogScaffold.vue"
import EmailPreviewPanel from "./EmailPreviewPanel.vue"
import {useBulkPreview} from "@/composables/useBulkPreview"
import {useEmailPreview} from "@/composables/useEmailPreview"
import {useSubmitFeedback} from "@/composables/formUtils"
import {executeBulkIncassoNotification, previewIncassoNotification} from "@/services/api/blueshell/sdk.gen"
import {computeIncassoRows} from "@/utils/bulkCompute"
import type {BulkRow} from "@/utils/bulkRow"
import type {ContributionPeriodResponse} from "@/services/api"
import {effectiveAmount, feeTypeItems, type FeeType} from "@/utils/feePreview"
import {halfYearCutoffDefault, type BulkTarget} from "@/utils/bulkTarget"

/**
 * Incasso-notification per-action dialog. FE preview: as reminder plus incasso checks
 * (INCASSO_MISMATCH for members not marked for incasso). The operator can forcibly
 * include WARNING rows and override each included row's fee type. A dedicated Amount
 * column shows the € for the selected fee type. No server preview call.
 * See docs/proposals/bulk-actions/REDESIGN.md §5.2.
 */

defineOptions({name: "IncassoDialog", inheritAttrs: false})

interface Props {
  modelValue: boolean
  targets: BulkTarget[]
  period: ContributionPeriodResponse | null
  serverToday: string
  latestPeriod?: ContributionPeriodResponse | null
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

// Per-row fee-type selections, defaulting to the auto-selected recommendation per row.
const feeTypeSelections = ref<Record<number, FeeType>>({})

const scaffold = ref<{validate: () => Promise<boolean>} | null>(null)

const {rows, counts, includedUserIds, reincludeOverrides, submitting, setRows, submit, reset} =
  useBulkPreview()
const {submitState, showSubmitStatus, setSubmitResult} = useSubmitFeedback()
const preview = useEmailPreview()

// The period used for cutoff defaulting and validation bounds. This MUST be the period
// the operator is acting on (the selected period), not the globally-latest period: the
// cutoff and its validation are meaningful only relative to the period being processed.
// Using the global latest broke parallel runs, where an unrelated newer period became the
// bound and the (valid, in-selected-period) cutoff was rejected, so confirm silently
// no-opped and the dialog never closed. Fall back to latestPeriod only when nothing is
// selected.
const boundsPeriod = computed(() => props.period ?? props.latestPeriod ?? null)

// ── Validation rules ──────────────────────────────────────────────────────────
const incassoDateRules = [
  (v: string) => !!v || "Expected incasso date is required.",
  (v: string) =>
    !v || !props.serverToday || v > props.serverToday || "Expected incasso date must be after today.",
]

const cutoffRules = [
  (v: string) => !!v || "Half-year cutoff date is required.",
  (v: string) => {
    const p = boundsPeriod.value
    if (!v || !p) return true
    if (v < p.startDate || v > p.endDate) {
      return "Cutoff date must fall within the selected contribution period."
    }
    return true
  },
]

// Compute rows reactively from targets, period, and cutoffDate
const computedRows = computed(() =>
  computeIncassoRows(props.targets, props.period, cutoffDate.value),
)

function rowAmount(row: BulkRow): number | null {
  const selected = feeTypeSelections.value[row.userId] ?? row.recommendedFeeType
  return effectiveAmount(selected, props.period)
}

const columns: BulkColumn[] = [
  {key: "name", header: "Member", sortable: true},
  {key: "memberType", header: "Type", sortable: true},
  {key: "disposition", header: "Status", sortable: true},
  {key: "memberSince", header: "Member since", sortable: true},
  {key: "fee", header: "Fee type", width: "200px"},
  {key: "amount", header: "Amount", align: "center", sortable: true, width: "90px"},
  {key: "note", header: "Note"},
]

const help = {
  title: "Send incasso notification",
  body:
    "Emails an incasso (direct-debit) notification to every included member for the selected "
    + "contribution period, announcing the amount and the expected incasso date. Members who are "
    + "not marked for incasso are warned and left out by default (tick Forcibly include to send "
    + "anyway). Honorary members and members without an email are never sent. The fee type is "
    + "auto-selected from the half-year cutoff date and can be changed per member; the Amount "
    + "column shows what each member will be debited. Confirming sends the emails immediately "
    + "(this cannot be undone).",
}

const canConfirm = computed(() => includedUserIds.value.length > 0 && !submitting.value)

// ── Email preview ─────────────────────────────────────────────────────────────
// Selectable preview recipients: the currently-included users (INCLUDED ∪ re-included
// WARNING), shown by name. Preview is faithful to what each included user would receive.
const previewUserOptions = computed(() => {
  const included = new Set(includedUserIds.value)
  return rows.value
    .filter((r) => included.has(r.userId))
    .map((r) => ({value: r.userId, title: r.name}))
})

// Default the preview recipient to the first included user; keep it valid as rows change.
watch(
  previewUserOptions,
  (options) => {
    const current = preview.selectedUserId.value
    if (options.length === 0) {
      preview.selectedUserId.value = null
    } else if (current == null || !options.some((o) => o.value === current)) {
      preview.selectedUserId.value = options[0]!.value
    }
  },
  {immediate: true},
)

// A preview needs a period and an expected incasso date to render faithfully.
const previewInputsReady = computed(() => !!props.period && !!expectedIncassoDate.value)

async function onPreview() {
  // The preview button is always clickable; validate the pinned form first so invalid
  // dates surface inline (red fields + messages) and abort before hitting the API.
  const valid = (await scaffold.value?.validate()) ?? true
  if (!valid) return
  const periodId = props.period?.id
  if (periodId == null || !expectedIncassoDate.value) return
  await preview.runPreview(async (userId) => {
    const feeType = feeTypeSelections.value[userId] ?? "FULL_YEAR_FEE"
    const resp = await previewIncassoNotification({
      body: {
        userId,
        contributionPeriodId: periodId,
        feeType,
        expectedIncassoDate: expectedIncassoDate.value,
      },
    })
    return resp.data ?? null
  })
}

// Defence-in-depth guard mirroring the v-form rules, so an invalid submit never reaches
// the API even though the confirm button stays clickable.
const datesValid = computed(() => {
  if (!expectedIncassoDate.value || !cutoffDate.value) return false
  if (props.serverToday && expectedIncassoDate.value <= props.serverToday) return false
  const p = boundsPeriod.value
  if (p && (cutoffDate.value < p.startDate || cutoffDate.value > p.endDate)) return false
  return true
})

function seedFeeSelections(newRows: BulkRow[]) {
  const selections: Record<number, FeeType> = {}
  for (const row of newRows) {
    if (row.recommendedFeeType) selections[row.userId] = row.recommendedFeeType
  }
  feeTypeSelections.value = selections
}

async function onConfirm() {
  if (!canConfirm.value || !datesValid.value || !props.period) return
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
      cutoffDate.value = halfYearCutoffDefault(boundsPeriod.value)
      seedFeeSelections(computedRows.value)
      setRows(computedRows.value)
    } else {
      reset()
      preview.reset()
      feeTypeSelections.value = {}
    }
  },
  {immediate: true},
)

// Recompute rows and re-seed fee selections whenever the cutoff (or targets) change.
watch(computedRows, (newRows) => {
  if (open.value) {
    seedFeeSelections(newRows)
    setRows(newRows)
  }
})
</script>

<template>
  <bulk-dialog-scaffold
    ref="scaffold"
    v-model="open"
    v-model:reinclude-overrides="reincludeOverrides"
    :can-confirm="canConfirm"
    :columns="columns"
    confirm-label="Send incasso notification"
    :counts="counts"
    :help="help"
    icon="mdi-bank-transfer"
    include-label="Forcibly include"
    :included-count="includedUserIds.length"
    :rows="rows"
    :show-submit-status="showSubmitStatus"
    :submit-state="submitState"
    :submitting="submitting"
    title="Send incasso notification"
    @cancel="emit('update:modelValue', false)"
    @confirm="onConfirm"
  >
    <template #form>
      <div class="mb-4 d-flex bulk-date-row">
        <v-text-field
          v-model="expectedIncassoDate"
          data-testid="bulk-action-expected-incasso-date"
          density="comfortable"
          hide-details="auto"
          label="Expected incasso date"
          placeholder="YYYY-MM-DD"
          prepend-inner-icon="mdi-calendar"
          :rules="incassoDateRules"
          type="date"
        />
        <v-text-field
          v-model="cutoffDate"
          data-testid="bulk-action-cutoff-date"
          density="comfortable"
          hide-details="auto"
          label="Half-year cutoff date"
          placeholder="YYYY-MM-DD"
          prepend-inner-icon="mdi-calendar-end"
          :rules="cutoffRules"
          type="date"
        />
      </div>
      <email-preview-panel
        v-model="preview.selectedUserId.value"
        v-model:dialog-open="preview.dialogOpen.value"
        :error="preview.error.value"
        :html="preview.html.value"
        :inputs-ready="previewInputsReady"
        :loading="preview.loading.value"
        :subject="preview.subject.value"
        :users="previewUserOptions"
        @preview="onPreview"
      />
    </template>

    <template #cell.fee="{row}">
      <template v-if="row.disposition === 'INCLUDED' || (row.disposition === 'WARNING' && reincludeOverrides[row.userId])">
        <v-select
          v-model="feeTypeSelections[row.userId]"
          class="bulk-feetype-select"
          :data-testid="`bulk-preview-feetype-${row.userId}`"
          density="compact"
          hide-details
          item-title="title"
          item-value="value"
          :items="feeTypeItems"
          variant="plain"
        />
      </template>
      <span
        v-else
        class="text-medium-emphasis"
      >—</span>
    </template>

    <template #cell.amount="{row}">
      <span
        v-if="rowAmount(row) != null"
        :data-testid="`bulk-preview-amount-${row.userId}`"
        class="text-caption"
      >€ {{ rowAmount(row) }}</span>
      <span
        v-else
        class="text-medium-emphasis"
      >—</span>
    </template>
  </bulk-dialog-scaffold>
</template>

<style lang="scss" scoped>
.bulk-date-row {
  gap: 12px;
  // Size each date cell independently so a validation message under one field does
  // not stretch/misalign the other; both fields use hide-details="auto" so their
  // input boxes keep an equal height and baseline and only the offending field grows.
  align-items: flex-start;

  > * {
    flex: 1 1 0;
  }
}

// Cleaner fee-type selector: an outlined, compact select reads as an intentional field in
// the table cell rather than the default underlined input. Constrain the width so it sits
// tidily in the Fee-type column.
.bulk-feetype-select {
  min-width: 150px;
  max-width: 190px;
}
</style>
