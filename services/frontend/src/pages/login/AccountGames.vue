<template>
  <account-frame heading="Games">
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
  </account-frame>
</template>

<script lang="ts" setup>
import {computed} from "vue"
import {useStore} from "vuex"
import AccountFrame from "@/components/common/AccountFrame.vue"
import GameHandles from "@/domains/esports/components/GameHandles.vue"

const store = useStore()
const userId = computed<number | undefined>(() => store.getters.getLogin?.userId)
</script>
