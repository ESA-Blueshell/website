<template>
  <div>
    <v-list-item>
      <div
        class="d-flex justify-space-between align-center"
        style="width: 100%;"
      >
        <div class="flex-grow-1">
          <v-list-item-title>{{ user.fullName }}</v-list-item-title>
          <v-list-item-subtitle>{{ user.username }}</v-list-item-subtitle>
        </div>

        <div
          class="d-flex align-center"
          style="flex-shrink: 0;"
        >
          <v-chip
            :color="user.enabled ? 'green' : 'red'"
            class="mr-3 d-flex justify-center align-center"
            size="small"
            style="width: 70px"
            variant="flat"
          >
            {{ user.enabled ? "Active" : "Inactive" }}
          </v-chip>

          <v-btn
            class="btn-tight"
            :loading="loading"
            :disabled="loading"
            variant="text"
            @click.stop="handleResend"
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
import type {AdvancedUser} from "@/services/api"
import {resendUserActivation, resetPassword} from "@/services/api"
import {$handleNetworkError} from "@/plugins/handleNetworkError.ts"

const props = defineProps<{
  user: AdvancedUser
  actionType: "activation" | "password"
}>()

const loading = ref(false)

const buttonLabel = computed(() =>
  props.actionType === "activation" ? "Resend Activation Email" : "Send Password Reset Email",
)

const handleResend = async () => {
  if (loading.value) return
  loading.value = true
  try {
    if (props.actionType === "activation") {
      await resendUserActivation({path: {username: props.user.username}, throwOnError: true})
    } else {
      await resetPassword({path: {username: props.user.username}, throwOnError: true})
    }
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
