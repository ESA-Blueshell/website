<script lang="ts" setup>
/* What a bulk task shows before anything changes: how many people it will apply to, each person
   with what will happen to them and why, and the one button that does it. */
import {computed, ref} from "vue"
import CutButton from "@/components/island/CutButton.vue"
import StateMark, {type StateKind} from "@/components/island/StateMark.vue"
import StateTag from "@/components/island/StateTag.vue"
import ManagementRow from "@/components/management/ManagementRow.vue"
import ManagementTable, {type TableColumn} from "@/components/management/ManagementTable.vue"
import RowCheck from "@/components/management/RowCheck.vue"
import type {SubmitState} from "@/composables/formUtils"
import type {BulkActionCounts, BulkDisposition, BulkRow} from "@/utils/bulkRow"
import {memberTypeLabel} from "@/utils/memberType"
import {dispositionLabel, effectiveDisposition, formatBulkDate, reasonLabel} from "@/utils/bulkDisposition"

defineOptions({name: "BulkDialogScaffold"})

interface Props {
  confirmLabel: string
  rows: BulkRow[]
  counts: BulkActionCounts
  includedCount: number
  reincludeOverrides: Record<number, boolean>
  submitting?: boolean
  submitState?: SubmitState
  showSubmitStatus?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  submitting: false,
  submitState: "idle",
  showSubmitStatus: false,
})

const emit = defineEmits<{
  (e: "update:reincludeOverrides", value: Record<number, boolean>): void
  (e: "confirm"): void
  (e: "cancel"): void
}>()

const COLUMNS: TableColumn[] = [
  {key: "name", label: "Member", sortable: true},
  {key: "memberType", label: "Type", sortable: true},
  {key: "disposition", label: "What happens", sortable: true},
  {key: "memberSince", label: "Member since", sortable: true},
  {key: "note", label: "Why", wrap: true},
]

const ORDER: Record<string, number> = {INCLUDED: 0, WARNING: 1, EXCLUDED: 2, SKIPPED: 3}
const comparators: Record<string, (a: BulkRow, b: BulkRow) => number> = {
  name: (a, b) => a.name.localeCompare(b.name),
  memberType: (a, b) => (a.memberType ?? "").localeCompare(b.memberType ?? ""),
  disposition: (a, b) => (ORDER[a.disposition] ?? 4) - (ORDER[b.disposition] ?? 4),
  memberSince: (a, b) => (a.memberSince ?? "").localeCompare(b.memberSince ?? ""),
}

const sortKey = ref("")
const descending = ref(false)
const sortBy = (key: string) => {
  descending.value = sortKey.value === key ? !descending.value : false
  sortKey.value = key
}
const sortedRows = computed(() => {
  const compare = comparators[sortKey.value]
  if (!compare) return props.rows
  const sorted = [...props.rows].sort(compare)
  return descending.value ? sorted.reverse() : sorted
})

const MARKS: Record<BulkDisposition, StateKind> = {INCLUDED: "in-step", WARNING: "extra", EXCLUDED: "not-created", SKIPPED: "not-compared"}

const hasReincludable = computed(() => props.rows.some((row) => row.disposition === "WARNING"))
const effective = (row: BulkRow) => effectiveDisposition(row, props.reincludeOverrides)
/** Ticked back in by the operator. It is included, and it is still the row that was flagged. */
const isForced = (row: BulkRow): boolean => row.disposition === "WARNING" && !!props.reincludeOverrides[row.userId]
const wordOf = (row: BulkRow) => (isForced(row) ? "Included anyway" : dispositionLabel(effective(row)))
const setReinclude = (userId: number, value: boolean) =>
  emit("update:reincludeOverrides", {...props.reincludeOverrides, [userId]: value})

const onSave = () => {
  if (!props.submitting) emit("confirm")
}
</script>

<template>
  <div
    class="bulk"
    data-testid="bulk-action-dialog"
  >
    <div
      v-if="$slots['info-box']"
      class="bulk__info"
    >
      <slot name="info-box" />
    </div>

    <p
      class="bulk__tags"
      data-testid="bulk-action-counts"
    >
      <state-tag tone="quiet">
        {{ counts.selected }} selected
      </state-tag>
      <state-tag tone="ok">
        {{ includedCount }} will apply
      </state-tag>
      <state-tag
        v-if="counts.warned > 0"
        tone="warn"
      >
        {{ counts.warned }} with warnings
      </state-tag>
      <state-tag
        v-if="counts.excluded > 0"
        tone="warn"
      >
        {{ counts.excluded }} excluded
      </state-tag>
      <state-tag
        v-if="counts.skipped > 0"
        tone="quiet"
      >
        {{ counts.skipped }} skipped
      </state-tag>
    </p>

    <management-table
      :columns="hasReincludable ? [...COLUMNS, {key: 'include', label: 'Include anyway'}] : COLUMNS"
      :descending="descending"
      :row-key="(row) => row.userId"
      :row-testid="(row) => `bulk-preview-row-${row.userId}`"
      search-label="Search members"
      :search-text="(row) => row.name"
      :rows="sortedRows"
      :sort-key="sortKey"
      testid="bulk-action-preview-table"
      @sort="sortBy"
    >
      <template #name="{row}">
        <span class="bulk__name">{{ row.name }}</span>
      </template>
      <template #memberType="{row}">
        <span class="mg-quiet">{{ memberTypeLabel(row.memberType) }}</span>
      </template>
      <template #disposition="{row}">
        <state-mark
          :kind="isForced(row) ? 'extra' : MARKS[effective(row)]"
          :testid="`bulk-preview-disposition-${row.userId}`"
        >
          {{ wordOf(row) }}
        </state-mark>
      </template>
      <template #memberSince="{row}">
        <span :data-testid="`bulk-preview-member-since-${row.userId}`">{{ formatBulkDate(row.memberSince) }}</span>
      </template>
      <template #note="{row}">
        <span
          class="mg-quiet"
          :data-testid="`bulk-preview-note-${row.userId}`"
        >{{ reasonLabel(row.reason) }}</span>
      </template>
      <template #include="{row}">
        <row-check
          v-if="row.disposition === 'WARNING'"
          :checked="reincludeOverrides[row.userId] ?? false"
          :label="`Include ${row.name} anyway`"
          :testid="`bulk-preview-reinclude-${row.userId}`"
          @toggle="setReinclude(row.userId, !(reincludeOverrides[row.userId] ?? false))"
        />
      </template>
      <template #phone="{row}">
        <management-row
          :meta="[memberTypeLabel(row.memberType), reasonLabel(row.reason)].filter(Boolean).join(' · ')"
          :name="row.name"
          :testid="`bulk-preview-row-${row.userId}`"
        >
          <template
            v-if="row.disposition === 'WARNING'"
            #check
          >
            <row-check
              :checked="reincludeOverrides[row.userId] ?? false"
              :label="`Include ${row.name} anyway`"
              :testid="`bulk-preview-reinclude-${row.userId}`"
              @toggle="setReinclude(row.userId, !(reincludeOverrides[row.userId] ?? false))"
            />
          </template>
          <state-mark
            :kind="isForced(row) ? 'extra' : MARKS[effective(row)]"
            :testid="`bulk-preview-disposition-${row.userId}`"
          >
            {{ wordOf(row) }}
          </state-mark>
        </management-row>
      </template>
    </management-table>

    <div class="bulk__acts">
      <cut-button
        :disabled="submitting || includedCount === 0"
        testid="bulk-action-confirm-btn"
        tone="solid"
        @click="onSave"
      >
        {{ confirmLabel }}
      </cut-button>
      <cut-button
        testid="bulk-action-cancel-btn"
        tone="quiet"
        @click="emit('cancel')"
      >
        Cancel
      </cut-button>
      <span
        v-if="showSubmitStatus"
        class="bulk__said"
        :class="{'bulk__said--wrong': submitState === 'error'}"
        data-testid="bulk-action-status"
        role="status"
      >{{ submitState === "error" ? "Nothing was changed." : "Done." }}</span>
    </div>
  </div>
</template>

<style scoped>
.bulk {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  padding-top: 1.2rem;
}

.bulk__info {
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
  font-size: 0.92rem;
  color: var(--color-ash);
}

.bulk__tags {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
}

.bulk__name {
  font-size: 0.9rem;
  font-weight: 600;
  color: var(--color-chalk);
}

.bulk__acts {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.6rem;
}

.bulk__said {
  font-size: 0.88rem;
  color: var(--color-ok);
}

.bulk__said--wrong {
  color: var(--color-danger);
}
</style>
