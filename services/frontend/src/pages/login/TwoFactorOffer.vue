<template>
  <v-main>
    <top-banner title="Two-factor authentication" />
    <div class="mx-3">
      <div
        class="mx-auto my-10"
        data-testid="two-factor-offer"
        style="max-width: 600px"
      >
        <p class="text-h5 mb-4">
          Keep your account yours
        </p>
        <p class="mb-4">
          With two-factor authentication, signing in also asks for a code from an app on your phone. Somebody
          who learns your password still cannot get in. Setting it up takes about two minutes, and you can do
          it any time from the Security page.
        </p>
        <div class="d-flex ga-3">
          <v-btn
            color="primary"
            data-testid="two-factor-offer-accept-btn"
            @click="answer(true)"
          >
            Set up now
          </v-btn>
          <v-btn
            data-testid="two-factor-offer-decline-btn"
            variant="outlined"
            @click="answer(false)"
          >
            Not now
          </v-btn>
        </div>
      </div>
    </div>
  </v-main>
</template>

<script lang="ts" setup>
import {useRoute, useRouter} from "vue-router"
import {useStore} from "vuex"
import TopBanner from "@/components/common/banners/TopBanner.vue"
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
