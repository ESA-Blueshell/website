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

          <!-- One pair per email this account can be sent: read it, then send it. -->
          <div
            v-for="email in mailableEmails"
            :key="email.purpose"
            class="d-flex align-center"
          >
            <v-btn
              :data-testid="`recovery-user-preview-btn-${email.purpose}-${user.id}`"
              class="btn-tight"
              icon="mdi-email-search-outline"
              size="small"
              :title="`Preview the ${email.noun}`"
              variant="text"
              @click.stop="showPreview(user.id, email.purpose)"
            />

            <v-btn
              :data-testid="`recovery-user-send-btn-${email.purpose}-${user.id}`"
              :disabled="sending !== null"
              :loading="sending === email.purpose"
              class="btn-tight"
              variant="text"
              @click.stop="send(email.purpose)"
            >
              {{ email.label }}
            </v-btn>
          </div>

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
      :error="previewError"
      :html="preview?.html"
      :link-placeholder="preview?.linkPlaceholder"
      :loading="previewLoading"
      :recipient-email="preview?.recipientEmail"
      :recipient-name="preview?.recipientName"
      :subject="preview?.subject"
      title="Email preview"
    />
  </div>
</template>

<script lang="ts" setup>
import {computed, ref} from "vue"
import {DateTime} from "luxon"
import type {UserDetailResponse} from "@/services/api"
import {resendRecoveryEmail, resetPassword, restoreDeletedUserById, TokenPurpose} from "@/services/api"
import {$handleNetworkError} from "@/plugins/handleNetworkError.ts"
import EmailPreviewDialog from "@/components/common/modals/EmailPreviewDialog.vue"
import {useRecoveryEmailPreview} from "@/composables/useRecoveryEmailPreview"

const props = defineProps<{
  user: UserDetailResponse
  actionType: "activation" | "password" | "restore"
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
} = useRecoveryEmailPreview()

/**
 * The recovery emails this row can send. An account created by the board activates
 * through a different email than one that signed itself up, and which of the two applies
 * is not something the list can tell, so both are offered rather than guessed at.
 */
const mailableEmails = computed(() => {
  if (props.actionType === "activation") {
    return [
      {purpose: TokenPurpose.USER_ACTIVATION, label: "Resend Activation", noun: "activation email"},
      {
        purpose: TokenPurpose.MEMBER_ACTIVATION,
        label: "Resend Member Activation",
        noun: "member activation email",
      },
    ]
  }
  if (props.actionType === "password") {
    return [
      {purpose: TokenPurpose.PASSWORD_RESET, label: "Send Password Reset", noun: "password reset email"},
    ]
  }
  return []
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

const send = async (purpose: TokenPurpose) => {
  if (sending.value !== null) return
  sending.value = purpose
  try {
    if (purpose === TokenPurpose.PASSWORD_RESET) {
      await resetPassword({path: {username: props.user.username}, throwOnError: true})
    } else {
      // Purpose-driven, so a board-created account stays reachable once its link expired.
      await resendRecoveryEmail({path: {userId: props.user.id}, query: {purpose}, throwOnError: true})
    }
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
