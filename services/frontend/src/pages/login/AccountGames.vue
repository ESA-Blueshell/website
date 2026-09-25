<template>
  <account-frame
    heading="Esports Teams"
    island-content
  >
    <task-layout aside-title="Where it shows">
      <div
        v-if="user"
        class="games__name"
        data-testid="games-name"
      >
        <div>
          <p class="games__name-title">
            Your name on the esports pages
          </p>
          <p class="games__name-body">
            {{ user.nameOnRosters
              ? `Your name, ${user.fullName}, shows beside your handle on the esports pages. Hide my name takes it off again.`
              : "Your name is hidden on the esports pages; only your handle shows. Show my name gives permission to display it beside your handle." }}
          </p>
        </div>
        <cut-button
          :disabled="busy"
          testid="games-name-toggle-btn"
          :tone="user.nameOnRosters ? 'quiet' : 'solid'"
          @click="toggleName"
        >
          {{ user.nameOnRosters ? "Hide my name" : "Show my name" }}
        </cut-button>
      </div>

      <template #aside>
        <p>
          The esports pages list you by your handle on every team and season you play in, and a
          captain finds you by it when putting a lineup together.
        </p>
      </template>
    </task-layout>

    <template v-if="userId">
      <game-handles :user-id="userId" />
      <played-rosters :user-id="userId" />
    </template>
  </account-frame>
</template>

<script lang="ts" setup>
import {computed, onMounted, ref} from "vue"
import {useStore} from "vuex"
import AccountFrame from "@/components/common/AccountFrame.vue"
import CutButton from "@/components/island/CutButton.vue"
import TaskLayout from "@/components/island/TaskLayout.vue"
import GameHandles from "@/domains/esports/island/GameHandles.vue"
import PlayedRosters from "@/domains/esports/island/PlayedRosters.vue"
import {readUser, saveNameOnRosters, type UserDetailResponse} from "@/domains/user"

const store = useStore()
const userId = computed<number | undefined>(() => store.getters.getLogin?.userId)
const user = ref<UserDetailResponse | null>(null)
const busy = ref(false)

const toggleName = async () => {
  if (!user.value) return
  busy.value = true
  const shown = await saveNameOnRosters(user.value.id, !user.value.nameOnRosters)
  busy.value = false
  if (shown === null) {
    store.commit("setStatusSnackbarMessage", "That could not be saved. Try again.")
    return
  }
  user.value = {...user.value, nameOnRosters: shown}
  store.commit("setStatusSnackbarMessage", shown ? "Your name now shows beside your handle." : "Only your handle shows now.")
}

onMounted(async () => {
  if (userId.value == null) return
  user.value = await readUser(userId.value)
})
</script>

<style scoped>
.games__name {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 1rem 2rem;
  padding: 1.1rem 1.3rem;
  background-color: var(--band-ground);
}

.games__name-title {
  font-family: var(--font-display);
  font-size: 1rem;
  text-transform: uppercase;
}

.games__name-body {
  margin-top: 0.25rem;
  font-size: 0.88rem;
  color: var(--color-ash);
}
</style>
