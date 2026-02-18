<template>
  <v-main>
    <top-banner title="Contribution Manager" />

    <div class="mx-3">
      <div
        class="mx-auto my-3"
        style="max-width: 800px"
      >
        <contribution-period-list @update:contribution-period="contributionPeriodChanged" />

        <contribution-user-list
          :contribution-period-id="selectedPeriodId"
          :contributions="contributions"
          :disabled="!selectedPeriodId"
          :users="membersPaid"
          class="mt-3"
          title="Contribution paid"
          @update:contribution="contributionAddedOrUpdated"
          @delete:contribution="contributionDeleted"
        />

        <contribution-user-list
          :contribution-period-id="selectedPeriodId"
          :contributions="contributions"
          :disabled="!selectedPeriodId"
          :users="membersUnpaid"
          class="mt-3"
          title="Contribution unpaid"
          @update:contribution="contributionAddedOrUpdated"
          @delete:contribution="contributionDeleted"
        />
      </div>
    </div>
  </v-main>
</template>

<script lang="ts" setup>
import {onMounted, ref, watch} from "vue"
import TopBanner from "@/components/common/banners/TopBanner.vue"
import ContributionPeriodList from "@/components/common/lists/ContributionPeriodList.vue"
import ContributionUserList from "@/components/common/lists/ContributionUserList.vue"

import {
  type ContributionPeriodResponse,
  type ContributionResponse,
  findContributionsByPeriodId,
  findMemberships,
  findUsers,
  type MembershipResponse,
  type UserDetailResponse,
} from "@/services/api"

const users = ref<UserDetailResponse[]>([])
const memberships = ref<MembershipResponse[]>([])
const contributions = ref<ContributionResponse[]>([])

const membersPaid = ref<UserDetailResponse[]>([])
const membersUnpaid = ref<UserDetailResponse[]>([])

const contributionPeriod = ref<ContributionPeriodResponse | undefined>()
const selectedPeriodId = ref<number>(0)

if ("scrollRestoration" in globalThis.history) {
  globalThis.history.scrollRestoration = "manual"
}

const getUsers = async () => {
  const response = await findUsers()
  if (response.status === 200) users.value = response.data?.content ?? []
  else console.log(response.error)
}

const getMemberships = async () => {
  try {
    const response = await findMemberships()
    memberships.value = response.data ?? []
  } catch (error) {
    console.error("Error fetching memberships:", error)
  }
}

const hasActiveMembership = (userId: number) =>
  memberships.value.some((membership) => membership.userId === userId && !membership.endDate)

const hasContribution = (userId: number) =>
  contributions.value.some(
    (c) => c.userId === userId && c.contributionPeriodId === selectedPeriodId.value,
  )

const updateLists = () => {
  const all = users.value.filter((u) => hasActiveMembership(u.id))
  membersPaid.value = all.filter((u) => hasContribution(u.id!))
  membersUnpaid.value = all.filter((u) => !hasContribution(u.id!))
}

watch([contributions, memberships, users, selectedPeriodId], updateLists, {deep: true})

const contributionAddedOrUpdated = (updated: ContributionResponse) => {
  const idx = contributions.value.findIndex(
    (c) => c.userId === updated.userId && c.contributionPeriodId === updated.contributionPeriodId,
  )
  if (idx === -1) contributions.value.push(updated)
  else contributions.value.splice(idx, 1, updated)
}

const contributionDeleted = (userId: number) => {
  contributions.value = contributions.value.filter(
    (c) => !(c.userId === userId && c.contributionPeriodId === selectedPeriodId.value),
  )
}

const contributionPeriodChanged = async (newPeriod: ContributionPeriodResponse) => {
  if (!newPeriod) return
  contributionPeriod.value = newPeriod
  selectedPeriodId.value = newPeriod.id as number
  const resp = await findContributionsByPeriodId({path: {periodId: newPeriod.id as number}})
  contributions.value = resp.data ?? []
}

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
