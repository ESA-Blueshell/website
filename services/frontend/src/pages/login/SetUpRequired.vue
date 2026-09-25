<template>
  <account-frame
    body="Your board, treasurer or admin role opens once your account has a second factor. It takes about two minutes."
    eyebrow="One more step"
    heading="Set up two-factor"
    island-content
    :tabs="false"
  >
    <template #actions>
      <cut-button
        testid="two-factor-sign-out-btn"
        tone="quiet"
        @click="leave"
      >
        Sign out
      </cut-button>
    </template>

    <div data-testid="security-set-up-required">
      <two-factor-set-up
        mode="required"
        @done="done"
      />
    </div>
  </account-frame>
</template>

<script lang="ts" setup>
import {useRoute, useRouter} from "vue-router"
import {useStore} from "vuex"
import AccountFrame from "@/components/common/AccountFrame.vue"
import CutButton from "@/components/island/CutButton.vue"
import {readTwoFactor, signOut, TwoFactorSetUp} from "@/domains/auth"
import {readUser} from "@/domains/user"
import type {TypedStore} from "@/plugins/store"

/**
 * Where a granted role waiting on two-factor is sent after signing in, and kept until it is set up.
 * Sign out is the only way off.
 */
const store = useStore() as TypedStore
const route = useRoute()
const router = useRouter()

const done = async () => {
  const [standing, user] = await Promise.all([readTwoFactor(), readUser(store.getters.getLogin!.userId)])
  if (standing) store.commit("setTwoFactor", standing)
  if (user) store.commit("setRoles", user.roles)
  store.commit("setStatusSnackbarMessage", "Two-factor authentication is on, and your role with it.")
  await router.replace(typeof route.query.redirect === "string" ? route.query.redirect : "/")
}

const leave = async () => {
  await signOut()
  store.commit("logout")
  await router.replace("/login")
}
</script>
