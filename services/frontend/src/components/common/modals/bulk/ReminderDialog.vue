<script lang="ts" setup>
import {computed, ref} from "vue"
import BulkDialogScaffold, {type BulkColumn} from "./BulkDialogScaffold.vue"
import EmailPreviewPanel from "./EmailPreviewPanel.vue"
import {useBulkEmailAction, type BulkEmailActionConfig, type BulkScaffoldInstance} from "@/composables/useBulkEmailAction"
import {executeBulkReminder, findContributionReminders, previewReminder} from "@/services/api/blueshell/sdk.gen"
import type {BulkContributionReminderExecuteRequest, ContributionReminderPreviewRequest} from "@/services/api/blueshell/types.gen"
import {computeReminderRows} from "@/utils/bulkCompute"
import type {ContributionPeriodResponse} from "@/services/api"
import {feeTypeItems} from "@/utils/feePreview"
import type {BulkTarget} from "@/utils/bulkTarget"

/**
 * Contribution-reminder per-action dialog. FE preview: computed from targets,
 * period, and cutoffDate. The operator can forcibly include WARNING rows (already-paid
 * or incasso-payers) and override each included row's fee type. A dedicated Amount column
 * shows the € for the selected fee type, and a Last-reminded-at column shows the most
 * recent reminder per user (fetched on open).
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

const paymentDueDate = ref("")

// Template ref for the BulkDialogScaffold instance. We use a named function (not an inline
// arrow) to avoid Vue re-creating the callback on every render, which would cause Vue to
// unmount and re-mount the ref each render cycle.
const scaffoldRef = ref<BulkScaffoldInstance | null>(null)
function onScaffoldRef(el: unknown) {
  scaffoldRef.value = (el ?? null) as BulkScaffoldInstance | null
}

// Fetch lastRemindedAt on dialog open
async function loadReminders() {
  const periodId = props.period?.id
  if (periodId == null) return {}
  const resp = await findContributionReminders({query: {contributionPeriodId: periodId}})
  const reminders = resp.data ?? []
  const latest: Record<number, string> = {}
  for (const r of reminders) {
    const when = r.remindedAt ?? r.createdAt
    if (!when) continue
    const existing = latest[r.userId]
    if (!existing || when > existing) latest[r.userId] = when
  }
  return latest
}

// Columns for reminder dialog (includes lastReminded)
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

// Config for the bulk email action (reminder-specific)
const actionConfig: BulkEmailActionConfig = {
  dateFieldName: "paymentDueDate",
  dateLabel: "Payment due date",
  dateTestid: "bulk-action-payment-due-date",
  dateValidationRule: (v, serverToday) =>
    !v || !serverToday || v > serverToday || "Payment due date must be after today.",
  computeRows: computeReminderRows,
  executeApi: (body) => executeBulkReminder({body: body as BulkContributionReminderExecuteRequest}),
  previewApi: (body) => previewReminder({body: body as ContributionReminderPreviewRequest}),
  columns,
  help: {
    title: "Send contribution reminder",
    body:
      "Emails a payment reminder to every included member for the selected contribution period. "
      + "Already-paid members and members who pay via incasso are warned and left out by default "
      + "(tick Forcibly include to send anyway). Honorary members and members without an email are "
      + "never sent. The fee type is auto-selected from the half-year cutoff date and can be changed "
      + "per member; the Amount column shows what each member will be billed. Confirming sends the "
      + "emails immediately (this cannot be undone).",
  },
  isStruck: (row, reincludeOverrides) =>
    row.reason === "PAYS_VIA_INCASSO" && !reincludeOverrides[row.userId],
  loadLastSent: loadReminders,
}

const action = useBulkEmailAction(paymentDueDate, props, actionConfig, scaffoldRef)

const open = computed({
  get: () => props.modelValue,
  set: (v) => emit("update:modelValue", v),
})

// Wrap onConfirm to emit done after success
async function handleConfirm() {
  const ok = await action.onConfirm()
  if (ok) {
    setTimeout(() => {
      emit("update:modelValue", false)
      emit("done")
    }, 1200)
  }
}

// Expose for testing
defineExpose({
  paymentDueDate,
  feeTypeSelections: action.feeTypeSelections,
  cutoffDate: action.cutoffDate,
  paymentDueRules: action.dateRules,
  cutoffRules: action.cutoffRules,
  // Expose scaffoldRef so tests can assert the scaffold is wired and spy on validate()
  scaffoldRef,
})
</script>

<template>
  <bulk-dialog-scaffold
    :ref="onScaffoldRef"
    v-model="open"
    :reinclude-overrides="action.reincludeOverrides.value"
    :columns="columns"
    confirm-label="Send reminder"
    :counts="action.counts.value"
    :get-row-amount="action.rowAmount"
    :help="actionConfig.help"
    icon="mdi-email-fast"
    include-label="Forcibly include"
    :included-count="action.includedUserIds.value.length"
    info-box-label="Contribution period"
    :rows="action.rows.value"
    :show-submit-status="action.showSubmitStatus.value"
    :submit-state="action.submitState.value"
    :submitting="action.submitting.value"
    title="Send contribution reminder"
    @cancel="emit('update:modelValue', false)"
    @confirm="handleConfirm"
    @update:reinclude-overrides="(v) => (action.reincludeOverrides.value = v)"
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
          :rules="action.dateRules.value"
          type="date"
        />
        <v-text-field
          v-model="action.cutoffDate.value"
          data-testid="bulk-action-cutoff-date"
          density="comfortable"
          hide-details="auto"
          label="Half-year cutoff date"
          placeholder="YYYY-MM-DD"
          prepend-inner-icon="mdi-calendar-end"
          :rules="action.cutoffRules.value"
          type="date"
        />
      </div>
    </template>

    <template
      v-if="action.periodInfo.value"
      #info-box
    >
      <div
        class="d-flex flex-wrap ga-2 align-center"
        data-testid="bulk-period-info"
      >
        <v-chip
          color="primary"
          prepend-icon="mdi-calendar-range"
          size="small"
          variant="tonal"
        >
          {{ action.periodInfo.value.range }}
        </v-chip>
        <v-chip
          v-for="fee in action.periodInfo.value.fees"
          :key="fee.label"
          :prepend-icon="fee.icon"
          size="small"
          variant="tonal"
        >
          {{ fee.label }}
        </v-chip>
      </div>
    </template>

    <template #footer-actions>
      <email-preview-panel
        v-model="action.emailPreview.selectedUserId.value"
        v-model:dialog-open="action.emailPreview.dialogOpen.value"
        :error="action.emailPreview.error.value"
        :html="action.emailPreview.html.value"
        :inputs-ready="action.previewInputsReady.value"
        :loading="action.emailPreview.loading.value"
        :subject="action.emailPreview.subject.value"
        :users="action.previewUserOptions.value"
        @preview="action.onPreview"
      />
    </template>

    <template #cell.fee="{row}">
      <template v-if="action.isEditable(row)">
        <v-select
          v-model="action.feeTypeSelections.value[row.userId]"
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
        v-else-if="action.isStruck(row, action.reincludeOverrides.value)"
        class="text-caption bulk-struck"
        :data-testid="`bulk-preview-feetype-struck-${row.userId}`"
      >{{ action.feeLabel(row) }}</span>
      <span
        v-else
        class="text-medium-emphasis"
      >—</span>
    </template>

    <template #cell.amount="{row}">
      <span
        v-if="action.rowAmount(row) != null"
        :data-testid="`bulk-preview-amount-${row.userId}`"
        class="text-caption"
        :class="{'bulk-struck': action.isStruck(row, action.reincludeOverrides.value)}"
      >€ {{ action.rowAmount(row) }}</span>
      <span
        v-else
        class="text-medium-emphasis"
      >—</span>
    </template>

    <template #cell.lastReminded="{row}">
      <span
        class="text-caption text-medium-emphasis"
        :data-testid="`bulk-preview-last-reminded-${row.userId}`"
      >{{ action.lastSentLabel(row.userId) }}</span>
    </template>
  </bulk-dialog-scaffold>
</template>

<style lang="scss" scoped>
.bulk-struck {
  text-decoration: line-through;
  color: rgba(var(--v-theme-on-surface), 0.5);
}
</style>
