<script lang="ts" setup>
/* Acting on many members at once, as a page: who it concerns, what will happen to each in plain
   words, warnings for anything unusual, then one button. Nothing changes before that button. */
import {computed, onMounted, ref} from "vue"
import {useRoute, useRouter} from "vue-router"
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
  <div
    class="bulk-task"
    data-testid="bulk-task"
  >
    <router-link
      class="bulk-task__back"
      :to="back"
    >
      Back
    </router-link>

    <p
      v-if="ids.length === 0"
      class="bulk-task__note"
      data-testid="bulk-task-empty"
    >
      Nobody is selected. Pick the people on the list first.
    </p>

    <template v-else-if="loaded">
      <membership-status-dialog
        v-if="action === 'start' || action === 'end'"
        v-model="open"
        inline
        :target-state="action"
        :targets="targets"
        @done="leave"
        @update:model-value="leave"
      />
      <paid-status-dialog
        v-else
        v-model="open"
        :contribution-period-id="periodId"
        inline
        :target-state="action"
        :targets="targets"
        @done="leave"
        @update:model-value="leave"
      />
    </template>
  </div>
</template>

<style scoped>
.bulk-task {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  padding: 2rem 2.4rem 3rem;
}

.bulk-task__back {
  align-self: flex-start;
  font-size: 0.84rem;
  color: var(--color-brand);
}

.bulk-task__note {
  margin: 0;
  color: var(--color-ash);
}

@media (max-width: 839px) {
  .bulk-task {
    padding: 1.2rem 1.1rem 2rem;
  }
}
</style>
