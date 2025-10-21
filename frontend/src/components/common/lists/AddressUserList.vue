<template>
  <div>
    <v-card>
      <h2 class="text-center">
        {{ title }} ({{ users.length }})
      </h2>
    </v-card>

    <v-list class="mt-3">
      <div
        v-for="user in users"
        :key="user.id ?? user.username"
      >
        <address-user-row
          :addresses="addresses"
          :expanded="expanded"
          :user="user"
          @update:expanded="updateExpanded"
          @update:address="updateAddress"
          @delete:address="deleteAddress"
        />
        <v-divider />
      </div>
    </v-list>
  </div>
</template>

<script lang="ts" setup>
import AddressUserRow from "../rows/AddressUserRow.vue"
import type {Address, AdvancedUser} from "@/services/api"
import {toRefs} from "vue"

const props = withDefaults(defineProps<{
  title: string
  addresses?: Address[]
  users: AdvancedUser[]
  expanded?: number | null
}>(), {
  addresses: () => [],
  expanded: null,
})

const {title, users, addresses, expanded} = toRefs(props)

const emit = defineEmits<{
  (e: "update:address", address: Address): void
  (e: "delete:address", addressId: number): void
  (e: "update:expanded", userId: number): void
}>()

const updateExpanded = (userId: number) => emit("update:expanded", userId)
const updateAddress = (address: Address) => emit("update:address", address)
const deleteAddress = (addressId: number) => emit("delete:address", addressId)
</script>
