<template>
  <account-frame
    heading="Keep your account yours"
    eyebrow="Two-factor authentication"
    island-content
    :tabs="false"
  >
    <task-layout data-testid="two-factor-offer">
      <p>
        With two-factor authentication, signing in also asks for a code from an app on your phone. Somebody
        who learns your password still cannot get in. Setting it up takes about two minutes, and you can do
        it any time from the Security page.
      </p>
      <div class="form-save">
        <cut-button
          tone="plain"
          data-testid="two-factor-offer-decline-btn"
          @click="answer(false)"
        >
          Not now
        </cut-button>
        <cut-button
          tone="solid"
          data-testid="two-factor-offer-accept-btn"
          @click="answer(true)"
        >
          Set up now
        </cut-button>
      </div>
    </task-layout>
  </account-frame>
</template>

<script lang="ts" setup>
import AccountFrame from "@/components/common/AccountFrame.vue"
import TaskLayout from "@/components/island/TaskLayout.vue"
import CutButton from "@/components/island/CutButton.vue"
import {useRoute, useRouter} from "vue-router"
import {useStore} from "vuex"
import {answerOffer, readTwoFactor, SECURITY_PAGES} from "@/domains/auth"
import type {TypedStore} from "@/plugins/store"

const route = useRoute()
const router = useRouter()
const store = useStore() as TypedStore

/** Either answer is the answer: the offer is not made again, on this browser or any other. */
const answer = async (setUpNow: boolean) => {
  await answerOffer()
  const standing = await readTwoFactor()
  if (standing) store.commit("setTwoFactor", standing)
  const onward = String(route.query.redirect ?? "/")
  if (setUpNow) await router.replace({path: SECURITY_PAGES.setUp, query: {redirect: onward}})
  else await router.replace(onward)
}
</script>
