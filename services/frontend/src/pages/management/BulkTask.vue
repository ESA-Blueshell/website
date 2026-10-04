<script lang="ts" setup>
/* Acting on many members at once, as a page: who it concerns, what will happen to each in plain
   words, warnings for anything unusual, then one button. Nothing changes before that button. */
import {computed, onMounted, ref} from "vue"
import {useRoute, useRouter} from "vue-router"
import ManagementPage from "@/components/management/ManagementPage.vue"
import MembershipStatusDialog from "@/components/common/modals/bulk/MembershipStatusDialog.vue"
import PaidStatusDialog from "@/components/common/modals/bulk/PaidStatusDialog.vue"
import {listPaidUserIds} from "@/domains/contribution"
import {type MembershipResponse, type UserDetailResponse, listMemberships, listUsers} from "@/domains/user"
import {$handleNetworkError} from "@/plugins/handleNetworkError"
import {computeBulkTargets} from "@/utils/bulkTarget"

defineOptions({name: "BulkTaskPage"})

const route = useRoute()
const router = useRouter()

const action = computed(() => String(route.params.action) as "start" | "end" | "paid" | "unpaid")
const ids = computed(() => String(route.query.ids ?? "").split(",").map(Number).filter((one) => Number.isInteger(one) && one > 0))
const periodId = computed(() => (route.query.period ? Number(route.query.period) : null))
/** Where the selection came from, which is where the page goes once it is done or cancelled. */
const back = computed(() => (typeof route.query.back === "string" && route.query.back.startsWith("/management") ? route.query.back : "/management/users"))

const users = ref<UserDetailResponse[]>([])
const memberships = ref<MembershipResponse[]>([])
const paid = ref<Set<number>>(new Set())
const loaded = ref(false)
const open = ref(true)

const targets = computed(() => {
  const held = new Map<number, MembershipResponse[]>()
  for (const one of memberships.value) held.set(one.userId, [...(held.get(one.userId) ?? []), one])
  return computeBulkTargets(ids.value, held, paid.value, new Map(users.value.map((user) => [user.id, user])))
})

const leave = () => router.push(back.value)

/* What each task is called and what it does, said before anything changes. */
const TASKS: Record<"start" | "end" | "paid" | "unpaid", {title: string; lede: string}> = {
  start: {
    title: "Start membership",
    lede: "Starts a membership for everyone included, from the date shown. People who are already members are skipped. "
      + "Somebody who was a member before gets a new membership, with the member type of their last one. Incasso is set per person afterwards.",
  },
  end: {
    title: "End membership",
    lede: "Ends the running membership of everyone included, on the date shown. People without a running membership are skipped. "
      + "Nothing is deleted: their history stays, and a membership can be started again later.",
  },
  paid: {
    title: "Mark as paid",
    lede: "Records the contribution as paid for everyone included, for this period. People who already paid are skipped, "
      + "and so are honorary members, who owe nothing. No email is sent.",
  },
  unpaid: {
    title: "Mark as unpaid",
    lede: "Removes the recorded payment for everyone included, for this period. People who have not paid are skipped. "
      + "No email is sent and nothing is refunded.",
  },
}
const task = computed(() => TASKS[action.value] ?? TASKS.end)
const backLabel = computed(() => (back.value.startsWith("/management/contributions") ? "Contributions" : "Users"))

onMounted(async () => {
  try {
    const [people, held, payers] = await Promise.all([
      listUsers(),
      listMemberships(),
      periodId.value == null ? Promise.resolve(new Set<number>()) : listPaidUserIds(periodId.value),
    ])
    users.value = people
    memberships.value = held
    paid.value = payers
  } catch (error) {
    $handleNetworkError(error)
  } finally {
    loaded.value = true
  }
})
</script>

<template>
  <management-page
    :back="{to: back, label: backLabel}"
    :eyebrow="action === 'paid' || action === 'unpaid' ? 'Contributions' : 'Members'"
    testid="bulk-task"
    :title="task.title"
  >
    <template #lede>
      {{ task.lede }}
    </template>

    <p
      v-if="ids.length === 0"
      class="bulk-task__note"
      data-testid="bulk-task-empty"
    >
      Nobody is selected. Go back and tick the people first.
    </p>

    <template v-else-if="loaded">
      <membership-status-dialog
        v-if="action === 'start' || action === 'end'"
        v-model="open"
        :target-state="action"
        :targets="targets"
        @done="leave"
        @update:model-value="leave"
      />
      <paid-status-dialog
        v-else
        v-model="open"
        :contribution-period-id="periodId"
        :target-state="action"
        :targets="targets"
        @done="leave"
        @update:model-value="leave"
      />
    </template>
  </management-page>
</template>

<style scoped>
.bulk-task__note {
  margin-top: 1.4rem;
  color: var(--color-ash);
}
</style>
