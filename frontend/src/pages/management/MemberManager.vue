<script lang="ts" setup>
import {computed, onMounted, ref} from "vue"
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

defineOptions({name: "MemberManagerPage"})

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

const hasActiveMembership = (u: AdvancedUser) =>
  memberships.value.some((m) => m.userId === u.id && !m.endDate)

const members = computed<AdvancedUser[]>(() => users.value.filter((u) => hasActiveMembership(u)))
const nonMembers = computed<AdvancedUser[]>(() => users.value.filter((u) => !hasActiveMembership(u)))

const deleteUser = (user: AdvancedUser) => {
  users.value = users.value.filter((u) => u.id !== user.id)
}

const updateUser = (user: AdvancedUser) => {
  const index = users.value.findIndex((u) => u.id === user.id)
  if (index === -1) {
    users.value = [...users.value, user]
  } else {
    users.value = [
      ...users.value.slice(0, index),
      user,
      ...users.value.slice(index + 1),
    ]
  }
  expanded.value = 0
}


const membershipChanged = async (updatedMembership: Membership) => {
  const index = memberships.value.findIndex((m) => m.id === updatedMembership.id)
  if (index === -1) memberships.value = [...memberships.value, updatedMembership]
  else memberships.value = [
    ...memberships.value.slice(0, index),
    updatedMembership,
    ...memberships.value.slice(index + 1),
  ]

  // Adding a membership will change the roles of the user, so it must be re-fetched
  const resp = await findUserById({path: {userId: updatedMembership.userId!}})
  if (resp.data) updateUser(resp.data)
}


const membershipsByUserId = computed<Record<number, Membership>>(() => {
  const map: Record<number, Membership> = {}
  memberships.value?.forEach((m) => map[m.userId] = m)
  return map
})

const contributionsByUserId = computed<Record<number, Contribution>>(() => {
  const map: Record<number, Contribution> = {}
  contributions.value?.forEach((c) => map[c.userId] = c)
  return map
})


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

<template>
  <v-main>
    <top-banner title="Member Manager" />

    <div class="mx-3">
      <div
        class="mx-auto my-3"
        style="max-width: 800px"
      >
        <contribution-period-list @update:contribution-period="contributionPeriodChanged" />

        <member-user-list
          v-model:expanded="expanded"
          :memberships-by-user-id="membershipsByUserId"
          :contributions-by-user-id="contributionsByUserId"
          :users="nonMembers"
          allow-create
          enable-delete
          class="mt-3"
          title="Non-members"
          @update:membership="membershipChanged"
          @update:user="updateUser"
          @delete:user="deleteUser"
        />

        <member-user-list
          v-model:expanded="expanded"
          :memberships-by-user-id="membershipsByUserId"
          :contributions-by-user-id="contributionsByUserId"
          :users="members"
          class="mt-3"
          title="Members"
          @update:membership="membershipChanged"
          @update:user="updateUser"
          @delete:user="deleteUser"
        />
      </div>
    </div>
  </v-main>
</template>

<style lang="scss">
span {
  font-weight: bold;
}
</style>
