<template>
  <v-main>
    <top-banner title="Recovery Manager" />

    <v-container>
      <div
        class="mx-auto my-3"
        style="max-width: 800px"
      >
        <recovery-user-list
          panel-key="inactive"
          :users="inactiveUsers"
          :pending-activations="pendingActivations"
          action-type="activation"
          title="Inactive accounts"
          @action:done="reloadLists"
        />

        <recovery-user-list
          panel-key="active"
          :users="activeUsers"
          action-type="password"
          class="mt-3"
          title="Active accounts"
          @action:done="reloadLists"
        />

        <recovery-user-list
          panel-key="deleted"
          :users="deletedUsers"
          action-type="restore"
          class="mt-3"
          title="Deleted users"
          @action:done="reloadLists"
        />
      </div>
    </v-container>
  </v-main>
</template>

<script lang="ts" setup>
import {onMounted, ref, watch} from "vue"
import TopBanner from "@/components/common/banners/TopBanner.vue"
import RecoveryUserList from "@/components/common/lists/RecoveryUserList.vue"

import {
  type TokenPurpose,
  type UserDetailResponse,
  findDeletedUsers,
  findUsers,
  pendingActivations as fetchPendingActivations,
} from "@/services/api"

const users = ref<UserDetailResponse[]>([])
const activeUsers = ref<UserDetailResponse[]>([])
const inactiveUsers = ref<UserDetailResponse[]>([])
const deletedUsers = ref<UserDetailResponse[]>([])
// Which activation each inactive account takes, so a row offers that one and no other.
const pendingActivations = ref<Record<number, TokenPurpose>>({})

if ("scrollRestoration" in globalThis.history) {
  globalThis.history.scrollRestoration = "manual"
}

const getUsers = async () => {
  const response = await findUsers()
  if (response.status === 200) users.value = response.data?.content ?? []
  else console.log(response.error)
}

const getDeletedUsers = async () => {
  const response = await findDeletedUsers()
  if (response.status === 200) deletedUsers.value = response.data?.content ?? []
  else console.log(response.error)
}

const getPendingActivations = async () => {
  const {data} = await fetchPendingActivations()
  pendingActivations.value = Object.fromEntries(
    (data?.activations ?? []).map((entry) => [entry.userId, entry.purpose]),
  )
}

const updateLists = () => {
  const all = users.value
  inactiveUsers.value = all.filter((u) => !u.enabled)
  activeUsers.value = all.filter((u) => !!u.enabled)
}

watch([users], updateLists, {deep: true})

const reloadLists = async () => {
  await Promise.all([getUsers(), getDeletedUsers(), getPendingActivations()])
}

onMounted(async () => {
  try {
    await reloadLists()
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
