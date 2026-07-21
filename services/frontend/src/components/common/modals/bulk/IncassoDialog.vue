<script lang="ts" setup>
import {computed, ref} from "vue"
import BulkDialogScaffold, {type BulkColumn} from "./BulkDialogScaffold.vue"
import EmailPreviewPanel from "./EmailPreviewPanel.vue"
import {useBulkEmailAction, type BulkEmailActionConfig, type BulkScaffoldInstance} from "@/composables/useBulkEmailAction"
import {executeBulkIncassoNotification, previewIncassoNotification} from "@/services/api/blueshell/sdk.gen"
import type {BulkIncassoNotificationExecuteRequest, IncassoNotificationPreviewRequest} from "@/services/api/blueshell/types.gen"
import {computeIncassoRows} from "@/utils/bulkCompute"
import type {ContributionPeriodResponse} from "@/services/api"
import {feeTypeItems} from "@/utils/feePreview"
import type {BulkTarget} from "@/utils/bulkTarget"

/**
 * Incasso-notification per-action dialog. FE preview: as reminder plus incasso checks
 * (INCASSO_MISMATCH for members not marked for incasso). The operator can forcibly
 * include WARNING rows and override each included row's fee type. A dedicated Amount
 * column shows the € for the selected fee type.
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

const expectedIncassoDate = ref("")

// Template ref for the BulkDialogScaffold instance. We use a named function (not an inline
// arrow) to avoid Vue re-creating the callback on every render, which would cause Vue to
// unmount and re-mount the ref each render cycle.
const scaffoldRef = ref<BulkScaffoldInstance | null>(null)
function onScaffoldRef(el: unknown) {
  scaffoldRef.value = (el ?? null) as BulkScaffoldInstance | null
}

// Columns for incasso dialog (no lastReminded column)
const columns: BulkColumn[] = [
  {key: "name", header: "Member", sortable: true},
  {key: "memberType", header: "Type", sortable: true},
  {key: "disposition", header: "Status", sortable: true},
  {key: "memberSince", header: "Member since", sortable: true},
  {key: "fee", header: "Fee type", width: "200px"},
  {key: "amount", header: "Amount", align: "center", sortable: true, width: "90px"},
  {key: "note", header: "Note"},
]

// Config for the bulk email action (incasso-specific)
const actionConfig: BulkEmailActionConfig = {
  dateFieldName: "expectedIncassoDate",
  dateLabel: "Expected incasso date",
  dateTestid: "bulk-action-expected-incasso-date",
  dateValidationRule: (v, serverToday) =>
    !v || !serverToday || v > serverToday || "Expected incasso date must be after today.",
  computeRows: computeIncassoRows,
  executeApi: (body) => executeBulkIncassoNotification({body: body as BulkIncassoNotificationExecuteRequest}),
  previewApi: (body) => previewIncassoNotification({body: body as IncassoNotificationPreviewRequest}),
  columns,
  help: {
    title: "Send incasso notification",
    body:
      "Emails an incasso (direct-debit) notification to every included member for the selected "
      + "contribution period, announcing the amount and the expected incasso date. Members who are "
      + "not marked for incasso are warned and left out by default (tick Forcibly include to send "
      + "anyway). Honorary members and members without an email are never sent. The fee type is "
      + "auto-selected from the half-year cutoff date and can be changed per member; the Amount "
      + "column shows what each member will be debited. Confirming sends the emails immediately "
      + "(this cannot be undone).",
  },
}

const action = useBulkEmailAction(expectedIncassoDate, props, actionConfig, scaffoldRef)

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
  expectedIncassoDate,
  feeTypeSelections: action.feeTypeSelections,
  cutoffDate: action.cutoffDate,
  incassoDateRules: action.dateRules,
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
    confirm-label="Send incasso notification"
    :counts="action.counts.value"
    :get-row-amount="action.rowAmount"
    :help="actionConfig.help"
    icon="mdi-bank-transfer"
    include-label="Forcibly include"
    :included-count="action.includedUserIds.value.length"
    info-box-label="Contribution period"
    :rows="action.rows.value"
    :show-submit-status="action.showSubmitStatus.value"
    :submit-state="action.submitState.value"
    :submitting="action.submitting.value"
    title="Send incasso notification"
    @cancel="emit('update:modelValue', false)"
    @confirm="handleConfirm"
    @update:reinclude-overrides="(v) => (action.reincludeOverrides.value = v)"
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
        v-else
        class="text-medium-emphasis"
      >—</span>
    </template>

    <template #cell.amount="{row}">
      <span
        v-if="action.rowAmount(row) != null"
        :data-testid="`bulk-preview-amount-${row.userId}`"
        class="text-caption"
      >€ {{ action.rowAmount(row) }}</span>
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
  align-items: flex-start;
  flex-wrap: wrap;

  > .v-text-field {
    flex: 1 1 220px;
  }
}

.bulk-feetype-select {
  min-width: 150px;
  max-width: 190px;
}
</style>
