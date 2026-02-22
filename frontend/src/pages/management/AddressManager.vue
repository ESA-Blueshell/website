<!-- AddressManager.vue -->
<template>
  <v-main>
    <top-banner title="Address Manager" />

    <div class="mx-3">
      <div
        class="mx-auto my-3"
        style="max-width: 800px"
      >
        <address-user-list
          :addresses="addresses"
          :expanded="expanded"
          panel-key="with-address"
          :users="usersWithAddress"
          title="Users with address"
          @update:address="addressChanged"
          @update:expanded="toggleExpanded"
          @delete:address="deleteAddress"
        />

        <address-user-list
          :addresses="addresses"
          :expanded="expanded"
          panel-key="without-address"
          :users="usersWithoutAddress"
          allow-create
          class="mt-3"
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
import {type AddressResponse, findAllAddresses, findUsers, type UserDetailResponse} from "@/services/api"

type ManagedUser = UserDetailResponse & { addressId?: number }
type ManagedAddress = AddressResponse & { userId?: number }

const users = ref<ManagedUser[]>([])
const addresses = ref<ManagedAddress[]>([])

const usersWithAddress = ref<ManagedUser[]>([])
const usersWithoutAddress = ref<ManagedUser[]>([])

const expanded = ref<number>(0)

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

const hasAddress = (u: ManagedUser) =>
  addresses.value.some((a) => a.id === u.addressId || a.userId === u.id)

const updateLists = () => {
  const all = users.value
  usersWithAddress.value = all.filter((u) => hasAddress(u))
  usersWithoutAddress.value = all.filter((u) => !hasAddress(u))
}

watch([addresses, users], () => updateLists(), {deep: true})

const toggleExpanded = (userId: number) => {
  expanded.value = userId === expanded.value ? 0 : userId
}

const addressChanged = (updated: ManagedAddress) => {
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
