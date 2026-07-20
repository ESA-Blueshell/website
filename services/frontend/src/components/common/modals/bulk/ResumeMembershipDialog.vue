<script lang="ts" setup>
import {computed, watch} from "vue"
import BulkDialogScaffold from "./BulkDialogScaffold.vue"
import {useBulkPreview} from "@/composables/useBulkPreview"
import {useSubmitFeedback} from "@/composables/formUtils"
import {executeBulkResume} from "@/services/api/blueshell/sdk.gen"
import {computeResumeMembershipRows} from "@/utils/bulkCompute"
import type {ContributionPeriodResponse} from "@/services/api"
import type {BulkTarget} from "@/utils/bulkTarget"

/**
 * Resume membership per-action dialog. FE preview: computed from targets and
 * the latest contribution period. No server preview call. Execute resumes or
 * creates new memberships.
 * See docs/proposals/bulk-actions/REDESIGN.md §5.2.
 */

defineOptions({name: "ResumeMembershipDialog", inheritAttrs: false})

interface Props {
  modelValue: boolean
  targets: BulkTarget[]
  latestPeriod: ContributionPeriodResponse | null
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

// Compute rows reactively from targets and latestPeriod
const computedRows = computed(() => computeResumeMembershipRows(props.targets, props.latestPeriod))

const canConfirm = computed(() => includedUserIds.value.length > 0 && !submitting.value)

const help = {
  title: "Resume membership",
  body:
    "Reactivates membership for every included member against the latest contribution "
    + "period. Members whose most recent membership ended within the latest period are "
    + "resumed; others start a new membership. Members who are already active are skipped, "
    + "and nobody can be resumed when there is no contribution period. This does not send "
    + "any email.",
}

async function onConfirm() {
  if (!canConfirm.value) return
  const ok = await submit(async () => {
    const resp = await executeBulkResume({
      body: {
        // Only the operator-included users are resumed; the backend resume request
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
    confirm-label="Resume membership"
    :counts="counts"
    :help="help"
    icon="mdi-account-convert"
    :included-count="includedUserIds.length"
    :rows="rows"
    :show-submit-status="showSubmitStatus"
    :submit-state="submitState"
    :submitting="submitting"
    title="Resume membership"
    @cancel="emit('update:modelValue', false)"
    @confirm="onConfirm"
  />
</template>
