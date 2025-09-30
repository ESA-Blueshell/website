<template>
  <div>
    <p class="text-h3">
      {{ title }} ({{ users.length }})
    </p>
    <v-list class="mt-3">
      <!-- Add New User Row -->
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
      <!-- Existing Users -->
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
          @toggle-expanded="toggleExpanded"
          @contribution-changed="contributionChanged"
          @membership-changed="membershipChanged"
          @user-changed="userChanged"
          @delete-user="deleteUser"
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
  (e: "toggle-expanded", userId: number): void;
  (e: "user-changed", user: AdvancedUser): void;
  (e: "delete-user", user: AdvancedUser): void;
  (e: "contribution-changed", contribution: Contribution): void;
  (e: "membership-changed", membership: Membership): void;
}>()

// Handlers
const toggleExpanded = (userId: number) => {
  emit("toggle-expanded", userId)
}

const contributionChanged = (contribution: Contribution) => {
  emit("contribution-changed", contribution)
}

const membershipChanged = (membership: Membership) => {
  emit("membership-changed", membership)
}

const userChanged = (user: AdvancedUser) => {
  toggleExpanded(0)
  emit("user-changed", user)
}

const deleteUser = (user: AdvancedUser) => {
  emit("delete-user", user)
}
</script>
