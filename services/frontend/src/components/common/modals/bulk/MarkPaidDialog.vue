<script lang="ts" setup>
import {computed, watch} from "vue"
import BulkDialogScaffold from "./BulkDialogScaffold.vue"
import {useBulkPreview} from "@/composables/useBulkPreview"
import {useSubmitFeedback} from "@/composables/formUtils"
import {markPaid} from "@/services/api/blueshell/sdk.gen"
import {computeMarkPaidRows} from "@/utils/bulkCompute"
import type {BulkTarget} from "@/utils/bulkTarget"

/**
 * Mark-as-paid per-action dialog. FE-preview: the decision is purely
 * `userId ∈ paidUserIds` — a row that is already paid is SKIPPED (ALREADY_PAID),
 * everything else is INCLUDED. No server preview call. Execute is idempotent.
 * See docs/proposals/bulk-actions/REDESIGN.md §5.2.
 */

defineOptions({name: "MarkPaidDialog", inheritAttrs: false})

interface Props {
  modelValue: boolean
  targets: BulkTarget[]
  contributionPeriodId: number | null
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

// Compute rows reactively from targets
const computedRows = computed(() => computeMarkPaidRows(props.targets))

const canConfirm = computed(() => includedUserIds.value.length > 0 && !submitting.value)

const help = {
  title: "Mark as paid",
  body:
    "Records a paid contribution for every included member for the selected contribution "
    + "period. Members who are already paid are skipped, and honorary members (who owe no "
    + "contribution) are skipped too. This only updates the paid status; it does not send "
    + "any email. The action is idempotent, so re-running it is safe.",
}

async function onConfirm() {
  if (!canConfirm.value || props.contributionPeriodId == null) return
  const ok = await submit(async () => {
    const resp = await markPaid({
      body: {userIds: includedUserIds.value, contributionPeriodId: props.contributionPeriodId as number},
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

// Update rows when dialog state changes
watch(
  () => props.modelValue,
  (isOpen) => {
    if (isOpen) {
      setRows(computedRows.value)
    } else {
      reset()
    }
  },
  {immediate: true},
)

// Also update when targets change
watch(computedRows, (newRows) => {
  if (props.modelValue) {
    setRows(newRows)
  }
})
</script>

<template>
  <bulk-dialog-scaffold
    v-model="open"
    v-model:reinclude-overrides="reincludeOverrides"
    :can-confirm="canConfirm"
    confirm-label="Mark paid"
    :counts="counts"
    :help="help"
    icon="mdi-cash-check"
    :included-count="includedUserIds.length"
    :rows="rows"
    :show-submit-status="showSubmitStatus"
    :submit-state="submitState"
    :submitting="submitting"
    title="Mark as paid"
    @cancel="emit('update:modelValue', false)"
    @confirm="onConfirm"
  />
</template>
