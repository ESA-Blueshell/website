<script lang="ts" setup>
import {computed} from "vue"
import BaseModal from "@/components/common/modals/BaseModal.vue"
import {useTableSort} from "@/composables/useTableSort"
import type {SubmitState} from "@/composables/formUtils"
import type {BulkActionCounts, BulkRow} from "@/utils/bulkRow"
import {memberTypeLabel} from "@/utils/memberType"
import {
  dispositionColor,
  dispositionLabel,
  effectiveDisposition,
  formatMemberSince,
  reasonLabel,
  rowColorClass,
} from "@/utils/bulkDisposition"

/**
 * Shared modal shell for every per-action bulk dialog. Renders the title/confirm chrome
 * (via BaseModal), the counts summary bar, and a sortable preview table. It knows nothing
 * about action type: the dialog hands it rows + state, and supplies extra columns via the
 * `#extra-head` / `#extra-cell` slots (fee selector) and drives re-include through the
 * `reincludeOverrides` v-model. See docs/proposals/bulk-actions/REDESIGN.md §5.1.
 */

defineOptions({name: "BulkDialogScaffold"})

interface Props {
  modelValue: boolean
  title: string
  icon: string
  confirmLabel: string
  rows: BulkRow[]
  counts: BulkActionCounts
  includedCount: number
  reincludeOverrides: Record<number, boolean>
  submitting?: boolean
  canConfirm?: boolean
  submitState?: SubmitState
  showSubmitStatus?: boolean
  /** Whether to render the fee-type column (reminder / incasso only). */
  showFeeColumn?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  submitting: false,
  canConfirm: true,
  submitState: "idle",
  showSubmitStatus: false,
  showFeeColumn: false,
})

const emit = defineEmits<{
  (e: "update:modelValue", value: boolean): void
  (e: "update:reincludeOverrides", value: Record<number, boolean>): void
  (e: "confirm"): void
  (e: "cancel"): void
}>()

const open = computed({
  get: () => props.modelValue,
  set: (val) => emit("update:modelValue", val),
})

const rowsRef = computed(() => props.rows)

type BulkSortKey = "name" | "memberType" | "disposition" | "memberSince" | "amount"

const comparators: Record<BulkSortKey, (a: BulkRow, b: BulkRow) => number> = {
  name: (a, b) => a.name.localeCompare(b.name),
  memberType: (a, b) => (a.memberType ?? "").localeCompare(b.memberType ?? ""),
  disposition: (a, b) => {
    const order: Record<string, number> = {INCLUDED: 0, WARNING: 1, EXCLUDED: 2, SKIPPED: 3}
    return (order[a.disposition] ?? 4) - (order[b.disposition] ?? 4)
  },
  memberSince: (a, b) => (a.memberSince ?? "").localeCompare(b.memberSince ?? ""),
  amount: (a, b) => (a.amount ?? 0) - (b.amount ?? 0),
}

const {sortedItems: sortedRows, toggleSort, sortIcon, ariaSort} = useTableSort(rowsRef, comparators)

const hasReincludable = computed(() => props.rows.some((r) => r.disposition === "WARNING"))

function effective(row: BulkRow) {
  return effectiveDisposition(row, props.reincludeOverrides)
}

function setReinclude(userId: number, value: boolean) {
  emit("update:reincludeOverrides", {...props.reincludeOverrides, [userId]: value})
}
</script>

<template>
  <base-modal
    v-model="open"
    :save-disabled="!canConfirm || submitting"
    :save-icon="icon"
    :save-label="confirmLabel"
    :save-loading="submitting"
    :save-show-status="showSubmitStatus"
    :save-submit-state="submitState"
    :title="title"
    data-testid="bulk-action-dialog"
    max-width="960"
    save-testid="bulk-action-confirm-btn"
    scrollable
    show-cancel
    show-save
    @cancel="emit('cancel')"
    @save="emit('confirm')"
  >
    <!-- Action-specific form inputs (dates, cutoff, validation messages). -->
    <slot name="form" />

    <!-- Counts summary -->
      <div
        class="bulk-counts mb-4"
        data-testid="bulk-action-counts"
      >
        <v-chip
          class="mr-2"
          color="primary"
          size="small"
          variant="tonal"
        >
          {{ counts.selected }} selected
        </v-chip>
        <v-chip
          class="mr-2"
          color="success"
          size="small"
          variant="tonal"
        >
          {{ includedCount }} will apply
        </v-chip>
        <v-chip
          v-if="counts.warned > 0"
          class="mr-2"
          color="warning"
          size="small"
          variant="tonal"
        >
          {{ counts.warned }} with warnings
        </v-chip>
        <v-chip
          v-if="counts.excluded > 0"
          class="mr-2"
          color="error"
          size="small"
          variant="tonal"
        >
          {{ counts.excluded }} excluded
        </v-chip>
        <v-chip
          v-if="counts.skipped > 0"
          class="mr-2"
          color="grey"
          size="small"
          variant="tonal"
        >
          {{ counts.skipped }} skipped
        </v-chip>
      </div>

      <v-table
        density="compact"
        data-testid="bulk-action-preview-table"
      >
        <thead>
          <tr>
            <th
              class="sortable-header"
              role="button"
              tabindex="0"
              :aria-sort="ariaSort('name')"
              @click="toggleSort('name')"
              @keydown.enter="toggleSort('name')"
              @keydown.space.prevent="toggleSort('name')"
            >
              Member
              <v-icon
                :icon="sortIcon('name')"
                size="16"
              />
            </th>
            <th
              class="sortable-header"
              role="button"
              tabindex="0"
              :aria-sort="ariaSort('memberType')"
              @click="toggleSort('memberType')"
              @keydown.enter="toggleSort('memberType')"
              @keydown.space.prevent="toggleSort('memberType')"
            >
              Type
              <v-icon
                :icon="sortIcon('memberType')"
                size="16"
              />
            </th>
            <th
              class="sortable-header"
              role="button"
              tabindex="0"
              :aria-sort="ariaSort('disposition')"
              @click="toggleSort('disposition')"
              @keydown.enter="toggleSort('disposition')"
              @keydown.space.prevent="toggleSort('disposition')"
            >
              Status
              <v-icon
                :icon="sortIcon('disposition')"
                size="16"
              />
            </th>
            <th
              class="sortable-header"
              role="button"
              tabindex="0"
              :aria-sort="ariaSort('memberSince')"
              @click="toggleSort('memberSince')"
              @keydown.enter="toggleSort('memberSince')"
              @keydown.space.prevent="toggleSort('memberSince')"
            >
              Member since
              <v-icon
                :icon="sortIcon('memberSince')"
                size="16"
              />
            </th>
            <th v-if="showFeeColumn">
              Fee type
            </th>
            <th>Note</th>
            <th v-if="hasReincludable">
              Include
            </th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="row in sortedRows"
            :key="row.userId"
            :class="rowColorClass(effective(row))"
            :data-testid="`bulk-preview-row-${row.userId}`"
          >
            <td class="font-weight-medium">
              {{ row.name }}
            </td>
            <td class="text-caption text-medium-emphasis">
              {{ memberTypeLabel(row.memberType) }}
            </td>
            <td>
              <v-chip
                :color="dispositionColor(effective(row))"
                :data-testid="`bulk-preview-disposition-${row.userId}`"
                size="x-small"
                variant="tonal"
              >
                {{ dispositionLabel(effective(row)) }}
              </v-chip>
            </td>
            <td
              class="text-caption text-medium-emphasis"
              :data-testid="`bulk-preview-member-since-${row.userId}`"
            >
              {{ formatMemberSince(row.memberSince) }}
            </td>
            <td v-if="showFeeColumn">
              <slot
                name="fee-cell"
                :row="row"
              />
            </td>
            <td
              class="text-caption"
              :data-testid="`bulk-preview-note-${row.userId}`"
            >
              <span
                v-if="reasonLabel(row.reason)"
                :class="row.disposition === 'EXCLUDED' ? 'text-error' : row.disposition === 'WARNING' ? 'text-warning' : ''"
              >
                {{ reasonLabel(row.reason) }}
              </span>
              <span
                v-if="row.lastSentOn"
                class="text-medium-emphasis ml-1"
              >
                Last sent {{ row.lastSentOn }}
              </span>
            </td>
            <td v-if="hasReincludable">
              <v-checkbox
                v-if="row.disposition === 'WARNING'"
                :data-testid="`bulk-preview-reinclude-${row.userId}`"
                :model-value="reincludeOverrides[row.userId] ?? false"
                color="primary"
                density="compact"
                @update:model-value="(v) => setReinclude(row.userId, !!v)"
              />
            </td>
          </tr>
        </tbody>
      </v-table>
  </base-modal>
</template>

<style lang="scss" scoped>
.bulk-row--excluded td {
  background-color: rgba(var(--v-theme-error), 0.08);
  color: rgb(var(--v-theme-error));
}

.bulk-row--warning td {
  background-color: rgba(var(--v-theme-warning), 0.08);
}

.bulk-row--skipped td {
  opacity: 0.55;
}

.sortable-header {
  cursor: pointer;
  user-select: none;
  white-space: nowrap;

  &:hover {
    background-color: rgba(var(--v-theme-on-surface), 0.04);
  }

  &:focus {
    outline: 2px solid var(--v-theme-primary);
    outline-offset: -2px;
  }
}
</style>
