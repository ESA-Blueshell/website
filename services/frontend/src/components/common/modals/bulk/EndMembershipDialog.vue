<script lang="ts" setup>
import {computed, watch} from "vue"
import BulkDialogScaffold from "./BulkDialogScaffold.vue"
import {useBulkPreview} from "@/composables/useBulkPreview"
import {useSubmitFeedback} from "@/composables/formUtils"
import {previewBulkEnd, executeBulkEnd} from "@/services/api/blueshell/sdk.gen"
import {computeEndMembershipRows} from "@/utils/bulkCompute"
import type {MembershipResponse} from "@/services/api"

/**
 * End-membership per-action dialog. Calls the end-preview endpoint ONCE only to obtain
 * the server's `today`, then computes the rows locally from the memberships the page
 * already holds — using serverToday (not new Date()) so the "started today" boundary is
 * evaluated in the server's timezone. See docs/proposals/bulk-actions/REDESIGN.md §4.
 */

defineOptions({name: "EndMembershipDialog"})

interface Props {
  modelValue: boolean
  userIds: number[]
  namesById: Record<number, string>
  membershipsByUserId: Map<number, MembershipResponse[]>
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
    const resp = await previewBulkEnd({body: {userIds: props.userIds}})
    // Prefer the server date; fall back defensively to the browser date only if the
    // server omitted it (should not happen given the extended envelope).
    const serverToday = resp.data?.serverToday ?? new Date().toISOString().slice(0, 10)
    return {
      rows: computeEndMembershipRows(props.userIds, props.membershipsByUserId, props.namesById, serverToday),
      serverToday,
    }
  })
}

const canConfirm = computed(() => !loading.value && !error.value && includedUserIds.value.length > 0 && !submitting.value)

async function onConfirm() {
  if (!canConfirm.value) return
  const ok = await submit(async () => {
    const resp = await executeBulkEnd({body: {userIds: props.userIds}})
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
  // The host swaps in this component via `<component :is>` with modelValue already
  // true, so a non-immediate watch would never fire on the initial mount and the
  // server preview (serverToday + rows) would never load. `immediate` fixes that.
  {immediate: true},
)
</script>

<template>
  <bulk-dialog-scaffold
    v-model="open"
    v-model:reinclude-overrides="reincludeOverrides"
    :can-confirm="canConfirm"
    confirm-label="End membership"
    :counts="counts"
    :error="error"
    icon="mdi-account-cancel"
    :included-count="includedUserIds.length"
    :loading="loading"
    :rows="rows"
    :show-submit-status="showSubmitStatus"
    :submit-state="submitState"
    :submitting="submitting"
    title="End membership"
    @cancel="emit('update:modelValue', false)"
    @confirm="onConfirm"
  />
</template>
