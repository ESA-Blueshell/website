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

        <UserList
          title="Non-member users"
          :users="nonMembers"
          :expanded="expanded"
          :contributions="contributions"
          @contribution-changed="contributionChanged"
          @toggle-expanded="toggleExpanded"
          @user-changed="userChanged"
          @delete-user="deleteUser"
        />

        <UserList
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
<script lang="ts">
import {ref, watch, onMounted, defineComponent} from 'vue';
import TopBanner from '@/components/banners/TopBanner.vue';
import ContributionPeriodList from '@/views/login/member/ContributionPeriodList.vue';
import UserList from '@/views/login/member/UserList.vue';
import UserService from "@/services/UserService.ts";
import {type ContributionModel, type AdvancedUserModel, Role} from "@/models";
import {ContributionService} from "@/services";

export default defineComponent({
  name: 'MemberManager',
  components: {
    TopBanner: TopBanner,
    ContributionPeriodList,
    UserList,
  },
  setup() {
    const members = ref([] as AdvancedUserModel[]);
    const nonMembers = ref([] as AdvancedUserModel[]);
    const users = ref([] as AdvancedUserModel[]);
    const contributions = ref([] as ContributionModel[]);
    const expanded = ref(0);
    const search = ref('');
    const userService = new UserService();
    const contributionService = new ContributionService();
    const selectedPeriodId = ref(0)

    if ('scrollRestoration' in window.history) {
      window.history.scrollRestoration = 'manual';
    }


    const getUsers = async () => {
      users.value = await userService.getUsers();
      updateMembers();
    };

    const isSearched = (user: AdvancedUserModel) => {
      if (!search.value) {
        return true
      }
      // Split the search string into individual terms and convert them to lowercase
      const searchTerms = search.value.toLowerCase().split(' ').filter(Boolean);

      // Get all values from the user object, filter out falsy values, and convert them to lowercase strings
      const userValues = Object.values(user)
        .filter(Boolean)
        .map(value => String(value).toLowerCase());

      // Check if any search term is included in any of the user values
      return searchTerms.every((term) =>
        userValues.some((value) => value.includes(term))
      );
    };

    const updateMembers = () => {
      members.value = users.value.filter((user) => user?.membership && !user.membership?.endDate && isSearched(user));
      nonMembers.value = users.value.filter((user) => (!user?.membership || user?.membership?.endDate) && isSearched(user));
    };

    watch(search, () => {
      updateMembers();
    });

    const deleteUser = (user: AdvancedUserModel) => {
      users.value = users.value.filter((u) => u.id !== user.id);
      updateMembers();
    };

    const toggleExpanded = (userId: number) => {
      expanded.value = userId === expanded.value ? 0 : userId;
    };

    const userChanged = async (user: AdvancedUserModel) => {
      const index = users.value.findIndex((u) => u.id === user.id);
      if (index !== -1) {
        users.value.splice(index, 1, user);
      } else {
        users.value.push(user);
        contributions.value = await contributionService.getByPeriod(selectedPeriodId.value);
      }
      updateMembers();
    };

    const contributionChanged = (updatedContribution: ContributionModel) => {
      const index = contributions.value.findIndex((c) => c.id === updatedContribution.id);
      if (index !== -1) {
        contributions.value.splice(index, 1, updatedContribution);
      } else {
        contributions.value.push(updatedContribution);
      }
    };

    const selectedPeriodIdChanged = async (periodId: number) => {
      contributions.value = await contributionService.getByPeriod(periodId);
      selectedPeriodId.value = periodId;
    }

    onMounted(() => {
      getUsers();
    });

    return {
      members,
      nonMembers,
      contributions,
      expanded,
      search,
      deleteUser,
      toggleExpanded,
      userChanged,
      contributionChanged,
      selectedPeriodIdChanged,
    };
  },
});
</script>

<style scoped>
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
