<script lang="ts" setup>
import {computed} from "vue"
import {useNarrowLayout} from "@/composables/useNarrowLayout"
import {useTableSort} from "@/composables/useTableSort"
import {formatBulkDate, reasonLabel, rowColorClass} from "@/utils/bulkDisposition"
import type {BulkRow} from "@/utils/bulkRow"
import {isSelectable, lastAskedOn} from "@/utils/contributionEmail"
import {memberTypeLabel} from "@/utils/memberType"

/**
 * Step 1 — who the batch writes to.
 *
 * The Send-to box is the selection. A member the api warns about starts unticked and a
 * member it cannot reach has no box, so the reason column is the only thing left to read.
 */
defineOptions({name: "PaymentEmailMembersStep"})
const {narrow} = useNarrowLayout()

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
  // Ascending puts the never-asked first: no date is the strongest reason to include somebody.
  lastAskedOn: (a: BulkRow, b: BulkRow) =>
    (lastAskedOn(a) ?? "").localeCompare(lastAskedOn(b) ?? ""),
})

const columns = [
  {key: "name", header: "Member"},
  {key: "memberType", header: "Type"},
  {key: "memberSince", header: "Member since"},
  {key: "lastAskedOn", header: "Last payment email"},
] as const

function setSendTo(userId: number, value: boolean) {
  emit("update:sendTo", {...props.sendTo, [userId]: value})
}

function cellFor(row: BulkRow, key: (typeof columns)[number]["key"]): string {
  if (key === "name") return row.name
  if (key === "memberType") return memberTypeLabel(row.memberType)
  if (key === "memberSince") return formatBulkDate(row.memberSince)
  return formatBulkDate(lastAskedOn(row))
}
</script>

<template>
  <v-table
    v-if="!narrow"
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
          :data-testid="col.key === 'lastAskedOn' ? `payment-emails-last-ask-${row.userId}` : undefined"
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

  <!-- Below lg the columns do not fit, and a clipped Reason is worse than a taller row. -->
  <v-list
    v-else
    data-testid="payment-emails-members-list"
    density="compact"
  >
    <template
      v-for="(row, index) in sortedItems"
      :key="row.userId"
    >
      <v-list-item
        :class="[rowColorClass(row.disposition), refusals[row.userId] ? 'bulk-row--refused' : '']"
        :data-testid="`payment-emails-row-${row.userId}`"
      >
        <template #prepend>
          <v-checkbox-btn
            v-if="isSelectable(row)"
            :aria-label="`Send to ${row.name}`"
            color="primary"
            :data-testid="`payment-emails-send-to-${row.userId}`"
            density="compact"
            :model-value="sendTo[row.userId] ?? false"
            @update:model-value="(v) => setSendTo(row.userId, !!v)"
          />
          <!-- Holds the name's alignment for a member with no box to tick. -->
          <div
            v-else
            class="payment-email-list__spacer"
          />
        </template>

        <v-list-item-title class="font-weight-medium text-truncate">
          {{ row.name }}
        </v-list-item-title>

        <v-list-item-subtitle class="text-caption">
          {{ memberTypeLabel(row.memberType) }}
          <span class="mx-1">·</span>
          since {{ formatBulkDate(row.memberSince) }}
        </v-list-item-subtitle>

        <!-- Named in full: there is no column header here to say what the date is. -->
        <div class="text-caption text-medium-emphasis">
          Last payment email
          <span :data-testid="`payment-emails-last-ask-${row.userId}`">{{
            formatBulkDate(lastAskedOn(row))
          }}</span>
        </div>

        <div
          v-if="refusals[row.userId]"
          class="text-caption text-error"
          :data-testid="`payment-emails-refusal-${row.userId}`"
        >
          {{ refusals[row.userId] }}
        </div>
        <div
          v-else-if="row.reason"
          class="text-caption"
          :class="row.disposition === 'EXCLUDED' ? 'text-error' : 'text-warning'"
          :data-testid="`payment-emails-reason-${row.userId}`"
        >
          {{ reasonLabel(row.reason) }}
        </div>
      </v-list-item>
      <v-divider v-if="index < sortedItems.length - 1" />
    </template>
  </v-list>
</template>

<style lang="scss" scoped>
@use '@/styles/payment-email' as paymentEmail;

@include paymentEmail.sticky-table-header;

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

// Matches the checkbox's footprint, so named rows line up with tickable ones.
.payment-email-list__spacer {
  width: 40px;
}

.sortable-header {
  cursor: pointer;
  user-select: none;
  white-space: nowrap;
}
</style>
