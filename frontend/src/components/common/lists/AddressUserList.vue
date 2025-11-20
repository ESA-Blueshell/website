<template>
  <v-card class="overflow-hidden">
    <div
      :aria-controls="panelId"
      :aria-expanded="String(isOpen)"
      class="px-5 py-3 d-flex align-center justify-space-between"
      role="button"
      tabindex="0"
      @click="isOpen = !isOpen"
      @keydown.enter.prevent="isOpen = !isOpen"
      @keydown.space.prevent="isOpen = !isOpen"
    >
      <div
        class="d-flex align-center"
        style="gap: 12px;"
      >
        <div
          class="d-flex align-center"
          style="gap: 12px;"
        >
          <v-badge
            :content="countLabel"
            color="primary"
          >
            <h2 class="ma-0">
              {{ title }}
            </h2>
          </v-badge>
        </div>
      </div>
      <v-icon
        color="grey-darken-1"
        size="24"
      >
        {{ isOpen ? "mdi-chevron-up" : "mdi-chevron-down" }}
      </v-icon>
    </div>

    <v-expand-transition>
      <div
        v-show="isOpen"
        :id="panelId"
        class="px-5 pb-4 pt-2"
      >
        <v-text-field
          v-model="localSearch"
          clearable
          density="comfortable"
          hide-details
          label="Search for a user"
          prepend-inner-icon="mdi-magnify"
        />

        <v-list>
          <template
            v-for="user in filtered"
            :key="user.id ?? user.username"
          >
            <address-user-row
              :addresses="addresses"
              :allow-create="allowCreate"
              :enable-delete="enableDelete"
              :expanded="expanded"
              :user="user"
              @update:expanded="updateExpanded"
              @update:address="updateAddress"
              @delete:address="deleteAddress"
            />
            <v-divider />
          </template>

          <div
            v-if="filtered.length === 0"
            class="text-medium-emphasis text-center py-6"
          >
            No users found.
          </div>
        </v-list>
      </div>
    </v-expand-transition>
  </v-card>
</template>

<script lang="ts" setup>
import {computed, ref, toRefs} from "vue"
import AddressUserRow from "../rows/AddressUserRow.vue"
import type {Address, AdvancedUser} from "@/services/api"
import {filterUsers} from "@/plugins/userFilter"

const props = withDefaults(defineProps<{
  title: string
  addresses?: Address[]
  users: AdvancedUser[]
  expanded?: number | null
  allowCreate?: boolean
  enableDelete?: boolean
  startOpen?: boolean
}>(), {
  addresses: () => [],
  expanded: null,
  allowCreate: false,
  enableDelete: false,
  startOpen: false,
})

const {title, users, addresses, expanded, allowCreate, enableDelete, startOpen} = toRefs(props)

const emit = defineEmits<{
  (e: "update:address", address: Address): void
  (e: "delete:address", addressId: number): void
  (e: "update:expanded", userId: number): void
}>()

const localSearch = ref("")
const isOpen = ref<boolean>(startOpen.value)
const panelId = `aul-${Math.random().toString(36).slice(2)}`

const filtered = computed(() => filterUsers(users.value, localSearch.value))

const countLabel = computed(() =>
  localSearch.value
    ? `${filtered.value.length} / ${users.value.length}`
    : `${users.value.length}`,
)

const updateExpanded = (userId: number) => emit("update:expanded", userId)
const updateAddress = (address: Address) => emit("update:address", address)
const deleteAddress = (addressId: number) => emit("delete:address", addressId)
</script>
