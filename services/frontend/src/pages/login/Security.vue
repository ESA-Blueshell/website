<template>
  <account-frame
    body="How you sign in and who can. Pick what you came for."
    heading="Security"
    island-content
  >
    <notice-box
      v-if="standing?.required"
      class="security__notice"
      testid="security-set-up-required"
      title="Your role waits for two-factor"
      tone="warning"
    >
      <p>
        You hold a board, treasurer or admin role. It allows nothing until you set up two-factor
        authentication, which takes about two minutes.
      </p>
      <cut-button
        class="security__notice-way"
        :href="REQUIRED_SET_UP"
        testid="security-set-up-two-factor-btn"
        tone="solid"
      >
        Set up two-factor
      </cut-button>
    </notice-box>
    <notice-box
      v-else-if="low"
      class="security__notice"
      testid="security-backup-codes-low"
      :title="codesLeft"
      tone="warning"
    >
      <p>Make new ones before you run out. The old ones stop working.</p>
      <cut-button
        class="security__notice-way"
        href="/account/security/two-factor"
        testid="security-backup-codes-low-btn"
      >
        Make new codes
      </cut-button>
    </notice-box>

    <fact-list
      class="security__facts"
      :facts="facts"
    />

    <div class="security__rows">
      <cut-row
        :meta="twoFactorMeta"
        testid="security-two-factor"
        title="Two-factor authentication"
        :to="twoFactorPage"
      >
        <template #glyph>
          <security-glyph name="shield" />
        </template>
        <template #end>
          <state-tag :tone="standing?.on ? 'ok' : standing?.required ? 'warn' : 'quiet'">
            {{ standing?.on ? "On" : "Off" }}
          </state-tag>
        </template>
      </cut-row>
      <cut-row
        meta="Every other sign-in ends when you change it"
        testid="security-password"
        title="Password"
        to="/account/security/password"
      >
        <template #glyph>
          <security-glyph name="key" />
        </template>
      </cut-row>
      <cut-row
        :meta="emailMeta"
        testid="security-email"
        title="Email address"
        to="/account/security/email"
      >
        <template #glyph>
          <security-glyph name="mail" />
        </template>
        <template #end>
          <state-tag
            v-if="address?.pendingEmail"
            tone="warn"
          >
            Waiting
          </state-tag>
        </template>
      </cut-row>
      <cut-row
        :meta="signInsMeta"
        testid="security-sign-ins"
        title="Where you are signed in"
        to="/account/security/sign-ins"
      >
        <template #glyph>
          <security-glyph name="screens" />
        </template>
      </cut-row>
      <cut-row
        :meta="logMeta"
        testid="security-log"
        title="Security log"
        to="/account/security/log"
      >
        <template #glyph>
          <security-glyph name="log" />
        </template>
      </cut-row>
    </div>
  </account-frame>
</template>

<script lang="ts" setup>
import {computed, onMounted, ref} from "vue"
import {useStore} from "vuex"
import AccountFrame from "@/components/common/AccountFrame.vue"
import CutButton from "@/components/island/CutButton.vue"
import CutRow from "@/components/island/CutRow.vue"
import FactList, {type Fact} from "@/components/island/FactList.vue"
import NoticeBox from "@/components/island/NoticeBox.vue"
import StateTag from "@/components/island/StateTag.vue"
import {
  describeBrowser,
  describeSecurityEvent,
  type EmailAddressResponse,
  formatSecurityDay,
  formatSecurityMoment,
  lastChangeIn,
  listSignIns,
  listTrustedBrowsers,
  LOW_BACKUP_CODES,
  readEmailAddress,
  readMySecurityLog,
  readTwoFactor,
  SecurityGlyph,
  type SecurityEventResponse,
  type SignInResponse,
  type TrustedBrowserResponse,
  type TwoFactorStanding,
} from "@/domains/auth"
import type {TypedStore} from "@/plugins/store"

const REQUIRED_SET_UP = "/account/set-up-two-factor"

const store = useStore() as TypedStore

const standing = ref<TwoFactorStanding | null>(null)
const address = ref<EmailAddressResponse | null>(null)
const signIns = ref<SignInResponse[]>([])
const trusted = ref<TrustedBrowserResponse[]>([])
const events = ref<SecurityEventResponse[]>([])

const plural = (n: number, one: string, many = `${one}s`) => `${n} ${n === 1 ? one : many}`

const low = computed(() => standing.value?.on === true && standing.value.backupCodesLeft < LOW_BACKUP_CODES)
const codesLeft = computed(() => `${plural(standing.value?.backupCodesLeft ?? 0, "backup code")} left`)
const current = computed(() => signIns.value.find(one => one.current))
const lastChange = computed(() => lastChangeIn(events.value))

const facts = computed<Fact[]>(() => [
  {
    label: "Two-factor",
    value: standing.value?.on ? "On" : "Off",
    sub: standing.value?.on
      ? codesLeft.value
      : standing.value?.required ? "Your role waits for it" : "A code on top of your password",
    testid: "security-standing-two-factor",
  },
  {
    label: "Signed in",
    value: plural(signIns.value.length, "browser"),
    sub: current.value ? `This one since ${formatSecurityDay(current.value.signedInAt)}` : undefined,
    testid: "security-standing-sign-ins",
  },
  {
    label: "Last change",
    value: lastChange.value ? describeSecurityEvent(lastChange.value) : "Nothing yet",
    sub: lastChange.value ? whereAndWhen(lastChange.value) : undefined,
    testid: "security-standing-last-change",
  },
])

const whereAndWhen = (event: SecurityEventResponse) =>
  [formatSecurityDay(event.occurredAt), event.browser && event.platform ? describeBrowser(event.browser, event.platform) : ""]
    .filter(Boolean)
    .join(" · ")

const twoFactorPage = computed(() => {
  if (standing.value?.on) return "/account/security/two-factor"
  return standing.value?.required ? REQUIRED_SET_UP : "/account/security/two-factor/set-up"
})
const twoFactorMeta = computed(() => standing.value?.on
  ? `An authenticator app${standing.value.since ? `, on since ${formatSecurityDay(standing.value.since)}` : ""}`
  : "A code from your phone on top of your password")
const emailMeta = computed(() => {
  if (!address.value) return ""
  return address.value.pendingEmail ? `${address.value.email} · moving to ${address.value.pendingEmail}` : address.value.email
})
const signInsMeta = computed(() =>
  `${plural(signIns.value.length, "sign-in")} · ${trusted.value.length ? plural(trusted.value.length, "trusted browser") : "no trusted browsers"}`)
const logMeta = computed(() => {
  const newest = events.value[0]
  return newest
    ? `Last: ${describeSecurityEvent(newest)} ${formatSecurityMoment(newest.occurredAt)}`
    : "Nothing in the last twelve months"
})

onMounted(async () => {
  const [read, email, signedIn, browsers, log] = await Promise.all([
    readTwoFactor(), readEmailAddress(), listSignIns(), listTrustedBrowsers(), readMySecurityLog(0),
  ])
  standing.value = read
  if (read) store.commit("setTwoFactor", read)
  address.value = email
  signIns.value = signedIn
  trusted.value = browsers
  events.value = log?.events ?? []
})
</script>

<style scoped>
.security__notice {
  margin-top: 1.5rem;
}

.security__notice-way {
  margin-top: 0.75rem;
}

.security__facts {
  padding: 1.6rem 0 1.8rem;
}

.security__rows {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
</style>
