<script lang="ts" setup>
import {computed, ref, watch} from "vue"
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
// Per-row amount overrides
const amountOverrides = ref<Record<number, number | null>>({})

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

function rowColorClass(row: BulkPreviewRow): string {
  if (row.disposition === "EXCLUDED") return "bulk-row--excluded"
  if (row.disposition === "WARNING") return "bulk-row--warning"
  if (row.disposition === "SKIPPED") return "bulk-row--skipped"
  return ""
}

function dispositionLabel(row: BulkPreviewRow): string {
  if (row.disposition === "INCLUDED") return "Included"
  if (row.disposition === "EXCLUDED") return "Excluded"
  if (row.disposition === "WARNING") return "Warning"
  if (row.disposition === "SKIPPED") return "Skipped"
  return row.disposition
}

function dispositionColor(row: BulkPreviewRow): string {
  if (row.disposition === "EXCLUDED") return "error"
  if (row.disposition === "WARNING") return "warning"
  if (row.disposition === "SKIPPED") return "grey"
  return "success"
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
          amountOverrides: {},
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
          amountOverrides: {},
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
    amountOverrides.value = {}
    if (preview.value) {
      for (const row of preview.value.rows) {
        reincludeOverrides.value[row.userId] = false
        amountOverrides.value[row.userId] = row.amount ?? null
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
      amountOverrides.value = {}
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
  return true
})

async function onConfirm() {
  if (!canConfirm.value) return

  submitting.value = true
  const periodId = props.contributionPeriodId ?? 0

  const resolvedAmountOverrides: Record<string, number> = {}
  for (const [userId, amount] of Object.entries(amountOverrides.value)) {
    if (amount != null) resolvedAmountOverrides[userId] = amount
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
          amountOverrides: resolvedAmountOverrides,
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
          amountOverrides: resolvedAmountOverrides,
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
    max-width="760"
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
          {{ preview.counts.willApply }} will apply
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
            <th v-if="showPaymentDueDate || showIncassoDate">
              Amount
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
              {{ row.memberType ?? "—" }}
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
            <td v-if="showPaymentDueDate || showIncassoDate">
              <v-text-field
                v-if="row.disposition === 'INCLUDED' || (row.disposition === 'WARNING' && reincludeOverrides[row.userId])"
                v-model.number="amountOverrides[row.userId]"
                :data-testid="`bulk-preview-amount-${row.userId}`"
                density="compact"
                hide-details
                min="0"
                prefix="€"
                step="0.01"
                style="max-width: 100px"
                type="number"
              />
              <span
                v-else-if="row.amount != null"
                class="text-medium-emphasis"
              >€ {{ row.amount }}</span>
              <span
                v-else
                class="text-medium-emphasis"
              >—</span>
            </td>
            <td class="text-caption">
              <span
                v-if="row.reason"
                :class="row.disposition === 'EXCLUDED' ? 'text-error' : row.disposition === 'WARNING' ? 'text-warning' : ''"
              >
                {{ row.reason }}
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
