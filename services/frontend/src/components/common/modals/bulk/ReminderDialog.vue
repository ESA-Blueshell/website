<script lang="ts" setup>
import {computed, ref, watch} from "vue"
import {DateTime} from "luxon"
import BulkDialogScaffold, {type BulkColumn} from "./BulkDialogScaffold.vue"
import EmailPreviewPanel from "./EmailPreviewPanel.vue"
import {useBulkPreview} from "@/composables/useBulkPreview"
import {useEmailPreview} from "@/composables/useEmailPreview"
import {useSubmitFeedback} from "@/composables/formUtils"
import {executeBulkReminder, findContributionReminders, previewReminder} from "@/services/api/blueshell/sdk.gen"
import {computeReminderRows} from "@/utils/bulkCompute"
import type {BulkRow} from "@/utils/bulkRow"
import type {ContributionPeriodResponse} from "@/services/api"
import {effectiveAmount, feeTypeItems, feeTypeLabels, type FeeType} from "@/utils/feePreview"
import {halfYearCutoffDefault, type BulkTarget} from "@/utils/bulkTarget"

/**
 * Contribution-reminder per-action dialog. FE preview: computed from targets,
 * period, and cutoffDate. The operator can forcibly include WARNING rows (already-paid
 * or incasso-payers) and override each included row's fee type. A dedicated Amount column
 * shows the € for the selected fee type, and a Last-reminded-at column shows the most
 * recent reminder per user (fetched on open). No server preview call.
 * See docs/proposals/bulk-actions/REDESIGN.md §5.2.
 */

defineOptions({name: "ReminderDialog", inheritAttrs: false})

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

const paymentDueDate = ref("")
const cutoffDate = ref("")

// Per-row fee-type selections, defaulting to the auto-selected recommendation per row.
const feeTypeSelections = ref<Record<number, FeeType>>({})

// userId -> most-recent reminder date (ISO), fetched on open.
const lastRemindedAt = ref<Record<number, string>>({})

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
const paymentDueRules = [
  (v: string) => !!v || "Payment due date is required.",
  (v: string) =>
    !v || !props.serverToday || v > props.serverToday || "Payment due date must be after today.",
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
  computeReminderRows(props.targets, props.period, cutoffDate.value),
)

function rowAmount(row: BulkRow): number | null {
  const selected = feeTypeSelections.value[row.userId] ?? row.recommendedFeeType
  return effectiveAmount(selected, props.period)
}

// Whether a row is currently editable (shows the fee-type select): INCLUDED, or a WARNING
// the operator has forcibly re-included.
function isEditable(row: BulkRow): boolean {
  return row.disposition === "INCLUDED" || (row.disposition === "WARNING" && !!reincludeOverrides.value[row.userId])
}

// Incasso payers are excluded-by-default from the reminder. When NOT forcibly included,
// their fee type and amount are shown struck through to signal "won't be billed". Once
// re-included the strikethrough is dropped and the editable fee-type select takes over.
function isStruck(row: BulkRow): boolean {
  return row.reason === "PAYS_VIA_INCASSO" && !reincludeOverrides.value[row.userId]
}

// The read-only fee-type label for a non-editable row (its recommended type).
function feeLabel(row: BulkRow): string {
  return row.recommendedFeeType ? feeTypeLabels[row.recommendedFeeType] : ""
}

function lastRemindedLabel(userId: number): string {
  const iso = lastRemindedAt.value[userId]
  if (!iso) return "Never"
  const dt = DateTime.fromISO(iso)
  return dt.isValid ? dt.toFormat("dd/MM/yyyy") : "Never"
}

const columns: BulkColumn[] = [
  {key: "name", header: "Member", sortable: true},
  {key: "memberType", header: "Type", sortable: true},
  {key: "disposition", header: "Status", sortable: true},
  {key: "memberSince", header: "Member since", sortable: true},
  {key: "fee", header: "Fee type", width: "200px"},
  {key: "amount", header: "Amount", align: "center", sortable: true, width: "90px"},
  {key: "lastReminded", header: "Last reminded at", align: "center", width: "130px"},
  {key: "note", header: "Note"},
]

const help = {
  title: "Send contribution reminder",
  body:
    "Emails a payment reminder to every included member for the selected contribution period. "
    + "Already-paid members and members who pay via incasso are warned and left out by default "
    + "(tick Forcibly include to send anyway). Honorary members and members without an email are "
    + "never sent. The fee type is auto-selected from the half-year cutoff date and can be changed "
    + "per member; the Amount column shows what each member will be billed. Confirming sends the "
    + "emails immediately (this cannot be undone).",
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

// A preview needs a period and a payment-due date to render faithfully.
const previewInputsReady = computed(() => !!props.period && !!paymentDueDate.value)

async function onPreview() {
  // The preview button is always clickable; validate the pinned form first so invalid
  // dates surface inline (red fields + messages) and abort before hitting the API.
  const valid = (await scaffold.value?.validate()) ?? true
  if (!valid) return
  const periodId = props.period?.id
  if (periodId == null || !paymentDueDate.value) return
  await preview.runPreview(async (userId) => {
    const feeType = feeTypeSelections.value[userId] ?? "FULL_YEAR_FEE"
    const resp = await previewReminder({
      body: {
        userId,
        contributionPeriodId: periodId,
        feeType,
        paymentDueDate: paymentDueDate.value,
      },
    })
    return resp.data ?? null
  })
}

// Whether the date inputs satisfy the same rules the v-form enforces. Used as a
// defence-in-depth guard in onConfirm so an invalid submit never reaches the API even
// though the confirm button stays clickable.
const datesValid = computed(() => {
  if (!paymentDueDate.value || !cutoffDate.value) return false
  if (props.serverToday && paymentDueDate.value <= props.serverToday) return false
  const p = boundsPeriod.value
  if (p && (cutoffDate.value < p.startDate || cutoffDate.value > p.endDate)) return false
  return true
})

// Re-seed fee selections from the current computed rows' recommendations.
function seedFeeSelections(newRows: BulkRow[]) {
  const selections: Record<number, FeeType> = {}
  for (const row of newRows) {
    if (row.recommendedFeeType) selections[row.userId] = row.recommendedFeeType
  }
  feeTypeSelections.value = selections
}

async function loadReminders() {
  lastRemindedAt.value = {}
  const periodId = props.period?.id
  if (periodId == null) return
  const resp = await findContributionReminders({query: {contributionPeriodId: periodId}})
  const reminders = resp.data ?? []
  const latest: Record<number, string> = {}
  for (const r of reminders) {
    const when = r.remindedAt ?? r.createdAt
    if (!when) continue
    const existing = latest[r.userId]
    if (!existing || when > existing) latest[r.userId] = when
  }
  lastRemindedAt.value = latest
}

async function onConfirm() {
  if (!canConfirm.value || !datesValid.value || !props.period) return
  const overrides: Record<string, FeeType> = {}
  for (const userId of includedUserIds.value) {
    const feeType = feeTypeSelections.value[userId]
    if (feeType) overrides[String(userId)] = feeType
  }
  const ok = await submit(async () => {
    const resp = await executeBulkReminder({
      body: {
        userIds: props.targets.map((t) => t.userId),
        contributionPeriodId: props.period!.id,
        includedUserIds: includedUserIds.value,
        cutoffDate: cutoffDate.value,
        paymentDueDate: paymentDueDate.value,
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
      paymentDueDate.value = ""
      // Default the cutoff to the derived half-year midpoint (+1 month, day 1).
      cutoffDate.value = halfYearCutoffDefault(boundsPeriod.value)
      seedFeeSelections(computedRows.value)
      setRows(computedRows.value)
      void loadReminders()
    } else {
      reset()
      preview.reset()
      feeTypeSelections.value = {}
      lastRemindedAt.value = {}
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
    confirm-label="Send reminder"
    :counts="counts"
    :help="help"
    icon="mdi-email-fast"
    include-label="Forcibly include"
    :included-count="includedUserIds.length"
    :rows="rows"
    :show-submit-status="showSubmitStatus"
    :submit-state="submitState"
    :submitting="submitting"
    title="Send contribution reminder"
    @cancel="emit('update:modelValue', false)"
    @confirm="onConfirm"
  >
    <template #form>
      <div class="mb-4 d-flex bulk-date-row">
        <v-text-field
          v-model="paymentDueDate"
          data-testid="bulk-action-payment-due-date"
          density="comfortable"
          hide-details="auto"
          label="Payment due date"
          placeholder="YYYY-MM-DD"
          prepend-inner-icon="mdi-calendar"
          :rules="paymentDueRules"
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
      <template v-if="isEditable(row)">
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
      <!--
        Incasso payers are struck through: they pay via direct debit and are left out of
        the reminder by default, so their recommended fee type is shown for context but
        crossed out and muted.
      -->
      <span
        v-else-if="isStruck(row)"
        class="text-caption bulk-struck"
        :data-testid="`bulk-preview-feetype-struck-${row.userId}`"
      >{{ feeLabel(row) }}</span>
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
        :class="{'bulk-struck': isStruck(row)}"
      >€ {{ rowAmount(row) }}</span>
      <span
        v-else
        class="text-medium-emphasis"
      >—</span>
    </template>

    <template #cell.lastReminded="{row}">
      <span
        class="text-caption text-medium-emphasis"
        :data-testid="`bulk-preview-last-reminded-${row.userId}`"
      >{{ lastRemindedLabel(row.userId) }}</span>
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

// Incasso-payer rows are excluded-by-default: their fee type and amount are shown struck
// through and muted so the operator sees what they *would* be billed while it's clear the
// reminder skips them.
.bulk-struck {
  text-decoration: line-through;
  color: rgba(var(--v-theme-on-surface), 0.5);
}

// Cleaner fee-type selector: an outlined, compact select reads as an intentional field in
// the table cell rather than the default underlined input. Constrain the width so it sits
// tidily in the Fee-type column.
.bulk-feetype-select {
  min-width: 150px;
  max-width: 190px;
}
</style>
