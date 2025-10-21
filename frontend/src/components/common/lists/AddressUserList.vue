<template>
  <div>
    <p class="text-h3">
      {{ title }} ({{ users.length }})
    </p>

    <v-list class="mt-3">
      <div
        v-for="user in users"
        :key="user.id ?? user.username"
      >
        <address-user-row
          :addresses="addresses"
          :enable-delete="enableDelete"
          :expanded="expanded"
          :user="user"
          @update:expanded="toggleExpanded"
          @update:address="addressChanged"
          @update:user="userChanged"
          @delete:user="deleteUser"
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
  (e: "delete:user", user: AdvancedUser): void
  (e: "update:user", user: AdvancedUser): void
  (e: "update:address", address: Address): void
  (e: "update:expanded", userId: number): void
}>()

const toggleExpanded = (userId: number) => emit("update:expanded", userId)
const addressChanged = (address: Address) => emit("update:address", address)
</script>
