<template>
  <div
    class="set-up"
    data-testid="two-factor-set-up"
  >
    <ol class="set-up__steps">
      <li
        v-for="(one, index) in order"
        :key="one"
        :aria-current="one === stage ? 'step' : undefined"
        class="set-up__step"
        :class="{'set-up__step--on': one === stage, 'set-up__step--done': index < at}"
      >
        <span class="set-up__n">
          <svg
            v-if="index < at"
            aria-hidden="true"
            fill="none"
            stroke="currentColor"
            stroke-width="2.4"
            viewBox="0 0 24 24"
          ><path d="m5 12.5 4.5 4.5L19 7" /></svg>
          <template v-else>{{ index + 1 }}</template>
        </span>
        <span class="set-up__name">{{ STEP_NAMES[one] }}</span>
      </li>
    </ol>

    <div class="set-up__stage">
      <div class="set-up__main">
        <p class="set-up__eyebrow">
          Step {{ at + 1 }} of {{ order.length }}
        </p>

        <form
          v-if="stage === 'password'"
          class="set-up__form"
          @submit.prevent="start"
        >
          <h2 class="set-up__title">
            Re-enter your password
          </h2>
          <p class="set-up__lede">
            {{ copy.passwordLede }}
          </p>
          <form-field
            v-slot="field"
            class="set-up__narrow"
            label="Password"
            testid="two-factor-password-field"
          >
            <text-input
              v-model="password"
              autocomplete="current-password"
              :control-id="field.controlId"
              type="password"
            />
          </form-field>
          <div class="set-up__acts">
            <cut-button
              :disabled="!password || busy"
              submit
              testid="two-factor-start-btn"
              tone="solid"
            >
              Continue
            </cut-button>
            <cut-button
              v-if="leave"
              :href="leave"
              testid="two-factor-leave-btn"
              tone="quiet"
            >
              Not now
            </cut-button>
          </div>
        </form>

        <template v-else-if="stage === 'scan'">
          <h2 class="set-up__title">
            Scan this with your authenticator app
          </h2>
          <div class="set-up__scan">
            <img
              v-if="qr"
              alt="QR code to add ESA Blueshell to an authenticator app"
              class="set-up__qr"
              data-testid="two-factor-qr"
              height="208"
              :src="qr"
              width="208"
            >
            <div class="set-up__key">
              <p class="set-up__lede">
                Google Authenticator, Microsoft Authenticator, 1Password, Bitwarden or any app that shows
                six-digit codes. Scanning adds ESA Blueshell to it.
              </p>
              <p class="set-up__label">
                Cannot scan? Type this key
              </p>
              <p
                class="set-up__key-value"
                data-testid="two-factor-key"
              >
                {{ pending?.key }}
              </p>
              <cut-button
                class="set-up__copy"
                tone="quiet"
                @click="copyKey"
              >
                {{ keyCopied ? "Copied" : "Copy the key" }}
              </cut-button>
            </div>
          </div>
          <div class="set-up__acts">
            <cut-button
              :disabled="!pending"
              testid="two-factor-scanned-btn"
              tone="solid"
              @click="stage = 'code'"
            >
              I have scanned it
            </cut-button>
          </div>
        </template>

        <form
          v-else-if="stage === 'code'"
          class="set-up__form"
          @submit.prevent="confirm"
        >
          <h2 class="set-up__title">
            Enter the code your app shows
          </h2>
          <p class="set-up__lede">
            Six digits, and a new one every thirty seconds.
          </p>
          <form-field
            v-slot="field"
            class="set-up__narrow"
            label="Code"
            testid="two-factor-code-field"
          >
            <text-input
              v-model="code"
              autocomplete="one-time-code"
              :control-id="field.controlId"
              inputmode="numeric"
            />
          </form-field>
          <div class="set-up__acts">
            <cut-button
              :disabled="!code.trim() || busy"
              submit
              testid="two-factor-confirm-btn"
              tone="solid"
            >
              Check the code
            </cut-button>
            <cut-button
              tone="quiet"
              @click="stage = 'scan'"
            >
              Back
            </cut-button>
          </div>
        </form>

        <template v-else>
          <h2 class="set-up__title">
            Save your backup codes
          </h2>
          <p class="set-up__lede">
            Each one signs you in once if your phone is gone. Keep them somewhere other than your phone;
            they are shown this once.
          </p>
          <backup-codes :codes="codesToSave" />
          <div data-testid="two-factor-saved-check">
            <check-box
              v-model="saved"
              label="I have saved them somewhere safe"
            />
          </div>
          <div class="set-up__acts">
            <cut-button
              :disabled="!saved || busy"
              testid="two-factor-finish-btn"
              tone="solid"
              @click="finish"
            >
              {{ copy.finish }}
            </cut-button>
          </div>
        </template>

        <notice-box
          v-if="error"
          testid="two-factor-error"
          tone="danger"
        >
          {{ error }}
        </notice-box>
      </div>

      <aside class="set-up__aside">
        <p class="set-up__aside-title">
          {{ copy.asideTitle }}
        </p>
        <p>{{ copy.aside }}</p>
        <p>
          Lost your phone and your codes later on? An admin resets it after checking it is you. Ask at
          <a href="mailto:board@blueshell.utwente.nl">board@blueshell.utwente.nl</a>.
        </p>
      </aside>
    </div>
  </div>
</template>

<script lang="ts" setup>
import {computed, onMounted, ref} from "vue"
import QRCode from "qrcode"
import CheckBox from "@/components/island/CheckBox.vue"
import CutButton from "@/components/island/CutButton.vue"
import FormField from "@/components/island/FormField.vue"
import NoticeBox from "@/components/island/NoticeBox.vue"
import TextInput from "@/components/island/TextInput.vue"
import BackupCodes from "./BackupCodes.vue"
import {confirmTwoFactorCode, finishTwoFactorSetUp, startTwoFactorSetUp} from "../adapters/accountSecurity"
import type {TwoFactorSetupResponse} from "@/services/api"

type Stage = "password" | "scan" | "code" | "codes"

/**
 * Setting up an authenticator app, or replacing one: the password, the QR code, a first code and
 * the backup codes, one step at a time.
 *
 * Required is the set-up a granted role is sent to at sign-in. It opens at the phone, because that
 * sign-in proved it is the person; once the step-up window has passed the api refuses, and the
 * password step comes back in front.
 */
const {mode, leave = ""} = defineProps<{
  mode: "voluntary" | "replace" | "required"
  /** Where Not now goes. Without it the set-up offers no way off. */
  leave?: string
}>()

const emit = defineEmits<{ done: []; stepUp: [retry: () => void] }>()

const STEP_NAMES: Record<Stage, string> = {
  password: "Confirm it is you",
  scan: "Scan the code",
  code: "Enter a code",
  codes: "Save backup codes",
}

const COPY = {
  voluntary: {
    passwordLede: "Signing in then asks for a code from your phone as well. Re-enter your password to begin.",
    asideTitle: "What changes",
    aside: "A password alone no longer gets into your account. You can trust a browser for thirty days so it does not ask every time.",
    finish: "Turn on two-factor",
  },
  replace: {
    passwordLede: "Your current app keeps working until the new one is set up. Re-enter your password to begin.",
    asideTitle: "What changes",
    aside: "The new app and ten new backup codes replace the old ones the moment you finish. Every other sign-in ends.",
    finish: "Use the new app",
  },
  required: {
    passwordLede: "It has been a while since you signed in, so confirm it is still you before adding a phone.",
    asideTitle: "Why this is asked",
    aside: "Board, treasurer and admin roles reach member data and money. A code from your phone keeps them yours even if your password leaks.",
    finish: "Turn on two-factor and continue",
  },
}

const cameBack = ref(false)
const order = computed<Stage[]>(() =>
  mode === "required" && !cameBack.value ? ["scan", "code", "codes"] : ["password", "scan", "code", "codes"])
const copy = computed(() => COPY[mode])

const stage = ref<Stage>(mode === "required" ? "scan" : "password")
const at = computed(() => order.value.indexOf(stage.value))
const password = ref("")
const code = ref("")
const pending = ref<TwoFactorSetupResponse>()
const qr = ref<string>()
const keyCopied = ref(false)
const codesToSave = ref<string[]>([])
const saved = ref(false)
const busy = ref(false)
const error = ref<string | null>(null)

const start = async () => {
  busy.value = true
  error.value = null
  const result = await startTwoFactorSetUp(password.value || undefined)
  busy.value = false
  if (!result.ok) {
    if (!result.needsStepUp) error.value = result.reason
    else if (mode !== "required") emit("stepUp", start)
    else {
      cameBack.value = true
      stage.value = "password"
    }
    return
  }
  pending.value = result.value
  qr.value = await QRCode.toDataURL(result.value.otpauthUri, {margin: 1, width: 208})
  password.value = ""
  stage.value = "scan"
}

onMounted(() => {
  if (mode === "required") void start()
})

const copyKey = async () => {
  await navigator.clipboard?.writeText(pending.value?.key ?? "")
  keyCopied.value = true
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
  error.value = null
  const result = await finishTwoFactorSetUp()
  busy.value = false
  if (result.ok) emit("done")
  else error.value = result.reason
}
</script>

<style scoped>
.set-up__steps {
  display: grid;
  grid-auto-columns: minmax(0, 1fr);
  grid-auto-flow: column;
  gap: 2px;
  margin-top: 1.75rem;
}

.set-up__step {
  display: flex;
  align-items: center;
  gap: 0.8rem;
  padding: 0.9rem 1rem 0.9rem 1.1rem;
  background-color: var(--band-ground);
  color: var(--color-ash);
}

.set-up__step--on {
  color: var(--color-chalk);
  box-shadow: inset 0 -3px 0 var(--color-eyebrow);
}

.set-up__n {
  display: grid;
  place-items: center;
  min-width: 1.2rem;
  font-family: var(--font-display);
  font-size: 1.5rem;
  line-height: 1;
}

.set-up__step--done .set-up__n {
  color: var(--color-ok);
}

.set-up__n svg {
  width: 18px;
  height: 18px;
}

.set-up__name {
  font-size: 0.82rem;
  letter-spacing: 0.04em;
}

.set-up__stage {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 22rem;
  gap: 4rem;
  align-items: start;
  padding-top: 2.5rem;
}

.set-up__main,
.set-up__form {
  display: flex;
  flex-direction: column;
  gap: 1.15rem;
  max-width: 40rem;
}

.set-up__eyebrow,
.set-up__aside-title {
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 0.3em;
  text-transform: uppercase;
  color: var(--color-eyebrow);
}

.set-up__title {
  font-family: var(--font-display);
  font-size: 2.1rem;
  line-height: 1.02;
  text-transform: uppercase;
}

.set-up__lede {
  max-width: 38rem;
  font-size: 1.02rem;
  line-height: 1.6;
  color: color-mix(in oklab, var(--color-chalk) 86%, transparent);
}

.set-up__narrow {
  max-width: 26rem;
}

.set-up__acts {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.6rem;
  padding-top: 0.4rem;
}

.set-up__scan {
  display: grid;
  grid-template-columns: 13rem minmax(0, 1fr);
  gap: 1.75rem;
  align-items: start;
}

/* White whatever the theme: a scanner reads dark squares on light. */
.set-up__qr {
  width: 13rem;
  height: 13rem;
  padding: 0.9rem;
  background: #ffffff;
}

.set-up__key {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 0.45rem;
}

.set-up__key .set-up__lede {
  font-size: 0.9rem;
  color: var(--color-ash);
}

.set-up__label {
  margin-top: 0.6rem;
  font-size: 0.7rem;
  font-weight: 500;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--color-ash);
}

.set-up__key-value {
  font-family: var(--font-bitmap);
  font-size: 1rem;
  line-height: 1.7;
  letter-spacing: 0.08em;
  word-break: break-all;
}

.set-up__aside {
  display: flex;
  flex-direction: column;
  gap: 0.8rem;
  padding: 1.3rem 1.4rem 1.4rem;
  background-color: var(--band-ground);
  font-size: 0.9rem;
  line-height: 1.55;
  color: var(--color-ash);
}

.set-up__aside a {
  color: var(--color-brand);
}

@media (max-width: 767px) {
  .set-up__steps {
    margin-top: 1.1rem;
  }

  .set-up__step {
    flex-direction: column;
    align-items: flex-start;
    gap: 0.3rem;
    padding: 0.7rem 0.6rem;
  }

  .set-up__name {
    font-size: 0.66rem;
  }

  .set-up__stage {
    grid-template-columns: minmax(0, 1fr);
    gap: 1.75rem;
    padding-top: 1.5rem;
  }

  .set-up__title {
    font-size: 1.6rem;
  }

  .set-up__scan {
    grid-template-columns: minmax(0, 1fr);
    gap: 1.1rem;
  }
}
</style>
