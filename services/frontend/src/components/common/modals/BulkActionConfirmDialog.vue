<script lang="ts" setup>
import {computed, ref, watch} from "vue"
import {DateTime} from "luxon"
import BaseModal from "./BaseModal.vue"
import {useSubmitFeedback} from "@/composables/formUtils"
import {
  previewBulkContribution,
  executeBulkContribution,
  previewBulkReminder,
  executeBulkReminder,
  previewBulkEnd,
  executeBulkEnd,
  previewBulkIncassoNotification,
  executeBulkIncassoNotification,
} from "@/services/api/blueshell/sdk.gen"
import type {BulkPreviewResult, BulkPreviewRow} from "@/services/api/blueshell/types.gen"
import {memberTypeLabel} from "@/utils/memberType"

// Type alias for reason values from the generated BulkPreviewRow
type BulkRowReason = 'ALREADY_PAID' | 'NOT_PAID' | 'HONORARY' | 'INCASSO_MISMATCH' | 'NO_ACTIVE_MEMBERSHIP' | 'STARTED_TODAY'

// Fee type enum values — mirrors the backend BulkFeeType enum
type BulkFeeType = 'FULL_YEAR_FEE' | 'HALF_YEAR_FEE' | 'ALUMNI_FEE'

// Human-readable labels for fee types
const feeTypeLabels: Record<BulkFeeType, string> = {
  FULL_YEAR_FEE: "Full-year fee",
  HALF_YEAR_FEE: "Half-year fee",
  ALUMNI_FEE: "Alumni fee",
}

const feeTypeItems: Array<{title: string; value: BulkFeeType}> = [
  {title: feeTypeLabels.FULL_YEAR_FEE, value: "FULL_YEAR_FEE"},
  {title: feeTypeLabels.HALF_YEAR_FEE, value: "HALF_YEAR_FEE"},
  {title: feeTypeLabels.ALUMNI_FEE, value: "ALUMNI_FEE"},
]

defineOptions({name: "BulkActionConfirmDialog"})

// ── Action type ────────────────────────────────────────────────────────────────

export type BulkActionKind =
  | "markPaid"
  | "markUnpaid"
  | "sendReminder"
  | "sendIncasso"
  | "endMembership"

// ── Props ──────────────────────────────────────────────────────────────────────

interface Props {
  modelValue: boolean
  action: BulkActionKind
  userIds: number[]
  contributionPeriodId?: number | null
  /** Half-year cutoff date from the selected period (used as default cutoffDate). */
  halfYearCutoffDate?: string | null
}

const props = withDefaults(defineProps<Props>(), {
  contributionPeriodId: null,
  halfYearCutoffDate: null,
})

const emit = defineEmits<{
  (e: "update:modelValue", value: boolean): void
  (e: "done"): void
}>()

// ── Action config ──────────────────────────────────────────────────────────────

const actionConfig = computed(() => {
  const configs: Record<BulkActionKind, {title: string; icon: string; confirmLabel: string}> = {
    markPaid: {title: "Mark as paid", icon: "mdi-cash-check", confirmLabel: "Mark paid"},
    markUnpaid: {title: "Mark as unpaid", icon: "mdi-cash-remove", confirmLabel: "Mark unpaid"},
    sendReminder: {title: "Send contribution reminder", icon: "mdi-email-fast", confirmLabel: "Send reminder"},
    sendIncasso: {title: "Send incasso notification", icon: "mdi-bank-transfer", confirmLabel: "Send incasso"},
    endMembership: {title: "End membership", icon: "mdi-account-cancel", confirmLabel: "End membership"},
  }
  return configs[props.action]
})

const showPaymentDueDate = computed(() => props.action === "sendReminder")
const showIncassoDate = computed(() => props.action === "sendIncasso")

// ── Form state ─────────────────────────────────────────────────────────────────

const paymentDueDate = ref("")
const expectedIncassoDate = ref("")
const cutoffDate = ref("")

// ── Preview state ──────────────────────────────────────────────────────────────

const preview = ref<BulkPreviewResult | null>(null)
const previewLoading = ref(false)
const previewError = ref<string | null>(null)

// Per-row re-include overrides (for WARNING / EXCLUDED rows that can be opted in)
const reincludeOverrides = ref<Record<number, boolean>>({})
// Per-row fee type selections (for reminder + incasso actions)
const feeTypeSelections = ref<Record<number, BulkFeeType>>({})

// ── Submit state ───────────────────────────────────────────────────────────────

const submitting = ref(false)
const {submitState, showSubmitStatus, setSubmitResult} = useSubmitFeedback()

// ── Computed rows ──────────────────────────────────────────────────────────────

type EnrichedRow = BulkPreviewRow & {
  canReinclude: boolean
}

const enrichedRows = computed<EnrichedRow[]>(() => {
  if (!preview.value) return []
  return preview.value.rows.map((row) => ({
    ...row,
    canReinclude: row.disposition === "WARNING",
  }))
})

const includedUserIds = computed<number[]>(() => {
  if (!preview.value) return []
  return preview.value.rows
    .filter((row) => {
      if (row.disposition === "INCLUDED") return true
      if (row.disposition === "WARNING" && reincludeOverrides.value[row.userId]) return true
      return false
    })
    .map((row) => row.userId)
})

// ── Disposition styling ────────────────────────────────────────────────────────

/**
 * The disposition as it applies after operator overrides: a WARNING row the
 * operator has re-included counts as INCLUDED (it will be acted on). The
 * original reason is still shown in the note column so the flag stays visible.
 */
function effectiveDisposition(row: BulkPreviewRow): string {
  if (row.disposition === "WARNING" && reincludeOverrides.value[row.userId]) return "INCLUDED"
  return row.disposition
}

function rowColorClass(row: BulkPreviewRow): string {
  const d = effectiveDisposition(row)
  if (d === "EXCLUDED") return "bulk-row--excluded"
  if (d === "WARNING") return "bulk-row--warning"
  if (d === "SKIPPED") return "bulk-row--skipped"
  return ""
}

function dispositionLabel(row: BulkPreviewRow): string {
  const d = effectiveDisposition(row)
  if (d === "INCLUDED") return "Included"
  if (d === "EXCLUDED") return "Excluded"
  if (d === "WARNING") return "Warning"
  if (d === "SKIPPED") return "Skipped"
  return d
}

function dispositionColor(row: BulkPreviewRow): string {
  const d = effectiveDisposition(row)
  if (d === "EXCLUDED") return "error"
  if (d === "WARNING") return "warning"
  if (d === "SKIPPED") return "grey"
  return "success"
}

// ── Formatting helpers ─────────────────────────────────────────────────────────

function formatMemberSince(dateStr: string | undefined): string {
  if (!dateStr) return "—"
  const dt = DateTime.fromISO(dateStr)
  return dt.isValid ? dt.toFormat("dd/MM/yyyy") : "—"
}

function getDispositionReasonLabel(reason: BulkRowReason | undefined): string {
  if (!reason) return ""
  const reasonMap: Record<BulkRowReason, string> = {
    INCASSO_MISMATCH: "Not marked for incasso",
    ALREADY_PAID: "Already paid",
    HONORARY: "Honorary — no contribution needed",
    NOT_PAID: "Not paid",
    NO_ACTIVE_MEMBERSHIP: "No active membership",
    STARTED_TODAY: "Started today",
  }
  return reasonMap[reason] ?? reason.replace(/_/g, " ")
}

// ── Preview loading ────────────────────────────────────────────────────────────

async function loadPreview() {
  previewLoading.value = true
  previewError.value = null
  preview.value = null

  try {
    const periodId = props.contributionPeriodId ?? 0

    if (props.action === "markPaid" || props.action === "markUnpaid") {
      const resp = await previewBulkContribution({
        body: {
          userIds: props.userIds,
          contributionPeriodId: periodId,
          operation: props.action === "markPaid" ? "PAID" : "UNPAID",
        },
      })
      if (resp.data) preview.value = resp.data
      else previewError.value = "Failed to load preview."
    } else if (props.action === "sendReminder") {
      const resp = await previewBulkReminder({
        body: {
          userIds: props.userIds,
          contributionPeriodId: periodId,
          paymentDueDate: paymentDueDate.value || "2999-12-31",
          cutoffDate: cutoffDate.value || (props.halfYearCutoffDate ?? "2999-12-31"),
          includedUserIds: props.userIds,
          feeTypeOverrides: {},
        },
      })
      if (resp.data) preview.value = resp.data
      else previewError.value = "Failed to load preview."
    } else if (props.action === "sendIncasso") {
      const resp = await previewBulkIncassoNotification({
        body: {
          userIds: props.userIds,
          contributionPeriodId: periodId,
          expectedIncassoDate: expectedIncassoDate.value || "2999-12-31",
          cutoffDate: cutoffDate.value || (props.halfYearCutoffDate ?? "2999-12-31"),
          includedUserIds: props.userIds,
          feeTypeOverrides: {},
        },
      })
      if (resp.data) preview.value = resp.data
      else previewError.value = "Failed to load preview."
    } else if (props.action === "endMembership") {
      const resp = await previewBulkEnd({
        body: {
          userIds: props.userIds,
        },
      })
      if (resp.data) preview.value = resp.data
      else previewError.value = "Failed to load preview."
    }

    // Reset per-row state after new preview
    reincludeOverrides.value = {}
    feeTypeSelections.value = {}
    if (preview.value) {
      for (const row of preview.value.rows) {
        reincludeOverrides.value[row.userId] = false
        // Default each row's fee type to the backend's recommendation
        if (row.recommendedFeeType) {
          feeTypeSelections.value[row.userId] = row.recommendedFeeType
        }
      }
    }
  } catch {
    previewError.value = "An error occurred loading the preview."
  } finally {
    previewLoading.value = false
  }
}

// ── Watch open state ───────────────────────────────────────────────────────────

watch(
  () => props.modelValue,
  async (open) => {
    if (open) {
      // Reset form inputs
      paymentDueDate.value = ""
      expectedIncassoDate.value = ""
      cutoffDate.value = props.halfYearCutoffDate ?? ""
      await loadPreview()
    } else {
      preview.value = null
      previewError.value = null
      reincludeOverrides.value = {}
      feeTypeSelections.value = {}
    }
  },
)

// ── Refresh preview when dates change ─────────────────────────────────────────

watch([paymentDueDate, expectedIncassoDate], async () => {
  if (props.modelValue && preview.value) {
    await loadPreview()
  }
})

// ── Execute ────────────────────────────────────────────────────────────────────

const canConfirm = computed(() => {
  if (previewLoading.value || previewError.value || !preview.value) return false
  if (showPaymentDueDate.value && !paymentDueDate.value) return false
  if (showIncassoDate.value && !expectedIncassoDate.value) return false
  if (includedUserIds.value.length === 0) return false
  return true
})

async function onConfirm() {
  if (!canConfirm.value) return

  submitting.value = true
  const periodId = props.contributionPeriodId ?? 0

  // Build feeTypeOverrides map for all included users
  const resolvedFeeTypeOverrides: Record<string, BulkFeeType> = {}
  for (const userId of includedUserIds.value) {
    const feeType = feeTypeSelections.value[userId]
    if (feeType) resolvedFeeTypeOverrides[String(userId)] = feeType
  }

  try {
    let ok = false

    if (props.action === "markPaid" || props.action === "markUnpaid") {
      const resp = await executeBulkContribution({
        body: {
          userIds: props.userIds,
          contributionPeriodId: periodId,
          operation: props.action === "markPaid" ? "PAID" : "UNPAID",
        },
      })
      ok = resp.data != null
    } else if (props.action === "sendReminder") {
      const resp = await executeBulkReminder({
        body: {
          userIds: props.userIds,
          contributionPeriodId: periodId,
          paymentDueDate: paymentDueDate.value,
          cutoffDate: cutoffDate.value || (props.halfYearCutoffDate ?? ""),
          includedUserIds: includedUserIds.value,
          feeTypeOverrides: resolvedFeeTypeOverrides,
        },
      })
      ok = resp.data != null
    } else if (props.action === "sendIncasso") {
      const resp = await executeBulkIncassoNotification({
        body: {
          userIds: props.userIds,
          contributionPeriodId: periodId,
          expectedIncassoDate: expectedIncassoDate.value,
          cutoffDate: cutoffDate.value || (props.halfYearCutoffDate ?? ""),
          includedUserIds: includedUserIds.value,
          feeTypeOverrides: resolvedFeeTypeOverrides,
        },
      })
      ok = resp.data != null
    } else if (props.action === "endMembership") {
      const resp = await executeBulkEnd({
        body: {
          userIds: props.userIds,
        },
      })
      ok = resp.data != null
    }

    setSubmitResult(ok)

    if (ok) {
      setTimeout(() => {
        emit("update:modelValue", false)
        emit("done")
      }, 1200)
    }
  } catch {
    setSubmitResult(false)
  } finally {
    submitting.value = false
  }
}

function onCancel() {
  emit("update:modelValue", false)
}

const open = computed({
  get: () => props.modelValue,
  set: (val) => emit("update:modelValue", val),
})

// ── Fee type helpers ───────────────────────────────────────────────────────────

/**
 * Returns true if the operator has changed a row's fee type away from the
 * backend's recommendation.
 */
function isFeeTypeChanged(row: BulkPreviewRow): boolean {
  if (!row.recommendedFeeType) return false
  const selected = feeTypeSelections.value[row.userId]
  return selected !== undefined && selected !== row.recommendedFeeType
}

/**
 * Returns a tooltip string describing what the recommended fee type was.
 */
function feeTypeChangedTooltip(row: BulkPreviewRow): string {
  if (!row.recommendedFeeType) return ""
  return `Changed from recommended: ${feeTypeLabels[row.recommendedFeeType]}`
}
</script>

<template>
  <base-modal
    v-model="open"
    :save-disabled="!canConfirm || submitting"
    :save-icon="actionConfig.icon"
    :save-label="actionConfig.confirmLabel"
    :save-loading="submitting"
    :save-show-status="showSubmitStatus"
    :save-submit-state="submitState"
    :title="actionConfig.title"
    data-testid="bulk-action-dialog"
    max-width="960"
    save-testid="bulk-action-confirm-btn"
    scrollable
    show-cancel
    show-save
    @cancel="onCancel"
    @save="onConfirm"
  >
    <!-- Date inputs (action-specific) -->
    <div
      v-if="showPaymentDueDate || showIncassoDate"
      class="mb-4 d-flex flex-wrap gap-3"
    >
      <v-text-field
        v-if="showPaymentDueDate"
        v-model="paymentDueDate"
        data-testid="bulk-action-payment-due-date"
        :density="'comfortable'"
        hide-details="auto"
        label="Payment due date"
        placeholder="YYYY-MM-DD"
        prepend-inner-icon="mdi-calendar"
        style="max-width: 240px"
        type="date"
      />
      <v-text-field
        v-if="showIncassoDate"
        v-model="expectedIncassoDate"
        data-testid="bulk-action-expected-incasso-date"
        :density="'comfortable'"
        hide-details="auto"
        label="Expected incasso date"
        placeholder="YYYY-MM-DD"
        prepend-inner-icon="mdi-calendar"
        style="max-width: 240px"
        type="date"
      />
      <v-text-field
        v-if="showPaymentDueDate || showIncassoDate"
        v-model="cutoffDate"
        data-testid="bulk-action-cutoff-date"
        :density="'comfortable'"
        hide-details="auto"
        label="Half-year cutoff date"
        placeholder="YYYY-MM-DD"
        prepend-inner-icon="mdi-calendar-end"
        style="max-width: 240px"
        type="date"
      />
    </div>

    <!-- Loading skeleton -->
    <div
      v-if="previewLoading"
      class="d-flex align-center justify-center py-8"
    >
      <v-progress-circular
        color="primary"
        indeterminate
        size="40"
      />
      <span class="ml-3 text-medium-emphasis">Loading preview…</span>
    </div>

    <!-- Error state -->
    <v-alert
      v-else-if="previewError"
      :text="previewError"
      class="mb-4"
      closable
      type="error"
      @click:close="previewError = null"
    />

    <!-- Preview content -->
    <template v-else-if="preview">
      <!-- Counts summary -->
      <div
        class="bulk-counts mb-4"
        data-testid="bulk-action-counts"
      >
        <v-chip
          class="mr-2"
          color="primary"
          size="small"
          variant="tonal"
        >
          {{ preview.counts.selected }} selected
        </v-chip>
        <v-chip
          class="mr-2"
          color="success"
          size="small"
          variant="tonal"
        >
          {{ includedUserIds.length }} will apply
        </v-chip>
        <v-chip
          v-if="preview.counts.warned > 0"
          class="mr-2"
          color="warning"
          size="small"
          variant="tonal"
        >
          {{ preview.counts.warned }} with warnings
        </v-chip>
        <v-chip
          v-if="preview.counts.excluded > 0"
          class="mr-2"
          color="error"
          size="small"
          variant="tonal"
        >
          {{ preview.counts.excluded }} excluded
        </v-chip>
        <v-chip
          v-if="preview.counts.skipped > 0"
          class="mr-2"
          color="grey"
          size="small"
          variant="tonal"
        >
          {{ preview.counts.skipped }} skipped
        </v-chip>
      </div>

      <!-- Per-user rows table -->
      <v-table
        density="compact"
        data-testid="bulk-action-preview-table"
      >
        <thead>
          <tr>
            <th>Member</th>
            <th>Type</th>
            <th>Status</th>
            <th>Member since</th>
            <th v-if="showPaymentDueDate || showIncassoDate">
              Fee type
            </th>
            <th>Note</th>
            <th v-if="enrichedRows.some((r) => r.canReinclude)">
              Include
            </th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="row in enrichedRows"
            :key="row.userId"
            :class="rowColorClass(row)"
            :data-testid="`bulk-preview-row-${row.userId}`"
          >
            <td class="font-weight-medium">
              {{ row.name }}
            </td>
            <td class="text-caption text-medium-emphasis">
              {{ memberTypeLabel(row.memberType) }}
            </td>
            <td>
              <v-chip
                :color="dispositionColor(row)"
                :data-testid="`bulk-preview-disposition-${row.userId}`"
                size="x-small"
                variant="tonal"
              >
                {{ dispositionLabel(row) }}
              </v-chip>
            </td>
            <td
              class="text-caption text-medium-emphasis"
              :data-testid="`bulk-preview-member-since-${row.userId}`"
            >
              {{ formatMemberSince(row.memberSince) }}
            </td>
            <td v-if="showPaymentDueDate || showIncassoDate">
              <template v-if="row.disposition === 'INCLUDED' || (row.disposition === 'WARNING' && reincludeOverrides[row.userId])">
                <div class="d-flex align-center gap-2">
                  <v-select
                    v-model="feeTypeSelections[row.userId]"
                    :data-testid="`bulk-preview-feetype-${row.userId}`"
                    :items="feeTypeItems"
                    density="compact"
                    hide-details
                    style="min-width: 150px; max-width: 180px"
                  />
                  <span
                    v-if="row.amount != null"
                    class="text-caption text-medium-emphasis"
                  >€ {{ row.amount }}</span>
                  <v-tooltip
                    v-if="isFeeTypeChanged(row)"
                    :text="feeTypeChangedTooltip(row)"
                    location="top"
                  >
                    <template #activator="{props: tooltipProps}">
                      <v-icon
                        v-bind="tooltipProps"
                        color="warning"
                        icon="mdi-alert-circle-outline"
                        size="small"
                      />
                    </template>
                  </v-tooltip>
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
            </td>
            <td
              class="text-caption"
              :data-testid="`bulk-preview-note-${row.userId}`"
            >
              <span
                v-if="getDispositionReasonLabel(row.reason)"
                :class="row.disposition === 'EXCLUDED' ? 'text-error' : row.disposition === 'WARNING' ? 'text-warning' : ''"
              >
                {{ getDispositionReasonLabel(row.reason) }}
              </span>
              <span
                v-if="row.lastSentOn"
                class="text-medium-emphasis"
              >
                Last sent {{ row.lastSentOn }}
              </span>
            </td>
            <td v-if="enrichedRows.some((r) => r.canReinclude)">
              <v-checkbox
                v-if="row.canReinclude"
                v-model="reincludeOverrides[row.userId]"
                :data-testid="`bulk-preview-reinclude-${row.userId}`"
                color="primary"
                density="compact"
                hide-details
              />
            </td>
          </tr>
        </tbody>
      </v-table>
    </template>
  </base-modal>
</template>

<style lang="scss" scoped>
.bulk-row--excluded td {
  background-color: rgba(var(--v-theme-error), 0.08);
  color: rgb(var(--v-theme-error));
}

.bulk-row--warning td {
  background-color: rgba(var(--v-theme-warning), 0.08);
}

.bulk-row--skipped td {
  opacity: 0.55;
}

.gap-3 {
  gap: 12px;
}
</style>
