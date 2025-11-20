<script lang="ts" setup>
import {computed, ref, toRefs} from "vue"
import MemberUserRow from "../rows/MemberUserRow.vue"
import AdvancedUserForm from "@/components/form/AdvancedUserForm.vue"
import type {AdvancedUser, Contribution, Membership} from "@/services/api"
import {filterUsers} from "@/plugins/userFilter"

defineOptions({name: "MemberUserList"})

const props = withDefaults(defineProps<{
  title: string
  membershipsByUserId?: Record<number, Membership>,
  contributionsByUserId?: Record<number, Contribution>,
  users: AdvancedUser[]
  allowCreate?: boolean
  enableDelete?: boolean
  startOpen?: boolean
}>(), {
  membershipsByUserId: () => ({}),
  contributionsByUserId: () => ({}),
  allowCreate: false,
  enableDelete: false,
  startOpen: false,
})

const expanded = defineModel<number>("expanded", {default: 0})

const {title, users, membershipsByUserId, contributionsByUserId, allowCreate, enableDelete, startOpen} = toRefs(props)

const emit = defineEmits<{
  (e: "delete:user", user: AdvancedUser): void
  (e: "update:user", user: AdvancedUser): void
  (e: "update:membership", membership: Membership): void
}>()

const localSearch = ref("")
const isOpen = ref<boolean>(startOpen.value)
const panelId = `mul-${Math.random().toString(36).slice(2)}`

const filteredUsers = computed(() => filterUsers(users.value, localSearch.value))

const countLabel = computed(() =>
  localSearch.value ? `${filteredUsers.value.length} / ${users.value.length}` : `${users.value.length}`,
)

const toggleExpanded = (userId: number) => {
  expanded.value = userId === expanded.value ? 0 : userId
}
const toggleCreate = () => {
  expanded.value = expanded.value === -1 ? 0 : -1
}

const membershipChanged = (membership: Membership) => emit("update:membership", membership)
const updateUser = (user: AdvancedUser) => {
  console.log("user", user)
  emit("update:user", user)
}
const deleteUser = (user: AdvancedUser) => emit("delete:user", user)

const createDraft = ref<AdvancedUser>()
const onCreateSubmitted = (ok: boolean) => {
  if (ok && createDraft.value) {
    emit("update:user", createDraft.value)
    expanded.value = 0
    createDraft.value = undefined
  }
}
</script>

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
      <v-badge
        :content="countLabel"
        color="primary"
      >
        <h2 class="ma-0">
          {{ title }}
        </h2>
      </v-badge>
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
                  v-model="createDraft"
                  class="mt-4"
                  show-submit
                  @submitted="onCreateSubmitted"
                  @update:expanded="toggleExpanded"
                />
              </div>
            </v-expand-transition>
            <v-divider />
          </div>

          <template
            v-for="user in filteredUsers"
            :key="user.id"
          >
            <member-user-row
              v-model:expanded="expanded"
              :contribution="contributionsByUserId[user.id]"
              :enable-delete="enableDelete"
              :membership="membershipsByUserId[user.id]"
              :user="user"
              @update:membership="membershipChanged"
              @update:user="updateUser"
              @delete:user="deleteUser"
              @update:expanded="toggleExpanded"
            />
            <v-divider />
          </template>

          <div
            v-if="filteredUsers.length === 0"
            class="text-medium-emphasis text-center py-6"
          >
            No users found.
          </div>
        </v-list>
      </div>
    </v-expand-transition>
  </v-card>
</template>

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
