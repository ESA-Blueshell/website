<template>
  <v-main>
    <top-banner title="Member Manager" />

    <div class="mx-3">
      <div
        class="mx-auto my-3"
        style="max-width: 800px"
      >
        <contribution-period-list
          @update:contribution-period="contributionPeriodChanged"
        />

        <member-user-list
          :contributions="contributions"
          :expanded="expanded"
          :memberships="memberships"
          :users="nonMembers"
          allow-create
          enable-delete
          class="mt-3"
          title="Non-members"
          @update:membership="membershipChanged"
          @update:expanded="toggleExpanded"
          @update:user="userChanged"
          @delete:user="deleteUser"
        />

        <member-user-list
          :contributions="contributions"
          :expanded="expanded"
          :memberships="memberships"
          :users="members"
          class="mt-3"
          title="Members"
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
import TopBanner from "@/components/common/banners/TopBanner.vue"
import MemberUserList from "@/components/common/lists/MemberUserList.vue"
import ContributionPeriodList from "@/components/common/lists/ContributionPeriodList.vue"

import {
  type AdvancedUser,
  type Contribution,
  type ContributionPeriod,
  findContributionsByPeriodId,
  findMemberships,
  findUserById,
  findUsers,
  type Membership,
} from "@/services/api"

const members = ref<AdvancedUser[]>([])
const nonMembers = ref<AdvancedUser[]>([])
const users = ref<AdvancedUser[]>([])
const memberships = ref<Membership[]>([])
const contributions = ref<Contribution[]>([])

const selectedPeriodId = ref<number>(0)
const contributionPeriod = ref<ContributionPeriod | undefined>()
const expanded = ref<number>(0)

if ("scrollRestoration" in globalThis.history) {
  globalThis.history.scrollRestoration = "manual"
}

const getUsers = async () => {
  const response = await findUsers()
  if (response.status === 200) users.value = response.data?.content ?? []
  else console.log(response.error)
}

const updateLists = () => {
  const hasActiveMembership = (u: AdvancedUser) => memberships.value.some((m) => m.userId === u.id && !m.endDate)
  const all = users.value
  members.value = all.filter((u) => hasActiveMembership(u))
  nonMembers.value = all.filter((u) => !hasActiveMembership(u))
}

watch([memberships, users], updateLists, {deep: true})

const deleteUser = (user: AdvancedUser) => {
  users.value = users.value.filter((u) => u.id !== user.id)
}

const toggleExpanded = (userId: number) => {
  expanded.value = userId === expanded.value ? 0 : userId
}

const userChanged = (user: AdvancedUser) => {
  const index = users.value.findIndex((u) => u.id === user.id)
  if (index === -1) users.value.push(user)
  else users.value.splice(index, 1, user)
}

const membershipChanged = async (updatedMembership: Membership) => {
  const index = memberships.value.findIndex((m) => m.id === updatedMembership.id)
  if (index === -1) memberships.value.push(updatedMembership)
  else memberships.value.splice(index, 1, updatedMembership)

  const resp = await findUserById({path: {userId: updatedMembership.userId!}})
  if (resp.data) userChanged(resp.data)
}

const contributionPeriodChanged = async (newPeriod: ContributionPeriod) => {
  if (!newPeriod) return
  contributionPeriod.value = newPeriod
  selectedPeriodId.value = newPeriod.id as number
  const [membershipsResp, contributionsResp] = await Promise.all([
    findMemberships({query: {from: newPeriod.startDate, to: newPeriod.endDate}}),
    findContributionsByPeriodId({path: {periodId: newPeriod.id as number}}),
  ])
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
