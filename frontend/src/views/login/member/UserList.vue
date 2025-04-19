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
            <UserEdit
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
        <UserListRow
          :user="user"
          :expanded="expanded"
          :is-member-list="isMemberList"
          :contributions="contributions"
          @toggle-expanded="toggleExpanded"
          @contribution-changed="contributionChanged"
          @user-changed="userChanged"
          @delete-user="deleteUser"
        />
        <v-divider />
      </div>
    </v-list>
  </div>
</template>

<script lang="ts">
import UserListRow from './UserListRow.vue';
import type {AdvancedUserModel, ContributionModel} from "@/models";
import UserEdit from "@/components/UserEdit.vue";

export default {
  name: 'UserList',
  components: {UserEdit, UserListRow},
  props: {
    title: {
      type: String,
      required: true,
    },
    contributions: {
      type: Array as () => ContributionModel[],
      default: () => [],
    },
    users: {
      type: Array as () => AdvancedUserModel[],
      default: () => [],
    },
    expanded: {
      type: Number,
      default: null,
    },
    isMemberList: {
      type: Boolean,
      default: false,
    },
  },
  emits: [
    'toggle-expanded',
    'user-changed',
    'delete-user',
    'contribution-changed',
  ],
  setup(props, {emit}) {
    const toggleExpanded = (userId: number) => {
      emit('toggle-expanded', userId);
    };

    const contributionChanged = (contribution: ContributionModel) => {
      emit('contribution-changed', contribution);
    }

    const userChanged = (user: AdvancedUserModel) => {
      toggleExpanded(0);
      emit('user-changed', user);
    };

    const deleteUser = (user: AdvancedUserModel) => {
      emit('delete-user', user);
    };

    return {
      toggleExpanded,
      userChanged,
      deleteUser,
      contributionChanged,
    };
  },
};
</script>
