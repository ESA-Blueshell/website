<template>
  <account-frame
    heading="Games"
    island-content
  >
    <task-layout aside-title="Where it shows">
      <p class="games__lede">
        What you are called in each game. Change it here and every roster you are on follows. Leave a
        game empty if you do not play it.
      </p>
      <game-handles
        v-if="userId"
        :name-shown="nameShown"
        :user-id="userId"
      />

      <template #aside>
        <p>
          The esports pages list you by your handle on every team and season you play in, and a
          captain finds you by it when putting a lineup together.
        </p>
        <p>Your name shows beside it only if you say so, on the Account tab.</p>
      </template>
    </task-layout>
  </account-frame>
</template>

<script lang="ts" setup>
import {computed, onMounted, ref} from "vue"
import {useStore} from "vuex"
import AccountFrame from "@/components/common/AccountFrame.vue"
import TaskLayout from "@/components/island/TaskLayout.vue"
import GameHandles from "@/domains/esports/island/GameHandles.vue"
import {readMemberProfile} from "@/domains/user"

const store = useStore()
const userId = computed<number | undefined>(() => store.getters.getLogin?.userId)
const nameShown = ref<boolean | null>(null)

onMounted(async () => {
  if (userId.value == null) return
  nameShown.value = (await readMemberProfile(userId.value))?.nameOnRosters ?? null
})
</script>

<style scoped>
.games__lede {
  max-width: 38rem;
  font-size: 1.02rem;
  line-height: 1.6;
}
</style>
