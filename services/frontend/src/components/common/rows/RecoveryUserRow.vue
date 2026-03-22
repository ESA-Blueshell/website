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
          class="d-flex align-center gap-2"
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

          <v-btn
            :disabled="loading"
            :data-testid="`recovery-user-action-btn-${actionType}-${user.id}`"
            :loading="loading"
            class="btn-tight"
            variant="text"
            @click.stop="handleAction"
          >
            {{ buttonLabel }}
          </v-btn>
        </div>
      </div>
    </v-list-item>
  </div>
</template>

<script lang="ts" setup>
import {computed, ref} from "vue"
import {DateTime} from "luxon"
import type {UserDetailResponse} from "@/services/api"
import {resendUserActivation, resetPassword, restoreDeletedUserById} from "@/services/api"
import {$handleNetworkError} from "@/plugins/handleNetworkError.ts"

const props = defineProps<{
  user: UserDetailResponse
  actionType: "activation" | "password" | "restore"
}>()

const emit = defineEmits<{
  (e: "action:done"): void
}>()

const loading = ref(false)

const buttonLabel = computed(() => {
  if (props.actionType === "activation") return "Resend Activation Email"
  if (props.actionType === "password") return "Send Password Reset Email"
  return "Restore User"
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

const handleAction = async () => {
  if (loading.value) return
  loading.value = true
  try {
    if (props.actionType === "activation") {
      await resendUserActivation({path: {username: props.user.username}, throwOnError: true})
    } else if (props.actionType === "password") {
      await resetPassword({path: {username: props.user.username}, throwOnError: true})
    } else {
      await restoreDeletedUserById({path: {userId: props.user.id}, throwOnError: true})
    }
    emit("action:done")
  } catch (e: unknown) {
    $handleNetworkError(e)
  } finally {
    loading.value = false
  }
}
</script>

<style lang="scss">
.btn-tight {
  padding-inline: 6px !important;
  min-width: auto !important;
}
</style>
