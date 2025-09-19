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
            <advanced-user-edit
              class="mt-4"
              :model-value="{}"
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
          :user="user"
          :expanded="expanded"
          :is-member-list="isMemberList"
          :contributions="contributions"
          :memberships="memberships"
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

<script setup lang="ts">
import UserListRow from './UserListRow.vue';
import type {AdvancedUserDto, ContributionDto, MembershipDto} from '@/lib/blueshell/types.gen.ts';
import AdvancedUserEdit from "@/components/user/AdvancedUserEdit.vue";
import {toRefs, watch} from "vue";

// Props
const props = withDefaults(defineProps<{
  title: string;
  contributions?: ContributionDto[];
  memberships?: MembershipDto[];
  users: AdvancedUserDto[];
  expanded?: number | null;
  isMemberList?: boolean;
}>(), {
  contributions: () => [],
  memberships: () => [],
  expanded: null,
  isMemberList: false,
});

const { title, users, contributions, memberships, expanded, isMemberList } = toRefs(props);

// Emits
const emit = defineEmits<{
  (e: 'toggle-expanded', userId: number): void;
  (e: 'user-changed', user: AdvancedUserDto): void;
  (e: 'delete-user', user: AdvancedUserDto): void;
  (e: 'contribution-changed', contribution: ContributionDto): void;
  (e: 'membership-changed', membership: MembershipDto): void;
}>();

// Handlers
const toggleExpanded = (userId: number) => {
  emit('toggle-expanded', userId);
};

const contributionChanged = (contribution: ContributionDto) => {
  emit('contribution-changed', contribution);
};

const membershipChanged = (membership: MembershipDto) => {
  emit('membership-changed', membership);
};

const userChanged = (user: AdvancedUserDto) => {
  toggleExpanded(0);
  emit('user-changed', user);
};

const deleteUser = (user: AdvancedUserDto) => {
  emit('delete-user', user);
};
</script>
