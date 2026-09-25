<template>
  <account-frame
    :crumb="SECURITY_CRUMB"
    eyebrow="Security"
    :heading="replacing ? 'Replace your app' : 'Set up two-factor'"
    island-content
  >
    <two-factor-set-up
      :leave="SECURITY_PAGES.hub"
      :mode="replacing ? 'replace' : 'voluntary'"
      @done="done"
      @step-up="askStepUp"
    />

    <step-up-dialog
      v-model="stepUpOpen"
      :two-factor-on="standing?.on === true"
      @proved="stepUpProved"
    />
  </account-frame>
</template>

<script lang="ts" setup>
import {onMounted, ref} from "vue"
import {useRoute, useRouter} from "vue-router"
import {useStore} from "vuex"
import AccountFrame from "@/components/common/AccountFrame.vue"
import {
  readTwoFactor,
  SECURITY_CRUMB,
  SECURITY_PAGES,
  StepUpDialog,
  TwoFactorSetUp,
  type TwoFactorStanding,
  useStepUp,
} from "@/domains/auth"
import type {TypedStore} from "@/plugins/store"

const store = useStore() as TypedStore
const route = useRoute()
const router = useRouter()
const tell = (message: string) => store.commit("setStatusSnackbarMessage", message)
const {open: stepUpOpen, ask: askStepUp, proved: stepUpProved} = useStepUp(tell)

const replacing = route.query.replace === "1"
const standing = ref<TwoFactorStanding | null>(null)

const done = async () => {
  standing.value = await readTwoFactor()
  if (standing.value) store.commit("setTwoFactor", standing.value)
  tell("Two-factor authentication is on.")
  await router.replace(typeof route.query.redirect === "string" ? route.query.redirect : SECURITY_PAGES.twoFactor)
}

onMounted(async () => {
  standing.value = await readTwoFactor()
})
</script>
