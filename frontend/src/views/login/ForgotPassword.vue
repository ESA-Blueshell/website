<!-- ForgotPassword.vue -->
<template>
  <v-main>
    <top-banner title="Forgot Password" />

    <div class="mx-3">
      <div
        class="mx-auto mt-10"
        style="max-width: 500px"
      >
        <div v-if="!succeeded">
          <p>
            Enter your username, and we'll send you an email with a link to reset your password.
          </p>

          <v-form
            ref="form"
            v-model="valid"
            @submit.prevent
          >
            <v-text-field
              ref="usernameInput"
              v-model="username"
              :rules="[(v: string) => !!v || 'Username is required']"
              label="Username"
              @keydown.enter="sendResetMail"
            />

            <v-row>
              <v-spacer />
              <v-col cols="auto">
                <v-btn
                  :disabled="!valid || !username"
                  :loading="loading"
                  @click="sendResetMail"
                >
                  Send reset mail
                </v-btn>
              </v-col>
            </v-row>
          </v-form>
        </div>

        <div v-else>
          <p>
            All right, you should get a mail with a link you can use to reset your password at the email address
            associated to your username. If you don't receive anything, please report it in the
            <a
              class="text-decoration-none"
              href="https://discord.com/channels/324285132133629963/1020245710987350047"
              target="_blank"
            >Sitecie suggestions channel on discord</a> and we'll help you out.
          </p>
        </div>
      </div>
    </div>
  </v-main>
</template>

<script setup lang="ts">
import {onMounted, ref} from "vue"
import {useRoute} from "vue-router"
import {useStore} from "vuex"
import TopBanner from "@/components/banners/TopBanner.vue"

import {$handleNetworkError} from "@/plugins/handleNetworkError.ts"
import {resetPassword} from "@/lib"

type VFormValidateResult = { valid: boolean }
type VFormRef = {
  validate: () => Promise<VFormValidateResult> | VFormValidateResult
  resetValidation?: () => void
}

const route = useRoute()
const store = useStore()

const username = ref<string>("")
const valid = ref<boolean>(false)
const succeeded = ref<boolean>(false)
const loading = ref<boolean>(false)

const form = ref<VFormRef | null>(null)
const usernameInput = ref<HTMLInputElement | null>(null)

onMounted(() => {
  const q = route.query.username
  if (typeof q === "string") {
    username.value = q
  }
})

const sendResetMail = async () => {
  const result = await form.value?.validate()
  if (!result?.valid) return

  loading.value = true
  try {
    await resetPassword({query: {username: username.value}, throwOnError: true})

    succeeded.value = true
  } catch (e: unknown) {
    // The generated client surfaces HTTP status on e.response?.status (Axios-style).
    const anyErr = e as { response?: { status?: number } }
    if (anyErr?.response?.status === 404) {
      store.commit(
        "setStatusSnackbarMessage",
        "Uhhh, we don't know that username... Maybe check the spelling?",
      )
    } else {
      $handleNetworkError(e)
    }
  } finally {
    loading.value = false
  }
}
</script>
