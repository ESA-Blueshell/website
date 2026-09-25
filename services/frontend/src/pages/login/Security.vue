<template>
  <account-frame
    body="How you sign in and who can. Pick what you came for."
    heading="Security"
    island-content
  >
    <notice-box
      v-if="low"
      class="security__notice"
      testid="security-backup-codes-low"
      :title="codesLeft"
      tone="warning"
    >
      <p>Make new ones before you run out. The old ones stop working.</p>
      <cut-button
        class="security__notice-way"
        :href="SECURITY_PAGES.twoFactor"
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
        :to="SECURITY_PAGES.password"
      >
        <template #glyph>
          <security-glyph name="key" />
        </template>
      </cut-row>
      <cut-row
        :meta="emailMeta"
        testid="security-email"
        title="Email address"
        :to="SECURITY_PAGES.email"
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
        :to="SECURITY_PAGES.signIns"
      >
        <template #glyph>
          <security-glyph name="screens" />
        </template>
      </cut-row>
      <cut-row
        :meta="logMeta"
        testid="security-log"
        title="Security log"
        :to="SECURITY_PAGES.log"
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
  sayCount,
  SECURITY_PAGES,
  SecurityGlyph,
  type SecurityEventResponse,
  type SignInResponse,
  type TrustedBrowserResponse,
  type TwoFactorStanding,
} from "@/domains/auth"
import type {TypedStore} from "@/plugins/store"

const store = useStore() as TypedStore

const standing = ref<TwoFactorStanding | null>(null)
const address = ref<EmailAddressResponse | null>(null)
const signIns = ref<SignInResponse[]>([])
const trusted = ref<TrustedBrowserResponse[]>([])
const events = ref<SecurityEventResponse[]>([])

const low = computed(() => standing.value?.on === true && standing.value.backupCodesLeft < LOW_BACKUP_CODES)
const codesLeft = computed(() => `${sayCount(standing.value?.backupCodesLeft ?? 0, "backup code")} left`)
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
    value: sayCount(signIns.value.length, "browser"),
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

const twoFactorPage = computed(() => (standing.value?.on ? SECURITY_PAGES.twoFactor : SECURITY_PAGES.setUp))
const twoFactorMeta = computed(() => standing.value?.on
  ? `An authenticator app${standing.value.since ? `, on since ${formatSecurityDay(standing.value.since)}` : ""}`
  : "A code from your phone on top of your password")
const emailMeta = computed(() => {
  if (!address.value) return ""
  return address.value.pendingEmail ? `${address.value.email} · moving to ${address.value.pendingEmail}` : address.value.email
})
const signInsMeta = computed(() =>
  `${sayCount(signIns.value.length, "sign-in")} · ${trusted.value.length ? sayCount(trusted.value.length, "trusted browser") : "no trusted browsers"}`)
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
