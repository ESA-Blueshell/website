<script lang="ts" setup>
import {computed, ref, watch} from "vue"
import BulkDialogScaffold from "./BulkDialogScaffold.vue"
import {useBulkPreview} from "@/composables/useBulkPreview"
import {useSubmitFeedback} from "@/composables/formUtils"
import {endMemberships, previewBulkEnd} from "@/services/api/blueshell/sdk.gen"
import type {BulkActionResult, BulkMembershipPreview} from "@/services/api"
import {parseBulkRejection, type BulkRejection} from "@/utils/bulkRejection"
import {bulkRowsFromPreview} from "@/utils/bulkPreviewRows"
import type {BulkTarget} from "@/utils/bulkTarget"
import {formatBulkDate} from "@/utils/bulkDisposition"

/**
 * Ending the memberships of a selection.
 *
 * Unlike the contribution dialogs, the rows here are the api's decision rather than the
 * browser's: the invariants that say what may be ended live there, and so does the clock
 * the effective date is read from. The dialog asks what would happen, shows it, and then
 * asks for it to happen — the same selection both times, so the api applies the answer the
 * operator confirmed.
 *
 * The action is not tied to a contribution period: members leave on their own schedule.
 */

defineOptions({name: "EndMembershipDialog", inheritAttrs: false})

interface Props {
  modelValue: boolean
  targets: BulkTarget[]
}

const props = defineProps<Props>()
const emit = defineEmits<{
  (e: "update:modelValue", value: boolean): void
  (e: "done"): void
  /** The api refused the selection because the table is out of date. */
  (e: "stale"): void
}>()

const open = computed({
  get: () => props.modelValue,
  set: (v) => emit("update:modelValue", v),
})

const {rows, counts, includedUserIds, reincludeOverrides, submitting, setRows, submit, reset} =
  useBulkPreview()
const {submitState, showSubmitStatus, setSubmitResult} = useSubmitFeedback()

const help = {
  title: "End membership",
  body:
    "Ends the active membership of every included member, as of the date shown. Members "
    + "without an active membership are skipped, and so is anyone whose membership only "
    + "started today, which has no day to span yet. Ending a membership stops it going "
    + "forward: it deletes neither the member nor their history, and membership can be "
    + "started again later.",
}

/** The api's today, so the dialog never states a date the browser's clock invented. */
const effectiveDate = ref<string | null>(null)
const loading = ref(false)
const rejection = ref<BulkRejection | null>(null)
const result = ref<BulkActionResult | null>(null)

/** Names the refused rows where the table still knows them, so ids are a fallback. */
function namesFor(userIds: number[]): string {
  return userIds
    .map((id) => props.targets.find((target) => target.userId === id)?.name ?? `#${id}`)
    .join(", ")
}

async function loadPreview() {
  const userIds = props.targets.map((target) => target.userId)
  if (userIds.length === 0) {
    setRows([])
    return
  }
  loading.value = true
  try {
    const resp = await previewBulkEnd({body: {userIds}})
    const refused = parseBulkRejection(resp)
    if (refused) {
      rejection.value = refused
      setRows([])
      if (refused.requiresReload) emit("stale")
      return
    }
    const preview = resp.data as BulkMembershipPreview | undefined
    if (!preview) {
      setRows([])
      return
    }
    effectiveDate.value = preview.effectiveDate
    setRows(bulkRowsFromPreview(props.targets, preview.rows))
  } finally {
    loading.value = false
  }
}

async function onConfirm() {
  if (submitting.value || includedUserIds.value.length === 0) return
  rejection.value = null
  // The whole previewed selection is sent, not just the included rows: the api applies the
  // decision it previewed, so the counts that come back cover every member the operator
  // saw. There are no rows the operator can opt back in here, so the two sets agree.
  const userIds = rows.value.map((row) => row.userId)
  const ok = await submit(async () => {
    const resp = await endMemberships({body: {userIds}})
    // A refused selection wrote nothing, so the dialog stays open with the reasons rather
    // than reporting a failure the operator cannot act on.
    const refused = parseBulkRejection(resp)
    if (refused) {
      rejection.value = refused
      if (refused.requiresReload) emit("stale")
      return false
    }
    result.value = resp.data ?? null
    return resp.data != null
  })
  setSubmitResult(ok)
  if (ok) {
    setTimeout(() => {
      emit("update:modelValue", false)
      emit("done")
    }, 1800)
  }
}

watch(
  () => props.modelValue,
  async (isOpen) => {
    if (isOpen) {
      rejection.value = null
      result.value = null
      await loadPreview()
    } else {
      rejection.value = null
      result.value = null
      effectiveDate.value = null
      reset()
    }
  },
  {immediate: true},
)
</script>

<template>
  <bulk-dialog-scaffold
    v-model="open"
    v-model:reinclude-overrides="reincludeOverrides"
    confirm-label="End membership"
    :counts="counts"
    :help="help"
    icon="mdi-account-remove"
    :included-count="includedUserIds.length"
    info-box-label="Effective date"
    :rows="rows"
    :show-submit-status="showSubmitStatus"
    :submit-state="submitState"
    :submitting="submitting || loading"
    title="End membership"
    @cancel="emit('update:modelValue', false)"
    @confirm="onConfirm"
  >
    <template #info-box>
      <div
        class="text-body-2"
        data-testid="bulk-membership-effective-date"
      >
        <template v-if="effectiveDate">
          Memberships end on {{ formatBulkDate(effectiveDate) }}, the server's date.
        </template>
        <template v-else>
          Working out what this would do…
        </template>
      </div>

      <v-alert
        v-if="rejection"
        class="mt-2 mb-0"
        data-testid="bulk-membership-rejection"
        density="compact"
        type="warning"
        variant="tonal"
      >
        <div class="font-weight-medium mb-1">
          Nothing was changed.
        </div>
        <div
          v-for="reason in rejection.reasons"
          :key="reason.code"
          class="text-body-2"
        >
          {{ reason.message }}
          <span v-if="reason.userIds.length"> {{ namesFor(reason.userIds) }}</span>
        </div>
        <div
          v-if="rejection.requiresReload"
          class="text-body-2 mt-1"
        >
          The list has been reloaded; check the selection and try again.
        </div>
      </v-alert>

      <v-alert
        v-if="result"
        class="mt-2 mb-0"
        data-testid="bulk-membership-result"
        density="compact"
        type="success"
        variant="tonal"
      >
        {{ result.applied }} ended, {{ result.skipped }} skipped.
      </v-alert>
    </template>
  </bulk-dialog-scaffold>
</template>
