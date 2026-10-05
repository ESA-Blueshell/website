<script lang="ts" setup>
/* The reader's own contributions: each period they were a member in, what it cost and whether it
   is paid, so they can keep track without asking the treasurer. */
import {onMounted, ref} from "vue"
import SectionTitle from "@/components/account/SectionTitle.vue"
import StateMark from "@/components/island/StateMark.vue"
import ManagementTable, {type TableColumn} from "@/components/management/ManagementTable.vue"
import {type MemberPeriodContribution, listOwnContributions} from "@/domains/contribution"
import {feeTypeLabels} from "@/utils/feePreview"
import {formatDay, periodName} from "@/utils/timestamps"

defineOptions({name: "OwnContributions"})

const COLUMNS: TableColumn<MemberPeriodContribution>[] = [
  {key: "period", label: "Period", sortBy: (row) => row.startDate},
  {key: "fee", label: "Fee", sortBy: (row) => (row.feeType && row.fee != null ? feeTypeLabels[row.feeType] : null)},
  {key: "amount", label: "Amount", sortBy: (row) => row.fee},
  {key: "paid", label: "Paid", sortBy: (row) => (row.fee == null ? null : row.paid)},
]

const periods = ref<MemberPeriodContribution[]>([])
const loaded = ref(false)

onMounted(async () => {
  periods.value = await listOwnContributions()
  loaded.value = true
})
</script>

<template>
  <section
    v-if="loaded"
    class="own-contributions"
    data-testid="own-contributions"
  >
    <section-title>Your contributions</section-title>
    <p
      v-if="periods.length === 0"
      class="own-contributions__note"
      data-testid="own-contributions-none"
    >
      You have no contributions yet. They show here from your first period as a member.
    </p>
    <management-table
      v-else
      :columns="COLUMNS"
      :row-key="(period) => period.periodId"
      :row-testid="(period) => `own-contribution-${period.periodId}`"
      :rows="periods"
    >
      <template #period="{row}">
        <span class="mg-name">{{ periodName(row) }}</span>
        <span class="mg-sub">{{ formatDay(row.startDate) }} to {{ formatDay(row.endDate) }}</span>
      </template>
      <template #fee="{row}">
        {{ row.feeType && row.fee != null ? feeTypeLabels[row.feeType] : "Nothing to pay" }}
      </template>
      <template #amount="{row}">
        <span :class="{'mg-quiet': row.fee == null}">{{ row.fee != null ? `€ ${row.fee.toFixed(2)}` : "·" }}</span>
      </template>
      <template #paid="{row}">
        <state-mark
          v-if="row.fee != null"
          :kind="row.paid ? 'in-step' : 'extra'"
        >
          {{ row.paid ? `Paid${row.paidAt ? ` ${formatDay(row.paidAt)}` : ""}` : "Not paid yet" }}
        </state-mark>
      </template>
    </management-table>
  </section>
</template>

<style scoped>
.own-contributions {
  display: flex;
  flex-direction: column;
  gap: 0.8rem;
}

.own-contributions__note {
  color: var(--color-ash);
}
</style>
