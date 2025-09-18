<template>
  <v-main>
    <top-banner title="Member Manager" />

    <div class="mx-3">
      <div
        class="mx-auto my-10"
        style="max-width: 800px"
      >
        <contribution-period-list
          @selected-period-id-changed="selectedPeriodIdChanged"
        />

        <v-text-field
          v-model="search"
          label="Search for a user"
        />

        <user-list
          title="Non-member users"
          :users="nonMembers"
          :expanded="expanded"
          :contributions="contributions"
          @contribution-changed="contributionChanged"
          @toggle-expanded="toggleExpanded"
          @user-changed="userChanged"
          @delete-user="deleteUser"
        />

        <user-list
          class="mt-5"
          title="Members"
          :users="members"
          :expanded="expanded"
          is-member-list
          :contributions="contributions"
          @contribution-changed="contributionChanged"
          @toggle-expanded="toggleExpanded"
          @user-changed="userChanged"
          @delete-user="deleteUser"
        />
      </div>
    </div>
  </v-main>
</template>
<script setup lang="ts">
import {onMounted, ref, watch} from 'vue';
import TopBanner from '@/components/banners/TopBanner.vue';
import ContributionPeriodList from '@/views/member/ContributionPeriodList.vue';
import UserList from '@/views/member/UserList.vue';
import {type AdvancedUserDto, type ContributionDto, findContributionsByPeriodId, findUsers, Role} from '@/lib';

// State
const members = ref([] as AdvancedUserDto[]);
const nonMembers = ref([] as AdvancedUserDto[]);
const users = ref([] as AdvancedUserDto[]);
const contributions = ref([] as ContributionDto[]);
const expanded = ref(0);
const search = ref('');
const selectedPeriodId = ref(0);

if ('scrollRestoration' in window.history) {
  window.history.scrollRestoration = 'manual';
}

// Data loading
const getUsers = async () => {
  const response = await findUsers({});
  users.value = response.data ?? [];
  updateMembers();
};

// Helpers
const isSearched = (user: AdvancedUserDto) => {
  if (!search.value) return true;

  const searchTerms = search.value.toLowerCase().split(' ').filter(Boolean);
  const userValues = Object.values(user ?? {})
    .filter(Boolean)
    .map((v) => String(v).toLowerCase());

  return searchTerms.every((term) => userValues.some((value) => value.includes(term)));
};

const updateMembers = () => {
  members.value = users.value.filter(
    (user: AdvancedUserDto) => user.roles.includes(Role.MEMBER) && isSearched(user)
  );
  nonMembers.value = users.value.filter(
    (user: AdvancedUserDto) => (!user.roles.includes(Role.MEMBER)) && isSearched(user)
  );
};

watch(search, () => {
  updateMembers();
});

// Events
const deleteUser = (user: AdvancedUserDto) => {
  users.value = users.value.filter((u) => u.id !== user.id);
  updateMembers();
};

const toggleExpanded = (userId: number) => {
  expanded.value = userId === expanded.value ? 0 : userId;
};

const userChanged = async (user: AdvancedUserDto) => {
  const index = users.value.findIndex((u) => u.id === user.id);
  if (index !== -1) {
    users.value.splice(index, 1, user);
  } else {
    users.value.push(user);
    if (selectedPeriodId.value) {
      const resp = await findContributionsByPeriodId({ path: { periodId: selectedPeriodId.value } });
      contributions.value = resp.data ?? [];
    }
  }
  updateMembers();
};

const contributionChanged = (updatedContribution: ContributionDto) => {
  const index = contributions.value.findIndex((c) => c.id === updatedContribution.id);
  if (index !== -1) {
    contributions.value.splice(index, 1, updatedContribution);
  } else {
    contributions.value.push(updatedContribution);
  }
};

const selectedPeriodIdChanged = async (periodId: number) => {
  if (!periodId) return;
  const resp = await findContributionsByPeriodId({ path: { periodId } });
  contributions.value = resp.data ?? [];
  selectedPeriodId.value = periodId;
};

// Lifecycle
onMounted(() => {
  getUsers();
});
</script>

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
