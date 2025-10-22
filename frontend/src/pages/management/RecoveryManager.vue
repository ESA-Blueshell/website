<template>
  <v-main>
    <top-banner title="Recovery Manager" />

    <div class="mx-3">
      <div
        class="mx-auto my-10"
        style="max-width: 800px"
      >
        <recovery-user-list
          :users="inactiveUsers"
          title="Inactive accounts"
          action-type="activation"
        />

        <recovery-user-list
          class="mt-5"
          :users="activeUsers"
          title="Active accounts"
          action-type="password"
        />
      </div>
    </div>
  </v-main>
</template>

<script lang="ts" setup>
import {onMounted, ref, watch} from "vue"
import TopBanner from "@/components/common/banners/TopBanner.vue"
import RecoveryUserList from "@/components/common/lists/RecoveryUserList.vue"

import {type AdvancedUser, findUsers} from "@/services/api"

const users = ref<AdvancedUser[]>([])
const activeUsers = ref<AdvancedUser[]>([])
const inactiveUsers = ref<AdvancedUser[]>([])

if ("scrollRestoration" in globalThis.history) {
  globalThis.history.scrollRestoration = "manual"
}

const getUsers = async () => {
  const response = await findUsers()
  if (response.status === 200) users.value = response.data?.content ?? []
  else console.log(response.error)
}

const updateLists = () => {
  const all = users.value
  inactiveUsers.value = all.filter((u) => !u.enabled)
  activeUsers.value = all.filter((u) => !!u.enabled)
}

watch([users], updateLists, {deep: true})

onMounted(async () => {
  try {
    await getUsers()
  } catch (error) {
    console.error("Error fetching users:", error)
  }
})
</script>

<style lang="scss">
span {
  font-weight: bold;
}
</style>
