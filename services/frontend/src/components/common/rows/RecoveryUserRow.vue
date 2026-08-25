<template>
  <div>
    <v-list-item :data-testid="`recovery-user-row-${user.id}`">
      <div
        class="d-flex justify-space-between align-center"
        :data-testid="`recovery-user-toggle-${user.id}`"
        style="width: 100%;"
      >
        <div class="flex-grow-1">
          <v-list-item-title>{{ user.fullName }}</v-list-item-title>
          <v-list-item-subtitle>{{ user.username }}</v-list-item-subtitle>
        </div>

        <div
          class="d-flex align-center flex-wrap justify-end gap-2"
          style="flex-shrink: 0;"
        >
          <v-chip
            v-if="actionType === 'restore' && restoreWindowLabel"
            :color="restoreWindowUrgent ? 'warning' : undefined"
            class="mx-2"
            size="small"
            variant="tonal"
          >
            {{ restoreWindowLabel }}
          </v-chip>

          <!-- Reading the email is how it is sent: the dialog carries the send button. -->
          <v-btn
            v-if="mailableEmail"
            :data-testid="`recovery-user-send-btn-${mailableEmail.purpose}-${user.id}`"
            class="btn-tight"
            variant="text"
            @click.stop="openEmail"
          >
            {{ mailableEmail.label }}
          </v-btn>

          <v-btn
            v-if="actionType === 'restore'"
            :disabled="restoring"
            :data-testid="`recovery-user-action-btn-restore-${user.id}`"
            :loading="restoring"
            class="btn-tight"
            variant="text"
            @click.stop="restore"
          >
            Restore User
          </v-btn>
        </div>
      </div>
    </v-list-item>

    <email-preview-dialog
      v-model="previewOpen"
      :confirm-label="mailableEmail?.label ?? null"
      :confirm-loading="sending !== null"
      :error="previewError"
      :loading="previewLoading"
      :preview="preview"
      :title="mailableEmail?.label ?? 'Email preview'"
      @confirm="sendPreviewed"
    />
  </div>
</template>

<script lang="ts" setup>
import {computed, ref} from "vue"
import {DateTime} from "luxon"
import type {UserDetailResponse} from "@/services/api"
import {previewRecoveryEmail, resendRecoveryEmail, resetPassword, restoreDeletedUserById, TokenPurpose} from "@/services/api"
import {$handleNetworkError} from "@/plugins/handleNetworkError.ts"
import EmailPreviewDialog from "@/components/common/modals/EmailPreviewDialog.vue"
import {useEmailPreview} from "@/composables/useEmailPreview"

const props = defineProps<{
  user: UserDetailResponse
  actionType: "activation" | "password" | "restore"
  /** Which activation this account takes; the row offers that one and no other. */
  pendingActivation?: TokenPurpose | null
}>()

const emit = defineEmits<{
  (e: "action:done"): void
}>()

const sending = ref<TokenPurpose | null>(null)
const restoring = ref(false)

const {
  open: previewOpen,
  loading: previewLoading,
  error: previewError,
  preview,
  show: showPreview,
} = useEmailPreview()

/**
 * Read before sending: the button opens the email itself, and the dialog carries the send.
 * The row supplies the fetch; the composable owns only the state around it.
 */
const openEmail = () =>
  showPreview(async () => {
    const purpose = mailableEmail.value?.purpose
    if (!purpose) return null
    const {data} = await previewRecoveryEmail({path: {userId: props.user.id}, query: {purpose}})
    return data ?? null
  })

const EMAIL_LABELS: Record<string, string> = {
  [TokenPurpose.USER_ACTIVATION]: "Resend Activation",
  [TokenPurpose.MEMBER_ACTIVATION]: "Resend Member Activation",
  [TokenPurpose.PASSWORD_RESET]: "Send Password Reset",
}

/**
 * The one recovery email this row sends, or none.
 *
 * An account created by the board activates through a different email than one that signed
 * itself up, and the server says which. Naming no activation means none applies — a
 * deleted account, or one already active — so the row offers nothing rather than guessing
 * at the ordinary one. Guessing put an activation button on accounts that had no business
 * with it.
 */
const mailableEmail = computed(() => {
  const purpose = props.actionType === "activation"
    ? props.pendingActivation
    : props.actionType === "password"
      ? TokenPurpose.PASSWORD_RESET
      : null
  return purpose == null ? null : {purpose, label: EMAIL_LABELS[purpose] ?? "Send"}
})

const restoreWindowUrgent = computed(() => {
  if (!props.user.restoreUntilAt) return false
  return DateTime.fromISO(props.user.restoreUntilAt).diff(DateTime.now(), "days").days < 7
})

const restoreWindowLabel = computed(() => {
  if (!props.user.restoreUntilAt) return null
  const daysLeft = Math.ceil(DateTime.fromISO(props.user.restoreUntilAt).diff(DateTime.now(), "days").days)
  return `${daysLeft} day${daysLeft === 1 ? "" : "s"} left`
})

const sendPreviewed = async () => {
  const purpose = mailableEmail.value?.purpose
  if (!purpose || sending.value !== null) return
  sending.value = purpose
  try {
    if (purpose === TokenPurpose.PASSWORD_RESET) {
      await resetPassword({path: {username: props.user.username}, throwOnError: true})
    } else {
      // Purpose-driven, so a board-created account stays reachable once its link expired.
      await resendRecoveryEmail({path: {userId: props.user.id}, query: {purpose}, throwOnError: true})
    }
    previewOpen.value = false
    emit("action:done")
  } catch (e: unknown) {
    $handleNetworkError(e)
  } finally {
    sending.value = null
  }
}

const restore = async () => {
  if (restoring.value) return
  restoring.value = true
  try {
    await restoreDeletedUserById({path: {userId: props.user.id}, throwOnError: true})
    emit("action:done")
  } catch (e: unknown) {
    $handleNetworkError(e)
  } finally {
    restoring.value = false
  }
}
</script>

<style lang="scss">
.btn-tight {
  padding-inline: 6px !important;
  min-width: auto !important;
}
</style>
