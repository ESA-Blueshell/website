<template>
  <v-main>
    <top-banner title="Address Manager" />

    <div class="mx-3">
      <div
        class="mx-auto my-5"
        style="max-width: 800px"
      >
        <v-text-field
          v-model="search"
          label="Search for a user"
        />

        <address-user-list
          :addresses="addresses"
          :expanded="expanded"
          :users="usersWithAddress"
          title="Users with address"
          @update:address="addressChanged"
          @update:expanded="toggleExpanded"
          @delete:address="deleteAddress"
        />

        <address-user-list
          :addresses="addresses"
          :expanded="expanded"
          :users="usersWithoutAddress"
          allow-create
          enable-delete
          title="Users without address"
          @update:address="addressChanged"
          @update:expanded="toggleExpanded"
          @delete:address="deleteAddress"
        />
      </div>
    </div>
  </v-main>
</template>

<script lang="ts" setup>
import {onMounted, ref, watch} from "vue"
import TopBanner from "@/components/common/banners/TopBanner.vue"
import AddressUserList from "@/components/common/lists/AddressUserList.vue"

import {type Address, type AdvancedUser, findAllAddresses, findUsers} from "@/services/api"

const users = ref<AdvancedUser[]>([])
const addresses = ref<Address[]>([])

const usersWithAddress = ref<AdvancedUser[]>([])
const usersWithoutAddress = ref<AdvancedUser[]>([])

const expanded = ref<number>(0)
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

const getAddresses = async () => {
  const response = await findAllAddresses()
  if (response.status === 200) {
    addresses.value = response.data ?? []
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

const hasAddress = (u: AdvancedUser) => addresses.value.some((a) => a.id === u.addressId)

const updateLists = () => {
  const filtered = users.value.filter((v) => isSearched(v))
  console.log("FILTERING!")
  usersWithAddress.value = filtered.filter((u) => hasAddress(u))
  usersWithoutAddress.value = filtered.filter((u) => !hasAddress(u))
}

watch([addresses, users, search], () => updateLists(), {deep: true})

const toggleExpanded = (userId: number) => {
  expanded.value = userId === expanded.value ? 0 : userId
}

const addressChanged = (updated: Address) => {
  const index = addresses.value.findIndex((a) => a.id === updated.id)
  if (index === -1) {
    addresses.value.push(updated)
  } else {
    addresses.value.splice(index, 1, updated)
  }
}

const deleteAddress = (addressId: number) => {
  addresses.value = addresses.value.filter((a) => a.id !== addressId)
}

onMounted(async () => {
  try {
    await Promise.all([getUsers(), getAddresses()])
  } catch (error) {
    console.error("Error fetching data:", error)
  }
})
</script>

<style lang="scss">
span {
  font-weight: bold;
}

.hover-shadow {
  transition: 0.3s ease-in-out;
}

.hover-shadow:hover {
  box-shadow: 0 4px 8px rgba(186, 181, 181, 0.2);
  border-radius: 50%;
}
</style>
