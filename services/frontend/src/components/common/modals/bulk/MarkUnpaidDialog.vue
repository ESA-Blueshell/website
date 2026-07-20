<script lang="ts" setup>
import {computed, watch} from "vue"
import BulkDialogScaffold from "./BulkDialogScaffold.vue"
import {useBulkPreview} from "@/composables/useBulkPreview"
import {useSubmitFeedback} from "@/composables/formUtils"
import {markUnpaid} from "@/services/api/blueshell/sdk.gen"
import {computeMarkUnpaidRows} from "@/utils/bulkCompute"
import type {BulkTarget} from "@/utils/bulkTarget"

/**
 * Mark-as-unpaid per-action dialog. FE-preview: the decision is purely
 * `userId ∉ paidUserIds` — a row that is not paid is SKIPPED (NOT_PAID),
 * everything else is INCLUDED. No server preview call. Execute is idempotent.
 */

defineOptions({name: "MarkUnpaidDialog", inheritAttrs: false})

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
const computedRows = computed(() => computeMarkUnpaidRows(props.targets))

const canConfirm = computed(() => includedUserIds.value.length > 0 && !submitting.value)

async function onConfirm() {
  if (!canConfirm.value || props.contributionPeriodId == null) return
  const ok = await submit(async () => {
    const resp = await markUnpaid({
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
    confirm-label="Mark unpaid"
    :counts="counts"
    icon="mdi-cash-remove"
    :included-count="includedUserIds.length"
    :rows="rows"
    :show-submit-status="showSubmitStatus"
    :submit-state="submitState"
    :submitting="submitting"
    title="Mark as unpaid"
    @cancel="emit('update:modelValue', false)"
    @confirm="onConfirm"
  />
</template>
