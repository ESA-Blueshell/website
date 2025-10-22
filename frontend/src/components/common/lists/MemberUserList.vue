<template>
  <v-card class="overflow-hidden">
    <div
      class="px-5 py-3 d-flex align-center justify-space-between"
      role="button"
      :aria-expanded="String(isOpen)"
      :aria-controls="panelId"
      tabindex="0"
      @click="isOpen = !isOpen"
      @keydown.enter.prevent="isOpen = !isOpen"
      @keydown.space.prevent="isOpen = !isOpen"
    >
      <div
        class="d-flex align-center"
        style="gap: 12px;"
      >
        <h2 class="ma-0">
          {{ title }}
        </h2>
        <v-chip
          size="small"
          variant="tonal"
        >
          {{ countLabel }}
        </v-chip>
      </div>
      <v-icon
        size="24"
        color="grey-darken-1"
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
          label="Search for a user"
          clearable
          density="comfortable"
          prepend-inner-icon="mdi-magnify"
        />

        <v-list class="mt-1">
          <div v-if="allowCreate">
            <v-list-item @click="toggleCreate()">
              <div
                class="d-flex justify-space-between align-center"
                style="width: 100%;"
              >
                <v-list-item-title>Add User</v-list-item-title>
                <v-icon>mdi-plus</v-icon>
              </div>
            </v-list-item>
            <v-expand-transition>
              <div v-if="expanded === -1">
                <advanced-user-form
                  class="mt-4"
                  show-submit
                  :show-username="false"
                  @update:model-value="updateUser"
                />
              </div>
            </v-expand-transition>
            <v-divider />
          </div>

          <template
            v-for="user in filtered"
            :key="user.id ?? user.username"
          >
            <member-user-row
              :contributions="contributions"
              :enable-delete="enableDelete"
              :expanded="expanded"
              :memberships="memberships"
              :user="user"
              @update:expanded="toggleExpanded"
              @update:membership="membershipChanged"
              @update:user="userChanged"
              @delete:user="deleteUser"
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
import MemberUserRow from "../rows/MemberUserRow.vue"
import AdvancedUserForm from "@/components/form/AdvancedUserForm.vue"
import type {AdvancedUser, Contribution, Membership} from "@/services/api"
import {filterUsers} from "@/plugins/userFilter"

const props = withDefaults(defineProps<{
  title: string
  memberships?: Membership[]
  contributions?: Contribution[]
  users: AdvancedUser[]
  expanded?: number | null
  allowCreate?: boolean
  enableDelete?: boolean
  startOpen?: boolean
}>(), {
  memberships: () => [],
  contributions: () => [],
  expanded: null,
  allowCreate: false,
  enableDelete: false,
  startOpen: true,
})

const {title, users, memberships, contributions, expanded, allowCreate, enableDelete, startOpen} = toRefs(props)

const emit = defineEmits<{
  (e: "delete:user", user: AdvancedUser): void
  (e: "update:user", user: AdvancedUser): void
  (e: "update:membership", membership: Membership): void
  (e: "update:expanded", userId: number): void
}>()

const localSearch = ref("")
const isOpen = ref<boolean>(startOpen.value)
const panelId = `mul-${Math.random().toString(36).slice(2)}`

const filtered = computed(() => filterUsers(users.value, localSearch.value))
const countLabel = computed(() =>
  localSearch.value ? `${filtered.value.length} / ${users.value.length}` : `${users.value.length}`,
)

const toggleExpanded = (userId: number) => emit("update:expanded", userId)
const toggleCreate = () => emit("update:expanded", (expanded.value === -1 ? 0 : -1))
const membershipChanged = (membership: Membership) => emit("update:membership", membership)
const userChanged = (user: AdvancedUser) => emit("update:user", user)
const deleteUser = (user: AdvancedUser) => emit("delete:user", user)

const updateUser = (user: AdvancedUser) => {
  emit("update:expanded", 0)
  emit("update:user", user)
}
</script>
