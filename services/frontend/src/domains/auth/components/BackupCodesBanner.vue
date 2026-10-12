<template>
  <toast-area place="top">
    <toast-notice
      :open="shown"
      testid="backup-codes-banner"
      tone="warning"
    >
      {{ left }} backup {{ left === 1 ? "code" : "codes" }} left. Create new ones before you run out.
      <template #actions>
        <cut-button
          :href="SECURITY_PAGES.twoFactor"
          small
          testid="backup-codes-banner-open-btn"
          @click="dismissed = true"
        >
          Create new codes
        </cut-button>
        <cut-button
          small
          testid="backup-codes-banner-dismiss-btn"
          tone="quiet"
          @click="dismissed = true"
        >
          Later
        </cut-button>
      </template>
    </toast-notice>
  </toast-area>
</template>

<script lang="ts" setup>
import {computed, ref} from "vue"
import CutButton from "@/components/island/CutButton.vue"
import ToastArea from "@/components/island/ToastArea.vue"
import ToastNotice from "@/components/island/ToastNotice.vue"
import {useRoute} from "vue-router"
import {useStore} from "vuex"
import type {TypedStore} from "@/plugins/store"
import {LOW_BACKUP_CODES} from "../securityEvents"
import {SECURITY_PAGES} from "../securityPages"

const store = useStore() as TypedStore
const route = useRoute()
const dismissed = ref(false)

const left = computed(() => store.getters.getLogin?.twoFactor?.backupCodesLeft ?? 0)
const shown = computed(() =>
  store.getters.getLogin?.twoFactor?.on === true
  && left.value < LOW_BACKUP_CODES
  && !dismissed.value
  && route.path !== SECURITY_PAGES.twoFactor
  && route.path !== SECURITY_PAGES.hub,
)
</script>
