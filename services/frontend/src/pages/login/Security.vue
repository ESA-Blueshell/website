<template>
  <v-main>
    <top-banner title="Security" />
    <div class="mx-3">
      <div
        class="mx-auto my-10 security-page"
        style="max-width: 800px"
      >
        <v-alert
          v-if="standing?.required"
          class="mb-6"
          data-testid="security-set-up-required"
          type="info"
          variant="tonal"
        >
          You hold a board, treasurer or admin role. It allows nothing until you set up two-factor
          authentication, which takes about two minutes.
        </v-alert>

        <section data-testid="security-two-factor">
          <h2 class="text-h5 mb-2">
            Two-factor authentication
          </h2>
          <template v-if="standing?.on">
            <p class="mb-3">
              On. Signing in asks for a code from your authenticator app.
              <span data-testid="security-backup-codes-left">
                {{ standing.backupCodesLeft }} backup {{ standing.backupCodesLeft === 1 ? "code" : "codes" }} left.
              </span>
            </p>
            <v-alert
              v-if="standing.backupCodesLeft <= 3"
              class="mb-3"
              data-testid="security-backup-codes-low"
              type="warning"
              variant="tonal"
            >
              You are running low on backup codes. Make new ones before you run out.
            </v-alert>
            <backup-codes
              v-if="freshCodes.length"
              class="mb-3"
              :codes="freshCodes"
            />
            <div
              v-if="!replacing"
              class="d-flex flex-wrap ga-2"
            >
              <v-btn
                data-testid="security-new-backup-codes-btn"
                variant="outlined"
                @click="guarded(makeNewCodes)"
              >
                New backup codes
              </v-btn>
              <v-btn
                data-testid="security-replace-two-factor-btn"
                variant="outlined"
                @click="replacing = true"
              >
                Replace authenticator app
              </v-btn>
              <v-btn
                v-if="!holdsGrantedRole"
                color="error"
                data-testid="security-turn-off-two-factor-btn"
                variant="outlined"
                @click="guarded(turnOff)"
              >
                Turn off
              </v-btn>
            </div>
            <two-factor-set-up
              v-else
              @done="setUpDone"
              @step-up="askStepUp"
            />
          </template>
          <template v-else>
            <p class="mb-3">
              Off. Add a code from your phone to signing in, so a password alone does not get into your account.
            </p>
            <two-factor-set-up
              v-if="settingUp"
              @done="setUpDone"
              @step-up="askStepUp"
            />
            <v-btn
              v-else
              color="primary"
              data-testid="security-set-up-two-factor-btn"
              @click="settingUp = true"
            >
              Set up two-factor
            </v-btn>
          </template>
        </section>

        <v-divider class="my-8" />

        <section data-testid="security-password">
          <h2 class="text-h5 mb-2">
            Password
          </h2>
          <v-form
            ref="passwordForm"
            @submit.prevent="guarded(changePassword)"
          >
            <v-text-field
              v-model="currentPassword"
              data-testid="security-current-password-field"
              autocomplete="current-password"
              label="Current password"
              type="password"
            />
            <v-text-field
              v-model="newPassword"
              data-testid="security-new-password-field"
              autocomplete="new-password"
              label="New password"
              type="password"
            />
            <v-btn
              :disabled="!currentPassword || !newPassword"
              color="primary"
              data-testid="security-change-password-btn"
              type="submit"
            >
              Change password
            </v-btn>
          </v-form>
        </section>

        <v-divider class="my-8" />

        <section data-testid="security-email">
          <h2 class="text-h5 mb-2">
            Email address
          </h2>
          <p class="mb-3">
            A confirmation link goes to the new address. Until it is followed your account keeps the old
            one, which is told about the change.
          </p>
          <v-form @submit.prevent="guarded(moveEmail)">
            <v-text-field
              v-model="newEmail"
              data-testid="security-new-email-field"
              autocomplete="email"
              label="New email address"
              type="email"
            />
            <v-btn
              :disabled="!newEmail"
              color="primary"
              data-testid="security-change-email-btn"
              type="submit"
            >
              Send confirmation link
            </v-btn>
          </v-form>
        </section>

        <v-divider class="my-8" />

        <section data-testid="security-trusted-browsers">
          <h2 class="text-h5 mb-2">
            Trusted browsers
          </h2>
          <p
            v-if="!trusted.length"
            class="mb-3"
          >
            No browser skips the code at sign-in.
          </p>
          <v-list
            v-else
            density="compact"
          >
            <v-list-item
              v-for="one in trusted"
              :key="one.id"
              :subtitle="`Trusted ${formatDate(one.trustedAt)}, until ${formatDate(one.expiresAt)}`"
              :title="`${one.browser} on ${one.platform}`"
              data-testid="security-trusted-browser"
            >
              <template #append>
                <v-btn
                  size="small"
                  variant="text"
                  @click="forget(one.id)"
                >
                  Forget
                </v-btn>
              </template>
            </v-list-item>
          </v-list>
          <v-btn
            v-if="trusted.length"
            data-testid="security-forget-all-btn"
            variant="outlined"
            @click="forgetAll"
          >
            Forget all
          </v-btn>
        </section>

        <v-divider class="my-8" />

        <section data-testid="security-sign-ins">
          <h2 class="text-h5 mb-2">
            Where you are signed in
          </h2>
          <v-list density="compact">
            <v-list-item
              v-for="one in signIns"
              :key="one.id"
              :subtitle="`Signed in ${formatDate(one.signedInAt)}, last seen ${formatDate(one.lastSeenAt)}`"
              :title="`${one.browser} on ${one.platform}${one.current ? ' (this browser)' : ''}`"
              data-testid="security-sign-in"
            >
              <template #append>
                <v-btn
                  v-if="!one.current"
                  size="small"
                  variant="text"
                  @click="endSignIn(one.id)"
                >
                  Sign out
                </v-btn>
              </template>
            </v-list-item>
          </v-list>
          <v-btn
            color="error"
            data-testid="security-sign-out-everywhere-btn"
            variant="outlined"
            @click="everywhere"
          >
            Sign out everywhere
          </v-btn>
        </section>

        <v-divider class="my-8" />

        <section data-testid="security-log">
          <h2 class="text-h5 mb-2">
            Security log
          </h2>
          <p class="mb-3">
            Changes to how your account is signed in to, for the last twelve months.
          </p>
          <v-list density="compact">
            <v-list-item
              v-for="event in events"
              :key="event.id"
              :subtitle="`${formatDate(event.occurredAt)}${event.browser ? `, ${event.browser}` : ''}`"
              :title="describeSecurityEvent(event)"
              data-testid="security-log-entry"
            />
          </v-list>
          <v-btn
            v-if="morePages"
            variant="text"
            @click="loadEvents(page + 1)"
          >
            Show older
          </v-btn>
        </section>
      </div>
    </div>

    <step-up-dialog
      v-model="stepUpOpen"
      :two-factor-on="standing?.on === true"
      @proved="retryAfterStepUp"
    />
  </v-main>
</template>

<script lang="ts" setup>
import {computed, onMounted, ref} from "vue"
import {useRoute, useRouter} from "vue-router"
import {useStore} from "vuex"
import {DateTime} from "luxon"
import TopBanner from "@/components/common/banners/TopBanner.vue"
import {
  askToMoveEmail,
  BackupCodes,
  describeSecurityEvent,
  endEverySignIn,
  endOneSignIn,
  forgetEveryTrustedBrowser,
  forgetOneTrustedBrowser,
  listSignIns,
  listTrustedBrowsers,
  newBackupCodes,
  readMySecurityLog,
  readTwoFactor,
  removeTwoFactor,
  savePassword,
  type SecurityEventResponse,
  type SignInResponse,
  StepUpDialog,
  type TrustedBrowserResponse,
  TwoFactorSetUp,
  type TwoFactorStanding,
  type Written,
} from "@/domains/auth"
import type {TypedStore} from "@/plugins/store"

const store = useStore() as TypedStore
const route = useRoute()
const router = useRouter()

const standing = ref<TwoFactorStanding | null>(null)
const settingUp = ref(route.query.setUp === "1")
const replacing = ref(false)
const freshCodes = ref<string[]>([])
const currentPassword = ref("")
const newPassword = ref("")
const newEmail = ref("")
const trusted = ref<TrustedBrowserResponse[]>([])
const signIns = ref<SignInResponse[]>([])
const events = ref<SecurityEventResponse[]>([])
const page = ref(0)
const morePages = ref(false)
const stepUpOpen = ref(false)
let pendingRetry: (() => void) | null = null

/** Somebody holding a granted role may replace two-factor, never turn it off. */
const holdsGrantedRole = computed(() => store.getters.isBoard || store.getters.isAdmin)

const say = (message: string) => store.commit("setStatusSnackbarMessage", message)

const formatDate = (iso: string) => DateTime.fromISO(iso).toLocaleString(DateTime.DATETIME_MED)

/** Runs a write, and when the api asks for a step-up first, asks for it and runs it again. */
const guarded = async (write: () => Promise<Written<unknown>>) => {
  const result = await write()
  if (result.ok) return
  if (result.needsStepUp) askStepUp(() => void guarded(write))
  else say(result.reason)
}

const askStepUp = (retry: () => void) => {
  pendingRetry = retry
  stepUpOpen.value = true
}

const retryAfterStepUp = () => {
  pendingRetry?.()
  pendingRetry = null
}

const refreshStanding = async () => {
  standing.value = await readTwoFactor()
  if (standing.value) store.commit("setTwoFactor", standing.value)
}

const setUpDone = async () => {
  settingUp.value = false
  replacing.value = false
  await refreshStanding()
  say("Two-factor authentication is on.")
  await Promise.all([loadSignIns(), loadTrusted(), loadEvents(0)])
  if (route.query.redirect) await router.replace(String(route.query.redirect))
}

const makeNewCodes = async (): Promise<Written<unknown>> => {
  const result = await newBackupCodes()
  if (result.ok) {
    freshCodes.value = result.value
    await refreshStanding()
  }
  return result
}

const turnOff = async (): Promise<Written<unknown>> => {
  const result = await removeTwoFactor()
  if (result.ok) {
    await refreshStanding()
    say("Two-factor authentication is off.")
  }
  return result
}

const changePassword = async (): Promise<Written<unknown>> => {
  const result = await savePassword(currentPassword.value, newPassword.value)
  if (result.ok) {
    currentPassword.value = ""
    newPassword.value = ""
    say("Your password is changed. Every other sign-in has ended.")
    await loadSignIns()
  }
  return result
}

const moveEmail = async (): Promise<Written<unknown>> => {
  const result = await askToMoveEmail(newEmail.value.trim())
  if (result.ok) {
    say(`A confirmation link is on its way to ${newEmail.value.trim()}.`)
    newEmail.value = ""
  }
  return result
}

const loadTrusted = async () => {
  trusted.value = await listTrustedBrowsers()
}

const loadSignIns = async () => {
  signIns.value = await listSignIns()
}

const loadEvents = async (next: number) => {
  const read = await readMySecurityLog(next)
  if (!read) return
  events.value = next === 0 ? read.events : [...events.value, ...read.events]
  page.value = next
  morePages.value = read.page + 1 < read.totalPages
}

const forget = async (id: number) => {
  const result = await forgetOneTrustedBrowser(id)
  if (!result.ok) say(result.reason)
  await loadTrusted()
}

const forgetAll = async () => {
  await forgetEveryTrustedBrowser()
  await loadTrusted()
}

const endSignIn = async (id: string) => {
  const result = await endOneSignIn(id)
  if (!result.ok) say(result.reason)
  await loadSignIns()
}

const everywhere = async () => {
  const result = await endEverySignIn()
  if (!result.ok) {
    say(result.reason)
    return
  }
  store.commit("logout")
  await router.replace("/login")
}

onMounted(async () => {
  await refreshStanding()
  await Promise.all([loadTrusted(), loadSignIns(), loadEvents(0)])
})
</script>
