<template>
  <div>
    <p class="text-h3">
      {{ title }} ({{ users.length }})
    </p>
    <v-list class="mt-3">
      <div v-if="isMemberList">
        <v-list-item @click="toggleExpanded(-1)">
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
              @user-changed="userChanged"
            />
          </div>
        </v-expand-transition>
        <v-divider />
      </div>
      <div
        v-for="user in users"
        :key="user.username"
      >
        <user-list-row
          :contributions="contributions"
          :expanded="expanded"
          :is-member-list="isMemberList"
          :memberships="memberships"
          :user="user"
          @update:expanded="toggleExpanded"
          @update:contribution="contributionChanged"
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
import UserListRow from "./UserListRow.vue"
import type {AdvancedUser, Contribution, Membership} from "@/lib/blueshell/types.gen.ts"
import AdvancedUserForm from "@/components/user/AdvancedUserForm.vue"
import {toRefs} from "vue"

// Props
const props = withDefaults(defineProps<{
  title: string;
  contributions?: Contribution[];
  memberships?: Membership[];
  users: AdvancedUser[];
  expanded?: number | null;
  isMemberList?: boolean;
}>(), {
  contributions: () => [],
  memberships: () => [],
  expanded: null,
  isMemberList: false,
})

const {title, users, contributions, memberships, expanded, isMemberList} = toRefs(props)

// Emits
const emit = defineEmits<{
  (e: "delete:user", id: number): void

  (e: "update:user", user: AdvancedUser): void

  (e: "update:membership", membership: Membership): void

  (e: "update:contribution", contribution: Contribution): void;

  (e: "update:expanded", userId: number): void
}>()

// Handlers
const toggleExpanded = (userId: number) => {
  emit("update:expanded", userId)
}

const contributionChanged = (contribution: Contribution) => {
  emit("update:contribution", contribution)
}

const membershipChanged = (membership: Membership) => {
  emit("update:membership", membership)
}

const userChanged = (user: AdvancedUser) => {
  toggleExpanded(0)
  emit("update:user", user)
}

const deleteUser = (user: AdvancedUser) => {
  emit("update:user", user)
}
</script>
