<template>
  <v-main>
    <top-banner title="Lock your account" />
    <div class="mx-3">
      <div
        class="mx-auto my-10"
        data-testid="lock-account"
        style="max-width: 600px"
      >
        <v-progress-circular
          v-if="loading"
          indeterminate
        />
        <template v-else>
          <p class="text-h5 mb-4">
            Your account is locked
          </p>
          <p class="mb-4">
            If the link was still good, nobody can sign in to your account now and every sign-in has ended. The
            change you were told about has not been undone.
          </p>
          <p class="mb-2">
            To get back in, contact the board. An admin will check it is you and unlock the account:
          </p>
          <ul class="mb-4 ml-6">
            <li v-if="contact">
              email <a
                :href="`mailto:${contact}`"
                data-testid="lock-account-contact-email"
              >{{ contact }}</a>
            </li>
            <li>
              ask in <a :href="discordChannel('board')">#board-questions</a> or
              <a :href="discordChannel('suggestions')">#sitecie</a> on our Discord
            </li>
          </ul>
        </template>
      </div>
    </div>
  </v-main>
</template>

<script lang="ts" setup>
import {onMounted, ref} from "vue"
import {useRoute, useRouter} from "vue-router"
import {useStore} from "vuex"
import TopBanner from "@/components/common/banners/TopBanner.vue"
import {lockAccount} from "@/domains/auth"
import {discordChannel} from "@/domains/discord"
import {loadRecoveryTokenFromRoute} from "@/plugins/recoveryToken"
import type {TypedStore} from "@/plugins/store"

const route = useRoute()
const router = useRouter()
const store = useStore() as TypedStore

const loading = ref(true)
const contact = ref<string | null>(null)

onMounted(async () => {
  const token = loadRecoveryTokenFromRoute(route, router, "recovery:account-lock:token")
  if (token) contact.value = await lockAccount(token)
  if (store.getters.isLoggedIn) store.commit("logout")
  loading.value = false
})
</script>
