<template>
  <div>
    <p class="text-h3">
      {{ title }} ({{ users.length }})
    </p>

    <v-list class="mt-3">
      <div v-if="allowCreate">
        <v-list-item @click="toggleCreate()">
          <div
            class="d-flex justify-space-between align-center"
            style="width: 100%;"
          >
            <v-list-item-title>Add Member</v-list-item-title>
            <v-icon>mdi-plus</v-icon>
          </div>
        </v-list-item>

        <v-expand-transition>
          <div v-if="expanded === -1">
            <advanced-user-form
              :model-value="{}"
              class="mt-4"
              @user-changed="createdUserChanged"
            />
          </div>
        </v-expand-transition>
        <v-divider />
      </div>

      <div
        v-for="user in users"
        :key="user.id ?? user.username"
      >
        <member-user-list-row
          :user="user"
          :memberships="memberships"
          :expanded="expanded"
          :enable-delete="enableDelete"
          @update:expanded="toggleExpanded"
          @update:membership="membershipChanged"
          @update:user="userChanged"
          @delete:user="deleteUser"
        />
        <v-divider />
      </div>
    </v-list>
  </div>
</template>

<script lang="ts" setup>
import MemberUserListRow from "./MemberUserListRow.vue"
import AdvancedUserForm from "@/components/user/AdvancedUserForm.vue"
import type {AdvancedUser, Membership} from "@/lib"
import {toRefs} from "vue"

const props = withDefaults(defineProps<{
  title: string
  memberships?: Membership[]
  users: AdvancedUser[]
  expanded?: number | null
  allowCreate?: boolean
  enableDelete?: boolean
}>(), {
  memberships: () => [],
  expanded: null,
  allowCreate: false,
  enableDelete: false,
})

const {title, users, memberships, expanded, allowCreate} = toRefs(props)

const emit = defineEmits<{
  (e: "delete:user", user: AdvancedUser): void
  (e: "update:user", user: AdvancedUser): void
  (e: "update:membership", membership: Membership): void
  (e: "update:expanded", userId: number): void
}>()

const toggleExpanded = (userId: number) => emit("update:expanded", userId)

const toggleCreate = () => emit("update:expanded", (expanded.value === -1 ? 0 : -1))

const membershipChanged = (membership: Membership) => emit("update:membership", membership)
const userChanged = (user: AdvancedUser) => emit("update:user", user)
const deleteUser = (user: AdvancedUser) => emit("delete:user", user)

const createdUserChanged = (user: AdvancedUser) => {
  emit("update:expanded", 0) // close create panel
  emit("update:user", user)
}
</script>
