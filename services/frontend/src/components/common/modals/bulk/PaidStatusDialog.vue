<script lang="ts" setup>
import {computed, watch} from "vue"
import BulkDialogScaffold from "./BulkDialogScaffold.vue"
import {useBulkPreview} from "@/composables/useBulkPreview"
import {useSubmitFeedback} from "@/composables/formUtils"
import {markPaid, markUnpaid} from "@/services/api/blueshell/sdk.gen"
import {computeMarkPaidRows, computeMarkUnpaidRows} from "@/utils/bulkCompute"
import type {BulkTarget} from "@/utils/bulkTarget"

/**
 * Paid-status per-action dialog. Handles both "Mark as paid" and "Mark as unpaid"
 * actions via the targetState prop. FE-preview: membership disposition and inclusion
 * logic is independent per action. No server preview call. Execute is idempotent.
 */

defineOptions({name: "PaidStatusDialog", inheritAttrs: false})

interface Props {
  modelValue: boolean
  targetState: "paid" | "unpaid"
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

// Config for paid/unpaid state-specific behavior.
interface DialogConfig {
  title: string
  confirmLabel: string
  icon: string
  computeRows: (targets: BulkTarget[]) => ReturnType<typeof computeMarkPaidRows>
  submitApi: typeof markPaid | typeof markUnpaid
  help: {title: string; body: string}
}

const configMap: Record<"paid" | "unpaid", DialogConfig> = {
  paid: {
    title: "Mark as paid",
    confirmLabel: "Mark paid",
    icon: "mdi-cash-check",
    computeRows: (targets: BulkTarget[]) => computeMarkPaidRows(targets),
    submitApi: markPaid,
    help: {
      title: "Mark as paid",
      body:
        "Records a paid contribution for every included member for the selected contribution "
        + "period. Members who are already paid are skipped, and honorary members (who owe no "
        + "contribution) are skipped too. This only updates the paid status; it does not send "
        + "any email. The action is idempotent, so re-running it is safe.",
    },
  },
  unpaid: {
    title: "Mark as unpaid",
    confirmLabel: "Mark unpaid",
    icon: "mdi-cash-remove",
    computeRows: (targets: BulkTarget[]) => computeMarkUnpaidRows(targets),
    submitApi: markUnpaid,
    help: {
      title: "Mark as unpaid",
      body:
        "Clears the paid contribution for every included member for the selected contribution "
        + "period. Members who are not currently paid are skipped, and honorary members are "
        + "skipped too. This only updates the paid status; it does not send any email or issue a "
        + "refund. The action is idempotent, so re-running it is safe.",
    },
  },
}

// props.targetState is typed as "paid" | "unpaid" and configMap covers both keys,
// so this lookup can never return undefined — no runtime guard needed.
const config = computed(() => configMap[props.targetState])

const canConfirm = computed(() => includedUserIds.value.length > 0 && !submitting.value)

// Compute rows reactively from targets.
const computedRows = computed(() => config.value.computeRows(props.targets))

async function onConfirm() {
  if (!canConfirm.value || props.contributionPeriodId == null) return
  const ok = await submit(async () => {
    const resp = await config.value.submitApi({
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

// Update rows when dialog state changes.
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

// Also update when targets change.
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
    :confirm-label="config.confirmLabel"
    :counts="counts"
    :help="config.help"
    :icon="config.icon"
    :included-count="includedUserIds.length"
    :rows="rows"
    :show-submit-status="showSubmitStatus"
    :submit-state="submitState"
    :submitting="submitting"
    :title="config.title"
    @cancel="emit('update:modelValue', false)"
    @confirm="onConfirm"
  />
</template>
