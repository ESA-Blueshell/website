<script lang="ts" setup>
import {computed} from "vue"
import {useTableSort} from "@/composables/useTableSort"
import {formatBulkDate, reasonLabel, rowColorClass} from "@/utils/bulkDisposition"
import type {BulkRow} from "@/utils/bulkRow"
import {isSelectable} from "@/utils/contributionEmail"
import {memberTypeLabel} from "@/utils/memberType"

/**
 * Step 1 — who the batch writes to.
 *
 * The Send-to box is the selection. A member the api warns about starts unticked and a
 * member it cannot reach has no box, so the reason column is the only thing left to read.
 */
defineOptions({name: "PaymentEmailMembersStep"})

const props = defineProps<{
  rows: BulkRow[]
  sendTo: Record<number, boolean>
  /** Rows the api refused, by the sentence it refused them with. */
  refusals: Record<number, string>
}>()

const emit = defineEmits<{
  (e: "update:sendTo", value: Record<number, boolean>): void
}>()

const rowsRef = computed(() => props.rows)

const {sortedItems, toggleSort, sortIcon, ariaSort} = useTableSort(rowsRef, {
  name: (a: BulkRow, b: BulkRow) => a.name.localeCompare(b.name),
  memberType: (a: BulkRow, b: BulkRow) => (a.memberType ?? "").localeCompare(b.memberType ?? ""),
  memberSince: (a: BulkRow, b: BulkRow) => (a.memberSince ?? "").localeCompare(b.memberSince ?? ""),
})

const columns = [
  {key: "name", header: "Member"},
  {key: "memberType", header: "Type"},
  {key: "memberSince", header: "Member since"},
] as const

function setSendTo(userId: number, value: boolean) {
  emit("update:sendTo", {...props.sendTo, [userId]: value})
}

function cellFor(row: BulkRow, key: (typeof columns)[number]["key"]): string {
  if (key === "name") return row.name
  if (key === "memberType") return memberTypeLabel(row.memberType)
  return formatBulkDate(row.memberSince)
}
</script>

<template>
  <v-table
    class="payment-email-table"
    data-testid="payment-emails-members-table"
    density="compact"
  >
    <thead>
      <tr>
        <th>Send to</th>
        <th
          v-for="col in columns"
          :key="col.key"
          :aria-sort="ariaSort(col.key)"
          class="sortable-header"
          role="button"
          tabindex="0"
          @click="toggleSort(col.key)"
          @keydown.enter="toggleSort(col.key)"
          @keydown.space.prevent="toggleSort(col.key)"
        >
          {{ col.header }}
          <v-icon
            :icon="sortIcon(col.key)"
            size="16"
          />
        </th>
        <th>Reason</th>
      </tr>
    </thead>
    <tbody>
      <tr
        v-for="row in sortedItems"
        :key="row.userId"
        :class="[rowColorClass(row.disposition), refusals[row.userId] ? 'bulk-row--refused' : '']"
        :data-testid="`payment-emails-row-${row.userId}`"
      >
        <td>
          <v-checkbox
            v-if="isSelectable(row)"
            color="primary"
            :data-testid="`payment-emails-send-to-${row.userId}`"
            density="compact"
            hide-details
            :model-value="sendTo[row.userId] ?? false"
            @update:model-value="(v) => setSendTo(row.userId, !!v)"
          />
        </td>
        <td
          v-for="col in columns"
          :key="col.key"
          :class="col.key === 'name' ? 'font-weight-medium' : 'text-caption text-medium-emphasis'"
        >
          {{ cellFor(row, col.key) }}
        </td>
        <td>
          <span
            v-if="refusals[row.userId]"
            class="text-caption text-error"
            :data-testid="`payment-emails-refusal-${row.userId}`"
          >{{ refusals[row.userId] }}</span>
          <span
            v-else-if="row.reason"
            class="text-caption"
            :class="row.disposition === 'EXCLUDED' ? 'text-error' : 'text-warning'"
            :data-testid="`payment-emails-reason-${row.userId}`"
          >{{ reasonLabel(row.reason) }}</span>
          <span
            v-else
            class="text-medium-emphasis"
          >—</span>
        </td>
      </tr>
    </tbody>
  </v-table>
</template>

<style lang="scss" scoped>
.payment-email-table {
  :deep(thead th) {
    position: sticky;
    top: 0;
    z-index: 1;
    background-color: rgb(var(--v-theme-surface));
  }
}

.bulk-row--excluded td {
  background-color: rgba(var(--v-theme-error), 0.08);
  color: rgb(var(--v-theme-error));
}

.bulk-row--warning td {
  background-color: rgba(var(--v-theme-warning), 0.08);
}

// Loud enough to find in a long table: this is the row the send was refused over.
.bulk-row--refused td {
  background-color: rgba(var(--v-theme-error), 0.16);
}

.sortable-header {
  cursor: pointer;
  user-select: none;
  white-space: nowrap;
}
</style>
