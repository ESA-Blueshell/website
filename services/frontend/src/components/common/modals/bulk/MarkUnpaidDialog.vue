<script lang="ts" setup>
import {computed, watch} from "vue"
import BulkDialogScaffold from "./BulkDialogScaffold.vue"
import {useBulkPreview} from "@/composables/useBulkPreview"
import {useSubmitFeedback} from "@/composables/formUtils"
import {markUnpaid} from "@/services/api/blueshell/sdk.gen"
import type {BulkPreviewRow} from "@/services/api/blueshell/types.gen"

/**
 * Mark-as-unpaid per-action dialog. Mirror of MarkPaidDialog: a paid row is INCLUDED
 * (it will be unmarked), an unpaid row is SKIPPED (NOT_PAID). FE-preview, no server call.
 * See docs/proposals/bulk-actions/REDESIGN.md §5.2.
 */

defineOptions({name: "MarkUnpaidDialog"})

interface Props {
  modelValue: boolean
  userIds: number[]
  contributionPeriodId: number | null
  namesById: Record<number, string>
  paidUserIds: Set<number>
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

function computeRows(): BulkPreviewRow[] {
  return props.userIds.map((userId) => {
    const isPaid = props.paidUserIds.has(userId)
    return {
      userId,
      name: props.namesById[userId] ?? String(userId),
      disposition: isPaid ? "INCLUDED" : "SKIPPED",
      reason: isPaid ? undefined : "NOT_PAID",
    }
  })
}

const canConfirm = computed(() => includedUserIds.value.length > 0 && !submitting.value)

async function onConfirm() {
  if (!canConfirm.value || props.contributionPeriodId == null) return
  const ok = await submit(async () => {
    const resp = await markUnpaid({
      body: {userIds: props.userIds, contributionPeriodId: props.contributionPeriodId as number},
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
  (isOpen) => {
    if (isOpen) setRows(computeRows())
    else reset()
  },
)
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
