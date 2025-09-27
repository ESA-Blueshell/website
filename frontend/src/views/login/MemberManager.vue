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
          :contributions="contributions"
          :expanded="expanded"
          :memberships="memberships"
          :selected-period-id="selectedPeriodId"
          :users="nonMembers"
          title="Non-member users"
          @contribution-changed="contributionChanged"
          @membership-changed="membershipChanged"
          @toggle-expanded="toggleExpanded"
          @user-changed="userChanged"
          @delete-user="deleteUser"
        />

        <user-list
          :contributions="contributions"
          :expanded="expanded"
          :memberships="memberships"
          :users="members"
          class="mt-5"
          is-member-list
          title="Members"
          @contribution-changed="contributionChanged"
          @toggle-expanded="toggleExpanded"
          @user-changed="userChanged"
          @delete-user="deleteUser"
        />
      </div>
    </div>
  </v-main>
</template>
<script lang="ts" setup>
import {onMounted, ref, watch} from "vue"
import TopBanner from "@/components/banners/TopBanner.vue"
import ContributionPeriodList from "@/components/contribution-period/ContributionPeriodList.vue"
import UserList from "@/components/user/UserList.vue"
import {
  type AdvancedUser,
  type Contribution,
  findContributionsByPeriodId,
  findMemberships,
  findUsers,
  type Membership,
} from "@/lib"

// State
const members = ref([] as AdvancedUser[])
const nonMembers = ref([] as AdvancedUser[])
const users = ref([] as AdvancedUser[])
const contributions = ref([] as Contribution[])
const memberships = ref([] as Membership[])
const expanded = ref(0)
const search = ref("")
const selectedPeriodId = ref(0)

if ("scrollRestoration" in window.history) {
  window.history.scrollRestoration = "manual"
}

// Data loading
const getUsers = async () => {
  try {
    const response = await findUsers()
    users.value = response.data ?? []
  } catch (error) {
    console.error("Error fetching users:", error)
  }
}

const getMemberships = async () => {
  try {
    const response = await findMemberships()
    memberships.value = response.data ?? []
  } catch (error) {
    console.error("Error fetching memberships:", error)
  }
}

// Helpers
const isSearched = (user: AdvancedUser) => {
  if (!search.value) return true

  const searchTerms = search.value.toLowerCase().split(" ").filter(Boolean)
  const userValues = Object.values(user ?? {})
    .filter(Boolean)
    .map((v) => String(v).toLowerCase())

  return searchTerms.every((term) => userValues.some((value) => value.includes(term)))
}

const updateMembers = () => {
  members.value = users.value.filter(
    (user: AdvancedUser) => memberships.value.some((m) => m.userId === user.id) && isSearched(user),
  )
  nonMembers.value = users.value.filter(
    (user: AdvancedUser) => !memberships.value.some((m) => m.userId === user.id) && isSearched(user),
  )
}

watch(search, () => {
  updateMembers()
})

// Events
const deleteUser = (user: AdvancedUser) => {
  users.value = users.value.filter((u) => u.id !== user.id)
  updateMembers()
}

const toggleExpanded = (userId: number) => {
  expanded.value = userId === expanded.value ? 0 : userId
}

const userChanged = async (user: AdvancedUser) => {
  const index = users.value.findIndex((u) => u.id === user.id)
  if (index !== -1) {
    users.value.splice(index, 1, user)
  } else {
    users.value.push(user)
    if (selectedPeriodId.value) {
      const resp = await findContributionsByPeriodId({path: {periodId: selectedPeriodId.value}})
      contributions.value = resp.data ?? []
    }
  }
  updateMembers()
}

const contributionChanged = (updatedContribution: Contribution) => {
  const index = contributions.value.findIndex((c) => c.id === updatedContribution.id)
  if (index !== -1) {
    contributions.value.splice(index, 1, updatedContribution)
  } else {
    contributions.value.push(updatedContribution)
  }
}

const membershipChanged = (updatedMembership: Membership) => {
  const index = memberships.value.findIndex((c) => c.id === updatedMembership.id)
  if (index !== -1) {
    memberships.value.splice(index, 1, updatedMembership)
  } else {
    memberships.value.push(updatedMembership)
  }
  updateMembers()
}

const selectedPeriodIdChanged = async (periodId: number) => {
  if (!periodId) return
  const resp = await findContributionsByPeriodId({path: {periodId}})
  contributions.value = resp.data ?? []
  selectedPeriodId.value = periodId
}

// Lifecycle
onMounted(async () => {
  try {
    await Promise.all([getUsers(), getMemberships()])

    updateMembers()
  } catch (error) {
    console.error("Error fetching data:", error)
  }
})
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
