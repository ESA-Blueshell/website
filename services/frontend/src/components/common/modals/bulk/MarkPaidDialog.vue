<script lang="ts" setup>
import {computed, watch} from "vue"
import BulkDialogScaffold from "./BulkDialogScaffold.vue"
import {useBulkPreview} from "@/composables/useBulkPreview"
import {useSubmitFeedback} from "@/composables/formUtils"
import {markPaid} from "@/services/api/blueshell/sdk.gen"
import type {BulkPreviewRow} from "@/services/api/blueshell/types.gen"

/**
 * Mark-as-paid per-action dialog. FE-preview: the decision is purely
 * `userId ∈ paidUserIds` — a row that is already paid is SKIPPED (ALREADY_PAID),
 * everything else is INCLUDED. No server preview call. Execute is idempotent.
 * See docs/proposals/bulk-actions/REDESIGN.md §5.2.
 */

defineOptions({name: "MarkPaidDialog"})

interface Props {
  modelValue: boolean
  userIds: number[]
  contributionPeriodId: number | null
  /** Names keyed by userId, for row display. */
  namesById: Record<number, string>
  /** The set of currently-paid user IDs (from usePaidToggle). */
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

/** Compute the preview rows locally from the paid set. */
function computeRows(): BulkPreviewRow[] {
  return props.userIds.map((userId) => {
    const alreadyPaid = props.paidUserIds.has(userId)
    return {
      userId,
      name: props.namesById[userId] ?? String(userId),
      disposition: alreadyPaid ? "SKIPPED" : "INCLUDED",
      reason: alreadyPaid ? "ALREADY_PAID" : undefined,
    }
  })
}

const canConfirm = computed(() => includedUserIds.value.length > 0 && !submitting.value)

async function onConfirm() {
  if (!canConfirm.value || props.contributionPeriodId == null) return
  const ok = await submit(async () => {
    const resp = await markPaid({
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
  // The host swaps in this component via `<component :is>` with modelValue already
  // true, so a non-immediate watch would never fire on the initial mount and the
  // preview rows would stay empty. `immediate` guarantees the open path runs.
  {immediate: true},
)
</script>

<template>
  <bulk-dialog-scaffold
    v-model="open"
    v-model:reinclude-overrides="reincludeOverrides"
    :can-confirm="canConfirm"
    confirm-label="Mark paid"
    :counts="counts"
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
