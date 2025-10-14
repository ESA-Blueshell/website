<template>
  <v-main>
    <top-banner title="Member Manager" />

    <div class="mx-3">
      <div
        class="mx-auto my-10"
        style="max-width: 800px"
      >
        <contribution-period-list
          @update:contribution-period="contributionPeriodChanged"
        />

        <v-text-field
          v-model="search"
          label="Search for a user"
        />

        <member-user-list
          :users="nonMembers"
          :memberships="memberships"
          :contributions="contributions"
          :expanded="expanded"
          title="Non-members"
          enable-delete
          @update:membership="membershipChanged"
          @update:expanded="toggleExpanded"
          @update:user="userChanged"
          @delete:user="deleteUser"
        />

        <member-user-list
          :users="members"
          :memberships="memberships"
          :contributions="contributions"
          :expanded="expanded"
          class="mt-5"
          title="Members"
          allow-create
          @update:membership="membershipChanged"
          @update:expanded="toggleExpanded"
          @update:user="userChanged"
          @delete:user="deleteUser"
        />
      </div>
    </div>
  </v-main>
</template>

<script lang="ts" setup>
import {onMounted, ref, watch} from "vue"
import TopBanner from "@/components/banners/TopBanner.vue"
import MemberUserList from "@/components/user/MemberUserList.vue"

import {
  type AdvancedUser,
  type Contribution,
  type ContributionPeriod,
  findContributionsByPeriodId,
  findMemberships, findUserById,
  findUsers,
  type Membership,
} from "@/lib"
import ContributionPeriodList from "@/components/contribution-period/ContributionPeriodList.vue"

const members = ref<AdvancedUser[]>([])
const nonMembers = ref<AdvancedUser[]>([])
const users = ref<AdvancedUser[]>([])
const memberships = ref<Membership[]>([])
const contributions = ref<Contribution[]>([])

const selectedPeriodId = ref<number>(0)
const contributionPeriod = ref<ContributionPeriod | undefined>()
const expanded = ref<number>(0)
const search = ref("")

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

const isSearched = (user: AdvancedUser) => {
  if (!search.value) return true
  const searchTerms = search.value.toLowerCase().split(" ").filter(Boolean)
  const userValues = Object.values(user ?? {})
    .filter(Boolean)
    .map((v) => String(v).toLowerCase())
  return searchTerms.every((term) => userValues.some((value) => value.includes(term)))
}

const updateLists = () => {
  const hasActiveMembership = (u: AdvancedUser) => memberships.value.some((m) => m.userId === u.id && !m.endDate)

  const filtered = users.value.filter((v) => isSearched(v))
  members.value = filtered.filter((u) => hasActiveMembership(u))
  nonMembers.value = filtered.filter((u) => !hasActiveMembership(u))
}

watch([memberships, users, search], () => updateLists(), {deep: true})

const deleteUser = (user: AdvancedUser) => {
  users.value = users.value.filter((u) => u.id !== user.id)
}

const toggleExpanded = (userId: number) => {
  expanded.value = userId === expanded.value ? 0 : userId
}

const userChanged = (user: AdvancedUser) => {
  const index = users.value.findIndex((u) => u.id === user.id)
  if (index !== -1) {
    users.value.splice(index, 1, user)
  } else {
    users.value.push(user)
  }
}

const membershipChanged = async (updatedMembership: Membership) => {
  const index = memberships.value.findIndex((m) => m.id === updatedMembership.id)
  if (index !== -1) {
    memberships.value.splice(index, 1, updatedMembership)
  } else {
    memberships.value.push(updatedMembership)
  }
  const resp = await findUserById({path: {userId: updatedMembership.userId!}})
  if (resp.data) userChanged(resp.data)
}

const contributionPeriodChanged = async (newPeriod: ContributionPeriod) => {
  if (!newPeriod) return
  contributionPeriod.value = newPeriod
  selectedPeriodId.value = newPeriod.id as number
  const [membershipsResp, contributionsResp ] = await Promise.all([
      findMemberships({query: {from: newPeriod.startDate, to: newPeriod.endDate}}),
      findContributionsByPeriodId({path: {periodId: newPeriod.id as number}}),
    ],
  )

  memberships.value = membershipsResp.data ?? []
  contributions.value = contributionsResp.data ?? []
}

onMounted(async () => {
  try {
    await getUsers()
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
