<template>
  <v-main>
    <top-banner title="Recovery Manager" />

    <div class="mx-3">
      <div
        class="mx-auto my-10"
        style="max-width: 800px"
      >
        <v-text-field
          v-model="search"
          label="Search for a user"
        />

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
const search = ref("")

if ("scrollRestoration" in globalThis.history) {
  globalThis.history.scrollRestoration = "manual"
}

const getUsers = async () => {
  const response = await findUsers()
  if (response.status === 200) {
    users.value = response.data?.content ?? []
  } else {
    console.log(response.error)
  }
}

const isSearched = (user: AdvancedUser) => {
  if (!search.value) return true
  const searchTerms = search.value.toLowerCase().split(" ").filter(Boolean)
  const userValues = Object.values(user ?? {})
    .filter(Boolean)
    .map((v) => String(v).toLowerCase())
  return searchTerms.every((term) => userValues.some((value) => value.includes(term)))
}

const updateLists = () => {
  const filtered = users.value.filter(isSearched)
  inactiveUsers.value = filtered.filter((u) => !u.enabled)
  activeUsers.value = filtered.filter((u) => !!u.enabled)
}

watch([users, search], () => updateLists(), {deep: true})

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
