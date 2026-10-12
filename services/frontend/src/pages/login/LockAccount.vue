<template>
  <account-frame
    :heading="loading ? 'Lock your account' : 'Your account is locked'"
    island-content
    :tabs="false"
  >
    <task-layout data-testid="lock-account">
      <loading-spinner v-if="loading" />
      <template v-else>
        <p>
          If the link was still good, nobody can sign in to your account now and every sign-in has ended. The
          change you were told about has not been undone.
        </p>
        <p>
          To get back in, contact the board. An admin will check it is you and unlock the account:
        </p>
        <ul class="lock__ways">
          <li v-if="contact">
            email <a
              class="form-link"
              :href="`mailto:${contact}`"
              data-testid="lock-account-contact-email"
            >{{ contact }}</a>
          </li>
          <li>
            ask in <a
              class="form-link"
              :href="discordChannel('board')"
            >#board-questions</a> or
            <a
              class="form-link"
              :href="discordChannel('suggestions')"
            >#sitecie</a> on our Discord
          </li>
        </ul>
      </template>
    </task-layout>
  </account-frame>
</template>

<script lang="ts" setup>
import AccountFrame from "@/components/common/AccountFrame.vue"
import TaskLayout from "@/components/island/TaskLayout.vue"
import {onMounted, ref} from "vue"
import {useRoute, useRouter} from "vue-router"
import {useStore} from "vuex"
import LoadingSpinner from "@/components/island/LoadingSpinner.vue"
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

<style scoped>
.lock__ways {
  margin-left: 1.25rem;
  list-style: disc;
}
</style>
