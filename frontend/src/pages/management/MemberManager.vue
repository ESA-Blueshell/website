<script lang="ts" setup>
import {computed, onMounted, ref} from "vue"
import TopBanner from "@/components/common/banners/TopBanner.vue"
import MemberUserList from "@/components/common/lists/MemberUserList.vue"
import ContributionPeriodList from "@/components/common/lists/ContributionPeriodList.vue"

import {
  type ContributionPeriodResponse,
  type ContributionResponse,
  type CreateUserRequest,
  findContributionsByPeriodId,
  findMemberships,
  findUserById,
  findUsers,
  type MembershipResponse,
  type UserDetailResponse,
} from "@/services/api"

defineOptions({name: "MemberManagerPage"})

const users = ref<Array<CreateUserRequest & Partial<UserDetailResponse>>>([])
const memberships = ref<MembershipResponse[]>([])
const contributions = ref<ContributionResponse[]>([])

const selectedPeriodId = ref<number>(0)
const contributionPeriod = ref<ContributionPeriodResponse | undefined>()
const expanded = ref<number>(0)

if ("scrollRestoration" in globalThis.history) {
  globalThis.history.scrollRestoration = "manual"
}

const getUsers = async () => {
  const response = await findUsers()
  if (response.status === 200) users.value = response.data?.content ?? []
  else console.log(response.error)
}

const hasActiveMembership = (u: CreateUserRequest & Partial<UserDetailResponse>) =>
  memberships.value.some((m) => m.userId === u.id && !m.endDate)

const members = computed<Array<CreateUserRequest & Partial<UserDetailResponse>>>(() => users.value.filter((u) => hasActiveMembership(u)))
const nonMembers = computed<Array<CreateUserRequest & Partial<UserDetailResponse>>>(() => users.value.filter((u) => !hasActiveMembership(u)))

const deleteUser = (user: CreateUserRequest & Partial<UserDetailResponse>) => {
  users.value = users.value.filter((u) => u.id !== user.id)
}

const updateUser = (user: CreateUserRequest & Partial<UserDetailResponse>) => {
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


const membershipChanged = async (updatedMembership: MembershipResponse) => {
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


const membershipsByUserId = computed<Record<number, MembershipResponse>>(() => {
  const map: Record<number, MembershipResponse> = {}
  memberships.value?.forEach((m) => map[m.userId] = m)
  return map
})

const contributionsByUserId = computed<Record<number, ContributionResponse>>(() => {
  const map: Record<number, ContributionResponse> = {}
  contributions.value?.forEach((c) => map[c.userId] = c)
  return map
})


const contributionPeriodChanged = async (newPeriod: ContributionPeriodResponse) => {
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
          :contributions-by-user-id="contributionsByUserId"
          :memberships-by-user-id="membershipsByUserId"
          panel-key="non-members"
          :users="nonMembers"
          allow-create
          class="mt-3"
          enable-delete
          title="Non-members"
          @update:membership="membershipChanged"
          @update:user="updateUser"
          @delete:user="deleteUser"
        />

        <member-user-list
          v-model:expanded="expanded"
          :contributions-by-user-id="contributionsByUserId"
          :memberships-by-user-id="membershipsByUserId"
          panel-key="members"
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
