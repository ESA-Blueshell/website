<template>
  <v-main>
    <top-banner title="My games" />
    <div class="mx-3">
      <div
        class="mx-auto my-10"
        style="max-width: 800px"
      >
        <game-handles
          v-if="userId"
          :user-id="userId"
        />
      </div>
    </div>
  </v-main>
</template>

<script lang="ts" setup>
import {computed} from "vue"
import {useStore} from "vuex"
import TopBanner from "@/components/common/banners/TopBanner.vue"
import GameHandles from "@/domains/esports/components/GameHandles.vue"

const store = useStore()
const userId = computed<number | undefined>(() => store.getters.getLogin?.userId)
</script>
