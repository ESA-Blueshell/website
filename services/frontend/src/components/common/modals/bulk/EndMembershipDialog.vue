<script lang="ts" setup>
import {computed, watch} from "vue"
import BulkDialogScaffold from "./BulkDialogScaffold.vue"
import {useBulkPreview} from "@/composables/useBulkPreview"
import {useSubmitFeedback} from "@/composables/formUtils"
import {executeBulkEnd} from "@/services/api/blueshell/sdk.gen"
import {computeEndMembershipRows} from "@/utils/bulkCompute"
import type {BulkTarget} from "@/utils/bulkTarget"

/**
 * End membership per-action dialog. FE preview: purely computed from targets and today.
 * No server preview call. Execute ends active memberships.
 * See docs/proposals/bulk-actions/REDESIGN.md §5.2.
 */

defineOptions({name: "EndMembershipDialog", inheritAttrs: false})

interface Props {
  modelValue: boolean
  targets: BulkTarget[]
  serverToday: string
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

// Compute rows reactively from targets and today
const computedRows = computed(() => computeEndMembershipRows(props.targets, props.serverToday))

const canConfirm = computed(() => includedUserIds.value.length > 0 && !submitting.value)

async function onConfirm() {
  if (!canConfirm.value) return
  const ok = await submit(async () => {
    const resp = await executeBulkEnd({
      body: {
        // Only the operator-included users are ended; the backend end request
        // takes the final userIds directly (no separate include set).
        userIds: includedUserIds.value,
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

// Update rows when targets or dialog state changes
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

watch(computedRows, (newRows) => {
  if (open.value) {
    setRows(newRows)
  }
})
</script>

<template>
  <bulk-dialog-scaffold
    v-model="open"
    v-model:reinclude-overrides="reincludeOverrides"
    :can-confirm="canConfirm"
    confirm-label="End membership"
    :counts="counts"
    icon="mdi-account-remove"
    :included-count="includedUserIds.length"
    :rows="rows"
    :show-submit-status="showSubmitStatus"
    :submit-state="submitState"
    :submitting="submitting"
    title="End membership"
    @cancel="emit('update:modelValue', false)"
    @confirm="onConfirm"
  />
</template>
