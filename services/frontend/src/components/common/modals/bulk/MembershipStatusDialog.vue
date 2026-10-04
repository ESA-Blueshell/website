<script lang="ts" setup>
import {computed, ref, watch} from "vue"
import BulkDialogScaffold from "./BulkDialogScaffold.vue"
import NoticeBox from "@/components/island/NoticeBox.vue"
import {useBulkPreview} from "@/composables/useBulkPreview"
import {useSubmitFeedback} from "@/composables/formUtils"
import type {BulkActionResult, BulkMembershipPreview} from "@/domains/user"
import {endTheMemberships, readMembershipEnd, readMembershipStart, startTheMemberships} from "@/domains/user"
import {parseBulkRejection, type BulkRejection} from "@/utils/bulkRejection"
import {bulkRowsFromPreview} from "@/utils/bulkPreviewRows"
import type {BulkTarget} from "@/utils/bulkTarget"
import {formatBulkDate} from "@/utils/bulkDisposition"

/**
 * Ending or starting the memberships of a selection, driven by `targetState`.
 *
 * Unlike the contribution dialogs, the rows here are the api's decision rather than the browser's:
 * the invariants that say what may be ended or started live there, and so does the clock the
 * effective date is read from. The dialog asks what would happen, shows it, and then asks for it to
 * happen — the same selection both times, so the api applies the answer the operator confirmed.
 * Neither action is tied to a contribution period: members leave and return on their own schedule.
 */

defineOptions({name: "MembershipStatusDialog", inheritAttrs: false})

type MembershipAction = "end" | "start"

interface Props {
  modelValue: boolean
  targetState: MembershipAction
  targets: BulkTarget[]
  /** Drawn on the bulk task page rather than over the list. */
}

const props = defineProps<Props>()
const emit = defineEmits<{
  (e: "update:modelValue", value: boolean): void
  (e: "done"): void
  /** The api refused the selection because the table is out of date. */
  (e: "stale"): void
}>()

const {rows, counts, includedUserIds, reincludeOverrides, submitting, setRows, submit, reset} =
  useBulkPreview()
const {submitState, showSubmitStatus, setSubmitResult} = useSubmitFeedback()

interface DialogConfig {
  title: string
  confirmLabel: string
  icon: string
  /** How the effective date reads in the info box, e.g. "Memberships end on …". */
  dateSentence: string
  /** Past tense for the result line, e.g. "3 ended, 1 skipped". */
  appliedVerb: string
  previewApi: typeof readMembershipEnd | typeof readMembershipStart
  submitApi: typeof endTheMemberships | typeof startTheMemberships
  help: {title: string; body: string}
}

const configMap: Record<MembershipAction, DialogConfig> = {
  end: {
    title: "End membership",
    confirmLabel: "End membership",
    icon: "mdi-account-remove",
    dateSentence: "Memberships end on",
    appliedVerb: "ended",
    previewApi: readMembershipEnd,
    submitApi: endTheMemberships,
    help: {
      title: "End membership",
      body:
        "Ends the active membership of every included member, as of the date shown. Members "
        + "without an active membership are skipped, and so is anyone whose membership only "
        + "started today, which has no day to span yet. Ending a membership stops it going "
        + "forward: it deletes neither the member nor their history, and membership can be "
        + "started again later.",
    },
  },
  start: {
    title: "Start membership",
    confirmLabel: "Start membership",
    icon: "mdi-account-plus",
    dateSentence: "Memberships start on",
    appliedVerb: "started",
    previewApi: readMembershipStart,
    submitApi: startTheMemberships,
    help: {
      title: "Start membership",
      body:
        "Gives every included member a membership beginning on the date shown. Members who "
        + "are already active are skipped. Somebody who was a member before comes back on a "
        + "new membership rather than their old one reopened, so their history reads as two "
        + "stays — and \"member since\" still shows the day they first joined. Their member "
        + "type carries over from their last membership; incasso does not, and is set per "
        + "member afterwards.",
    },
  },
}

// targetState is typed as the two keys configMap covers, so this lookup cannot miss.
const config = computed(() => configMap[props.targetState])

/** The api's today, so the dialog never states a date the browser's clock invented. */
const effectiveDate = ref<string | null>(null)
const loading = ref(false)
const rejection = ref<BulkRejection | null>(null)
const result = ref<BulkActionResult | null>(null)
/**
 * The preview did not arrive. Said out loud rather than left as an empty table under
 * "working it out", which reads as a selection nothing applies to.
 */
const previewFailed = ref(false)

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
    // The generated client answers a failure with an error object rather than throwing, so
    // every path out of here is checked rather than assumed to have succeeded.
    const resp = await config.value.previewApi({body: {userIds}})
    const refused = parseBulkRejection(resp)
    if (refused) {
      rejection.value = refused
      setRows([])
      if (refused.requiresReload) emit("stale")
      return
    }
    const preview = resp.data as BulkMembershipPreview | undefined
    if (!preview) {
      previewFailed.value = true
      setRows([])
      return
    }
    effectiveDate.value = preview.effectiveDate
    setRows(bulkRowsFromPreview(props.targets, preview.rows))
  } catch {
    previewFailed.value = true
    setRows([])
  } finally {
    loading.value = false
  }
}

async function onConfirm() {
  if (submitting.value || includedUserIds.value.length === 0) return
  rejection.value = null
  previewFailed.value = false
  // The whole previewed selection is sent, not just the included rows: the api applies the
  // decision it previewed, so the counts that come back cover every member the operator
  // saw. There are no rows the operator can opt back in here, so the two sets agree.
  const userIds = rows.value.map((row) => row.userId)
  const ok = await submit(async () => {
    const resp = await config.value.submitApi({body: {userIds}})
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
      previewFailed.value = false
      await loadPreview()
    } else {
      rejection.value = null
      result.value = null
      previewFailed.value = false
      effectiveDate.value = null
      reset()
    }
  },
  {immediate: true},
)
</script>

<template>
  <bulk-dialog-scaffold
    v-model:reinclude-overrides="reincludeOverrides"
    :confirm-label="config.confirmLabel"
    :counts="counts"
    :included-count="includedUserIds.length"
    :rows="rows"
    :show-submit-status="showSubmitStatus"
    :submit-state="submitState"
    :submitting="submitting || loading"
    @cancel="emit('update:modelValue', false)"
    @confirm="onConfirm"
  >
    <template #info-box>
      <p data-testid="bulk-membership-effective-date">
        <template v-if="effectiveDate">
          {{ config.dateSentence }} {{ formatBulkDate(effectiveDate) }}, the server's date.
        </template>
        <template v-else-if="previewFailed">
          The list of what this would do could not be loaded.
        </template>
        <template v-else-if="!rejection">
          Working out what this would do…
        </template>
      </p>
      <notice-box
        v-if="previewFailed"
        testid="bulk-membership-preview-failed"
        tone="danger"
      >
        Nothing has been changed. Go back and try again.
      </notice-box>
      <notice-box
        v-if="rejection"
        testid="bulk-membership-rejection"
        title="Nothing was changed"
        tone="warning"
      >
        <p
          v-for="reason in rejection.reasons"
          :key="reason.code"
        >
          {{ reason.message }}
          <span v-if="reason.userIds.length">{{ namesFor(reason.userIds) }}</span>
        </p>
        <p v-if="rejection.requiresReload">
          The list has been reloaded. Check the selection and try again.
        </p>
      </notice-box>
      <notice-box
        v-if="result"
        testid="bulk-membership-result"
      >
        {{ result.applied }} {{ config.appliedVerb }}, {{ result.skipped }} skipped.
      </notice-box>
    </template>
  </bulk-dialog-scaffold>
</template>
