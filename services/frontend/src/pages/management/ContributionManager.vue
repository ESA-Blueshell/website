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
          :period-member-user-ids="periodMemberUserIds"
          :disabled="!selectedPeriodId"
          panel-key="paid"
          :users="usersPaid"
          class="mt-3"
          title="Contribution paid"
          @update:contribution="contributionAddedOrUpdated"
          @delete:contribution="contributionDeleted"
        />

        <contribution-user-list
          :contribution-period-id="selectedPeriodId"
          :contributions="contributions"
          :period-member-user-ids="periodMemberUserIds"
          :disabled="!selectedPeriodId"
          panel-key="unpaid"
          :users="usersUnpaid"
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
import {computed, onMounted, ref} from "vue"
import TopBanner from "@/components/common/banners/TopBanner.vue"
import ContributionPeriodList from "@/components/common/lists/ContributionPeriodList.vue"
import ContributionUserList from "@/components/common/lists/ContributionUserList.vue"

import {
  type ContributionPeriodResponse,
  type ContributionResponse,
  findContributionsByPeriodId,
  findMemberships,
  findUsers,
  type UserDetailResponse,
} from "@/services/api"

const users = ref<UserDetailResponse[]>([])
const contributions = ref<ContributionResponse[]>([])
const periodMemberUserIds = ref<Set<number>>(new Set())

const selectedPeriodId = ref<number>(0)

if ("scrollRestoration" in globalThis.history) {
  globalThis.history.scrollRestoration = "manual"
}

const getUsers = async () => {
  const response = await findUsers()
  if (response.status === 200) users.value = response.data?.content ?? []
  else console.log(response.error)
}

const paidUserIds = computed<Set<number>>(() => {
  const periodId = selectedPeriodId.value
  if (!periodId) return new Set<number>()

  return new Set(
    contributions.value
      .filter((contribution) => contribution.contributionPeriodId === periodId)
      .map((contribution) => contribution.userId),
  )
})

const hasSelectedPeriodContribution = (user: UserDetailResponse) => paidUserIds.value.has(user.id)

const usersPaid = computed<UserDetailResponse[]>(() =>
  users.value.filter(hasSelectedPeriodContribution),
)

const usersUnpaid = computed<UserDetailResponse[]>(() =>
  users.value.filter((user) => !hasSelectedPeriodContribution(user)),
)

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

const contributionPeriodChanged = async (newPeriod?: ContributionPeriodResponse) => {
  const periodId = newPeriod?.id
  if (!periodId) {
    selectedPeriodId.value = 0
    contributions.value = []
    periodMemberUserIds.value = new Set()
    return
  }

  selectedPeriodId.value = periodId
  contributions.value = []
  periodMemberUserIds.value = new Set()

  const [contributionsResp, membershipsResp] = await Promise.all([
    findContributionsByPeriodId({path: {periodId}}),
    findMemberships({query: {from: newPeriod.startDate, to: newPeriod.endDate}}),
  ])

  if (selectedPeriodId.value !== periodId) return

  contributions.value = contributionsResp.data ?? []
  periodMemberUserIds.value = new Set(
    (membershipsResp.data ?? []).map((membership) => membership.userId),
  )
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
</style>
