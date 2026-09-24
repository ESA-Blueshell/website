<template>
  <div data-testid="two-factor-set-up">
    <v-form
      v-if="stage === 'password'"
      @submit.prevent="start"
    >
      <p class="mb-3">
        Enter your password to start. You need an authenticator app on your phone, such as Aegis, Google
        Authenticator or 1Password.
      </p>
      <v-text-field
        v-model="password"
        :input-props="{ 'data-testid': 'two-factor-password-input' }"
        autocomplete="current-password"
        label="Password"
        type="password"
      />
      <v-btn
        :disabled="!password"
        :loading="busy"
        color="primary"
        data-testid="two-factor-start-btn"
        type="submit"
      >
        Continue
      </v-btn>
    </v-form>

    <v-form
      v-else-if="stage === 'scan'"
      @submit.prevent="confirm"
    >
      <p class="mb-3">
        Scan this code with your authenticator app, then enter the six-digit code it shows.
      </p>
      <img
        v-if="qr"
        :src="qr"
        alt="QR code to add ESA Blueshell to an authenticator app"
        class="mb-2"
        data-testid="two-factor-qr"
        height="200"
        width="200"
      >
      <p class="text-body-2 mb-4">
        Or enter this key by hand:
        <code data-testid="two-factor-key">{{ pending?.key }}</code>
      </p>
      <v-text-field
        v-model="code"
        :input-props="{ 'data-testid': 'two-factor-code-input' }"
        autocomplete="one-time-code"
        inputmode="numeric"
        label="Code"
      />
      <v-btn
        :disabled="!code.trim()"
        :loading="busy"
        color="primary"
        data-testid="two-factor-confirm-btn"
        type="submit"
      >
        Verify
      </v-btn>
    </v-form>

    <div v-else-if="stage === 'codes'">
      <backup-codes :codes="codesToSave" />
      <v-checkbox
        v-model="saved"
        class="mt-2"
        data-testid="two-factor-saved-check"
        label="I have saved these codes"
      />
      <v-btn
        :disabled="!saved"
        :loading="busy"
        color="primary"
        data-testid="two-factor-finish-btn"
        @click="finish"
      >
        Turn on two-factor
      </v-btn>
    </div>

    <v-alert
      v-if="error"
      class="mt-3"
      data-testid="two-factor-error"
      type="error"
      variant="tonal"
    >
      {{ error }}
    </v-alert>
  </div>
</template>

<script lang="ts" setup>
import {ref} from "vue"
import QRCode from "qrcode"
import BackupCodes from "./BackupCodes.vue"
import {confirmTwoFactorCode, finishTwoFactorSetUp, startTwoFactorSetUp} from "../adapters/accountSecurity"
import type {TwoFactorSetupResponse} from "@/services/api"

const emit = defineEmits<{ done: []; stepUp: [retry: () => void] }>()

const stage = ref<"password" | "scan" | "codes">("password")
const password = ref("")
const code = ref("")
const pending = ref<TwoFactorSetupResponse>()
const qr = ref<string>()
const codesToSave = ref<string[]>([])
const saved = ref(false)
const busy = ref(false)
const error = ref<string | null>(null)

const start = async () => {
  busy.value = true
  error.value = null
  const result = await startTwoFactorSetUp(password.value)
  busy.value = false
  if (!result.ok) {
    if (result.needsStepUp) emit("stepUp", start)
    else error.value = result.reason
    return
  }
  pending.value = result.value
  qr.value = await QRCode.toDataURL(result.value.otpauthUri, {margin: 1, width: 200})
  password.value = ""
  stage.value = "scan"
}

const confirm = async () => {
  busy.value = true
  error.value = null
  const result = await confirmTwoFactorCode(code.value.trim())
  busy.value = false
  if (!result.ok) {
    error.value = result.reason
    return
  }
  codesToSave.value = result.value
  stage.value = "codes"
}

const finish = async () => {
  busy.value = true
  const result = await finishTwoFactorSetUp()
  busy.value = false
  if (result.ok) emit("done")
  else error.value = result.reason
}
</script>
