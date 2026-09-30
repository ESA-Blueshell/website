<script lang="ts" setup>
/* The one thing Account recovery does for an account: open the activation or password-reset email
   it would send, with its send button on it, or restore a deleted account inside its window. */
import {computed, ref} from "vue"
import EmailPreviewDialog from "@/components/common/modals/EmailPreviewDialog.vue"
import {useEmailPreview} from "@/composables/useEmailPreview"
import {previewRecoveryMail, requestPasswordReset, resendRecoveryMail, restoreDeletedUser, TokenPurpose} from "@/domains/recovery"
import type {UserDetailResponse} from "@/domains/user"
import {$handleNetworkError} from "@/plugins/handleNetworkError"

defineOptions({name: "RecoveryAction"})

const props = defineProps<{
  user: UserDetailResponse
  action: "activation" | "password" | "restore"
  /** Which activation this account takes; an account taking none is offered none. */
  pendingActivation?: TokenPurpose | null
}>()

const emit = defineEmits<{done: []}>()

const WORDS: Partial<Record<TokenPurpose, string>> = {
  [TokenPurpose.USER_ACTIVATION]: "Send activation",
  [TokenPurpose.MEMBER_ACTIVATION]: "Send member activation",
  [TokenPurpose.PASSWORD_RESET]: "Send password reset",
}

const purpose = computed(() =>
  props.action === "activation" ? props.pendingActivation ?? null : props.action === "password" ? TokenPurpose.PASSWORD_RESET : null)

const busy = ref(false)
const {open, loading, error, preview, show} = useEmailPreview()

/** Past its window a deleted account cannot come back, and the api says so too. */
const windowPassed = computed(() => props.user.restoreUntilAt != null && new Date(props.user.restoreUntilAt).getTime() < Date.now())

const openEmail = () => show(async () => (purpose.value ? previewRecoveryMail(props.user.id, purpose.value) : null))

const send = async () => {
  const chosen = purpose.value
  if (!chosen || busy.value) return
  busy.value = true
  try {
    if (chosen === TokenPurpose.PASSWORD_RESET) await requestPasswordReset(props.user.username)
    else await resendRecoveryMail(props.user.id, chosen)
    open.value = false
    emit("done")
  } catch (failure) {
    $handleNetworkError(failure)
  } finally {
    busy.value = false
  }
}

const restore = async () => {
  if (busy.value) return
  busy.value = true
  try {
    await restoreDeletedUser(props.user.id)
    emit("done")
  } catch (failure) {
    $handleNetworkError(failure)
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <span class="recovery-action">
    <button
      v-if="purpose"
      class="recovery-action__button"
      :data-testid="`recovery-user-send-btn-${purpose}-${user.id}`"
      type="button"
      @click="openEmail"
    >
      {{ WORDS[purpose] ?? "Send" }}
    </button>
    <button
      v-else-if="action === 'restore'"
      class="recovery-action__button"
      :data-testid="`recovery-user-action-btn-restore-${user.id}`"
      :disabled="busy || windowPassed"
      type="button"
      @click="restore"
    >
      {{ windowPassed ? "Window passed" : "Restore" }}
    </button>

    <email-preview-dialog
      v-model="open"
      :confirm-label="purpose ? WORDS[purpose] ?? 'Send' : 'Send'"
      :confirm-loading="busy"
      :error="error"
      :loading="loading"
      :preview="preview"
      :title="purpose ? WORDS[purpose] ?? 'Email' : 'Email'"
      @confirm="send"
    />
  </span>
</template>

<style scoped>
.recovery-action__button {
  padding: 0.35rem 0.8rem;
  border: 1px solid var(--color-hairline);
  background: none;
  font: inherit;
  font-size: 0.84rem;
  color: var(--color-chalk);
  white-space: nowrap;
  cursor: pointer;
}

.recovery-action__button:disabled {
  color: var(--color-ash);
  cursor: default;
}
</style>
