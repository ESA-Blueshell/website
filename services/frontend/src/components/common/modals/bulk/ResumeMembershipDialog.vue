<script lang="ts" setup>
import {computed, watch} from "vue"
import BulkDialogScaffold from "./BulkDialogScaffold.vue"
import {useBulkPreview} from "@/composables/useBulkPreview"
import {useSubmitFeedback} from "@/composables/formUtils"
import {previewBulkResume, executeBulkResume} from "@/services/api/blueshell/sdk.gen"

/**
 * Resume / start-membership per-action dialog. SERVER preview: the classification depends
 * on the globally most-recent contribution period (periods.findLatest) and per-user
 * membership history that the frontend does not load, so it cannot be computed locally.
 * Rows are read-only (no re-include / fee columns). Execute re-classifies against the
 * live DB. See docs/proposals/bulk-actions/REDESIGN.md §5.2.
 */

defineOptions({name: "ResumeMembershipDialog"})

interface Props {
  modelValue: boolean
  userIds: number[]
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

const {rows, counts, includedUserIds, reincludeOverrides, loading, error, submitting, loadPreview, submit, reset} =
  useBulkPreview()
const {submitState, showSubmitStatus, setSubmitResult} = useSubmitFeedback()

async function load() {
  await loadPreview(async () => {
    const resp = await previewBulkResume({body: {userIds: props.userIds}})
    return {rows: resp.data?.rows ?? []}
  })
}

const canConfirm = computed(() => !loading.value && !error.value && includedUserIds.value.length > 0 && !submitting.value)

async function onConfirm() {
  if (!canConfirm.value) return
  const ok = await submit(async () => {
    const resp = await executeBulkResume({body: {userIds: props.userIds}})
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
  async (isOpen) => {
    if (isOpen) await load()
    else reset()
  },
)
</script>

<template>
  <bulk-dialog-scaffold
    v-model="open"
    v-model:reinclude-overrides="reincludeOverrides"
    :can-confirm="canConfirm"
    confirm-label="Resume / start"
    :counts="counts"
    :error="error"
    icon="mdi-account-reactivate"
    :included-count="includedUserIds.length"
    :loading="loading"
    :rows="rows"
    :show-submit-status="showSubmitStatus"
    :submit-state="submitState"
    :submitting="submitting"
    title="Resume / start membership"
    @cancel="emit('update:modelValue', false)"
    @confirm="onConfirm"
  />
</template>
