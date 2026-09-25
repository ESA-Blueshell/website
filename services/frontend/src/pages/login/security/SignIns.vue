<template>
  <account-frame
    :crumb="SECURITY_CRUMB"
    eyebrow="Security"
    heading="Where you are signed in"
    island-content
  >
    <section data-testid="security-sign-ins">
      <div class="sign-ins__head">
        <h2 class="sign-ins__title">
          Signed in<count-badge
            :count="signIns.length"
            said="sign-ins"
            testid="security-sign-ins-count"
          />
        </h2>
        <div class="sign-ins__acts">
          <cut-button
            v-if="signIns.length > 1"
            testid="security-sign-out-elsewhere-btn"
            @click="signOutElsewhere"
          >
            Sign out everywhere else
          </cut-button>
          <cut-button
            testid="security-sign-out-everywhere-btn"
            tone="quiet"
            @click="signOutEverywhere"
          >
            Sign out everywhere
          </cut-button>
        </div>
      </div>
      <div class="sign-ins__rows">
        <cut-row
          v-for="one in signIns"
          :key="one.id"
          :meta="`Signed in ${formatSecurityDay(one.signedInAt)} · ${one.current ? 'active now' : `last seen ${formatSecurityMoment(one.lastSeenAt)}`}`"
          testid="security-sign-in"
          :title="describeBrowser(one.browser, one.platform)"
        >
          <template #glyph>
            <security-glyph :name="glyphOf(one.platform)" />
          </template>
          <template
            v-if="one.current"
            #tag
          >
            <state-tag tone="ok">
              This browser
            </state-tag>
          </template>
          <template
            v-else
            #end
          >
            <cut-button
              tone="quiet"
              @click="endSignIn(one.id)"
            >
              Sign out
            </cut-button>
          </template>
        </cut-row>
      </div>
    </section>

    <section data-testid="security-trusted-browsers">
      <div class="sign-ins__head">
        <h2 class="sign-ins__title">
          Trusted browsers<count-badge
            :count="trusted.length"
            said="trusted browsers"
            testid="security-trusted-browsers-count"
          />
        </h2>
        <p class="sign-ins__note">
          They skip the code at sign-in for thirty days
        </p>
      </div>
      <p
        v-if="!trusted.length"
        class="sign-ins__note"
      >
        No browser skips the code at sign-in.
      </p>
      <div class="sign-ins__rows">
        <cut-row
          v-for="one in trusted"
          :key="one.id"
          :meta="`Trusted ${formatSecurityDay(one.trustedAt)} · until ${formatSecurityDay(one.expiresAt)}`"
          testid="security-trusted-browser"
          :title="describeBrowser(one.browser, one.platform)"
        >
          <template #glyph>
            <security-glyph :name="glyphOf(one.platform)" />
          </template>
          <template #end>
            <cut-button
              tone="quiet"
              @click="forget(one.id)"
            >
              Forget
            </cut-button>
          </template>
        </cut-row>
      </div>
      <cut-button
        v-if="trusted.length"
        class="sign-ins__all"
        testid="security-forget-all-btn"
        tone="quiet"
        @click="forgetAll"
      >
        Forget all
      </cut-button>
    </section>

    <p class="sign-ins__note sign-ins__foot">
      Something you do not recognise? Sign out everywhere, then change your password.
    </p>
  </account-frame>
</template>

<script lang="ts" setup>
import {onMounted, ref} from "vue"
import {useRouter} from "vue-router"
import {useStore} from "vuex"
import AccountFrame from "@/components/common/AccountFrame.vue"
import CountBadge from "@/components/island/CountBadge.vue"
import CutButton from "@/components/island/CutButton.vue"
import CutRow from "@/components/island/CutRow.vue"
import StateTag from "@/components/island/StateTag.vue"
import {
  describeBrowser,
  endEverySignIn,
  endOneSignIn,
  endOtherSignIns,
  forgetEveryTrustedBrowser,
  forgetOneTrustedBrowser,
  formatSecurityDay,
  formatSecurityMoment,
  listSignIns,
  listTrustedBrowsers,
  SECURITY_CRUMB,
  SecurityGlyph,
  type SignInResponse,
  type TrustedBrowserResponse,
  type Written,
} from "@/domains/auth"
import type {TypedStore} from "@/plugins/store"

const PHONES = new Set(["iOS", "Android"])

const store = useStore() as TypedStore
const router = useRouter()
const tell = (message: string) => store.commit("setStatusSnackbarMessage", message)

const signIns = ref<SignInResponse[]>([])
const trusted = ref<TrustedBrowserResponse[]>([])

const glyphOf = (platform: string) => (PHONES.has(platform) ? "phone" : "screens")

const loadSignIns = async () => {
  signIns.value = await listSignIns()
}

const loadTrusted = async () => {
  trusted.value = await listTrustedBrowsers()
}

const said = (result: Written<unknown>) => {
  if (!result.ok) tell(result.reason)
}

const endSignIn = async (id: string) => {
  said(await endOneSignIn(id))
  await loadSignIns()
}

const signOutElsewhere = async () => {
  said(await endOtherSignIns())
  await loadSignIns()
}

const signOutEverywhere = async () => {
  const result = await endEverySignIn()
  if (!result.ok) {
    tell(result.reason)
    return
  }
  store.commit("logout")
  await router.replace("/login")
}

const forget = async (id: number) => {
  said(await forgetOneTrustedBrowser(id))
  await loadTrusted()
}

const forgetAll = async () => {
  said(await forgetEveryTrustedBrowser())
  await loadTrusted()
}

onMounted(() => Promise.all([loadSignIns(), loadTrusted()]))
</script>

<style scoped>
.sign-ins__head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: 1rem 2rem;
  margin: 2.5rem 0 1.1rem;
}

.sign-ins__title {
  font-family: var(--font-display);
  font-size: 1.6rem;
  line-height: 1.1;
  text-transform: uppercase;
}

.sign-ins__acts {
  display: flex;
  flex-wrap: wrap;
  gap: 0.6rem;
}

.sign-ins__rows {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.sign-ins__note {
  font-size: 0.85rem;
  color: var(--color-ash);
}

.sign-ins__all {
  margin-top: 1rem;
}

.sign-ins__foot {
  margin-top: 1.5rem;
}

@media (max-width: 767px) {
  .sign-ins__head {
    flex-direction: column;
    align-items: flex-start;
    gap: 0.9rem;
  }

  .sign-ins__title {
    font-size: 1.3rem;
  }
}
</style>
