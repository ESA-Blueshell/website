<template>
  <v-main>
    <top-banner title="Contribution Manager" />

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

        <contribution-user-list
          title="Contribution paid"
          :users="membersPaid"
          :contributions="contributions"
          :disabled="!selectedPeriodId"
          :contribution-period-id="selectedPeriodId"
          @update:contribution="contributionAddedOrUpdated"
          @delete:contribution="contributionDeleted"
        />

        <contribution-user-list
          class="mt-5"
          title="Contribution unpaid"
          :users="membersUnpaid"
          :contributions="contributions"
          :disabled="!selectedPeriodId"
          :contribution-period-id="selectedPeriodId"
          @update:contribution="contributionAddedOrUpdated"
          @delete:contribution="contributionDeleted"
        />
      </div>
    </div>
  </v-main>
</template>

<script lang="ts" setup>
import {onMounted, ref, watch} from "vue"
import TopBanner from "@/components/banners/TopBanner.vue"
import ContributionPeriodList from "@/components/contribution-period/ContributionPeriodList.vue"
import ContributionUserList from "@/components/user/ContributionUserList.vue"

import {
  type AdvancedUser,
  type Contribution,
  findContributionsByPeriodId,
  findMemberships,
  findUsers,
  type Membership,
} from "@/lib"

const users = ref<AdvancedUser[]>([])
const memberships = ref<Membership[]>([])
const contributions = ref<Contribution[]>([])

const membersPaid = ref<AdvancedUser[]>([])
const membersUnpaid = ref<AdvancedUser[]>([])

const search = ref("")
const selectedPeriodId = ref<number>(0)

if ("scrollRestoration" in window.history) {
  window.history.scrollRestoration = "manual"
}

const getUsers = async () => {
  const response = await findUsers()
  if (response.status === 200) {
    users.value = response.data?.content ?? []
  } else {
    console.log(response.error)
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

const isSearched = (user: AdvancedUser) => {
  if (!search.value) return true
  const searchTerms = search.value.toLowerCase().split(" ").filter(Boolean)
  const userValues = Object.values(user ?? {})
    .filter(Boolean)
    .map((v) => String(v).toLowerCase())
  return searchTerms.every((term) => userValues.some((value) => value.includes(term)))
}

const hasContribution = (userId: number) =>
  !!contributions.value.find(
    (c) => c.userId === userId && c.contributionPeriodId === selectedPeriodId.value,
  )

const updateLists = () => {
  const searched = users.value.filter((u) => isSearched(u))
  membersPaid.value = searched.filter((u) => hasContribution(u.id!))
  membersUnpaid.value = searched.filter((u) => !hasContribution(u.id!))
}

watch([contributions, memberships, users, selectedPeriodId, search], () => {
  updateLists()
}, {deep: true})

const contributionAddedOrUpdated = (updated: Contribution) => {
  const idx = contributions.value.findIndex((c) => c.id === updated.id)
  if (idx !== -1) {
    contributions.value.splice(idx, 1, updated)
  } else {
    contributions.value.push(updated)
  }
}

const contributionDeleted = (id: number) => {
  contributions.value = contributions.value.filter((c) => c.id !== id)
}

const selectedPeriodIdChanged = async (periodId: number) => {
  if (!periodId) return
  selectedPeriodId.value = periodId
  const resp = await findContributionsByPeriodId({path: {periodId}})
  contributions.value = resp.data ?? []
}

// Lifecycle
onMounted(async () => {
  try {
    await Promise.all([getUsers(), getMemberships()])
  } catch (error) {
    console.error("Error fetching data:", error)
  }
})
</script>

<style lang="scss">
span {
  font-weight: bold;
}
</style>
