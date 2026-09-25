<template>
  <v-snackbar
    :model-value="shown"
    color="warning"
    data-testid="backup-codes-banner"
    location="top"
    timeout="-1"
  >
    {{ left }} backup {{ left === 1 ? "code" : "codes" }} left. Make new ones before you run out.
    <template #actions>
      <v-btn
        data-testid="backup-codes-banner-open-btn"
        :to="TWO_FACTOR_PAGE"
        variant="text"
        @click="dismissed = true"
      >
        Make new codes
      </v-btn>
      <v-btn
        data-testid="backup-codes-banner-dismiss-btn"
        variant="text"
        @click="dismissed = true"
      >
        Later
      </v-btn>
    </template>
  </v-snackbar>
</template>

<script lang="ts" setup>
import {computed, ref} from "vue"
import {useRoute} from "vue-router"
import {useStore} from "vuex"
import type {TypedStore} from "@/plugins/store"
import {LOW_BACKUP_CODES} from "../securityEvents"

const TWO_FACTOR_PAGE = "/account/security/two-factor"

const store = useStore() as TypedStore
const route = useRoute()
const dismissed = ref(false)

const left = computed(() => store.getters.getLogin?.twoFactor?.backupCodesLeft ?? 0)
const shown = computed(() =>
  store.getters.getLogin?.twoFactor?.on === true
  && left.value < LOW_BACKUP_CODES
  && !dismissed.value
  && route.path !== TWO_FACTOR_PAGE,
)
</script>
