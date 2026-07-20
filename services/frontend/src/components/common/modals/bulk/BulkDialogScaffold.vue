<script lang="ts" setup>
import {computed, ref} from "vue"
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
 * (via BaseModal), an optional help panel, the counts summary bar, and a sortable,
 * column-driven preview table. It knows nothing about action type: the dialog hands it
 * rows + a `columns` descriptor and supplies custom cell content via `#cell.<key>` slots
 * (falling back to a built-in renderer for the standard keys). Re-include is driven via
 * the `reincludeOverrides` v-model. See docs/proposals/bulk-actions/REDESIGN.md §5.1.
 */

defineOptions({name: "BulkDialogScaffold"})

/** A single column descriptor for the preview table. */
export interface BulkColumn {
  key: string
  header: string
  align?: "start" | "end" | "center"
  sortable?: boolean
  width?: string
}

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
  /** Column descriptors for the preview table. Falls back to the standard set. */
  columns?: BulkColumn[]
  /** Label for the WARNING re-include column (e.g. "Include" or "Forcibly include"). */
  includeLabel?: string
  /** Optional help panel content rendered behind a "?" icon button in the header. */
  help?: {title: string; body: string}
}

const DEFAULT_COLUMNS: BulkColumn[] = [
  {key: "name", header: "Member", sortable: true},
  {key: "memberType", header: "Type", sortable: true},
  {key: "disposition", header: "Status", sortable: true},
  {key: "memberSince", header: "Member since", sortable: true},
  {key: "note", header: "Note"},
]

const props = withDefaults(defineProps<Props>(), {
  submitting: false,
  canConfirm: true,
  submitState: "idle",
  showSubmitStatus: false,
  columns: undefined,
  includeLabel: "Include",
  help: undefined,
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

const columns = computed<BulkColumn[]>(() => props.columns ?? DEFAULT_COLUMNS)

const rowsRef = computed(() => props.rows)

const comparators: Record<string, (a: BulkRow, b: BulkRow) => number> = {
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

/** A column is sortable only if flagged AND a comparator exists for its key. */
function isSortable(col: BulkColumn): boolean {
  return !!col.sortable && !!comparators[col.key]
}

function alignClass(col: BulkColumn): string {
  if (col.align === "end") return "text-end"
  if (col.align === "center") return "text-center"
  return ""
}

function effective(row: BulkRow) {
  return effectiveDisposition(row, props.reincludeOverrides)
}

function setReinclude(userId: number, value: boolean) {
  emit("update:reincludeOverrides", {...props.reincludeOverrides, [userId]: value})
}

// ── Help panel ────────────────────────────────────────────────────────────────
const helpOpen = ref(false)

// ── Validation ──────────────────────────────────────────────────────────────
// The confirm button is ALWAYS clickable (only disabled while submitting); on save we
// validate the wrapped form and only emit `confirm` when it reports valid. Dialogs
// attach :rules to their fields so missing/invalid inputs surface inline.
const formRef = ref<{validate: () => Promise<{valid: boolean}> | {valid: boolean}} | null>(null)

async function onSave() {
  if (props.submitting) return
  const form = formRef.value
  if (form) {
    const result = await form.validate()
    if (!result?.valid) return
  }
  emit("confirm")
}
</script>

<template>
  <base-modal
    v-model="open"
    :save-disabled="submitting"
    :save-icon="icon"
    :save-label="confirmLabel"
    :save-loading="submitting"
    :save-show-status="showSubmitStatus"
    :save-submit-state="submitState"
    :title="title"
    data-testid="bulk-action-dialog"
    max-width="1200"
    save-testid="bulk-action-confirm-btn"
    scrollable
    show-cancel
    show-save
    @cancel="emit('cancel')"
    @save="onSave"
  >
    <!-- Help affordance lives in the header band, next to the title. -->
    <template
      v-if="help"
      #title-append
    >
      <v-btn
        aria-label="Help"
        data-testid="bulk-action-help-btn"
        icon="mdi-help-circle-outline"
        size="small"
        variant="text"
        @click="helpOpen = !helpOpen"
      />
    </template>

    <!--
      Help panel — toggled from the header button. Rendered as the first thing in
      the body so it drops down directly beneath the header band when opened.
    -->
    <v-expand-transition v-if="help">
      <v-alert
        v-if="helpOpen"
        class="mb-4"
        data-testid="bulk-action-help-panel"
        density="comfortable"
        :title="help.title"
        type="info"
        variant="tonal"
      >
        {{ help.body }}
      </v-alert>
    </v-expand-transition>

    <v-form ref="formRef">
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
              v-for="col in columns"
              :key="col.key"
              :aria-sort="isSortable(col) ? ariaSort(col.key) : undefined"
              :class="[alignClass(col), isSortable(col) ? 'sortable-header' : '']"
              :role="isSortable(col) ? 'button' : undefined"
              :style="col.width ? {width: col.width} : undefined"
              :tabindex="isSortable(col) ? 0 : undefined"
              @click="isSortable(col) && toggleSort(col.key)"
              @keydown.enter="isSortable(col) && toggleSort(col.key)"
              @keydown.space.prevent="isSortable(col) && toggleSort(col.key)"
            >
              {{ col.header }}
              <v-icon
                v-if="isSortable(col)"
                :icon="sortIcon(col.key)"
                size="16"
              />
            </th>
            <th v-if="hasReincludable">
              {{ includeLabel }}
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
            <td
              v-for="col in columns"
              :key="col.key"
              :class="[alignClass(col), col.key === 'fee' ? 'bulk-fee-cell' : '']"
            >
              <!-- Dialog-supplied custom cell. -->
              <slot
                :name="`cell.${col.key}`"
                :row="row"
                :effective="effective(row)"
              >
                <!-- Default renderers for the standard keys. -->
                <template v-if="col.key === 'name'">
                  <span class="font-weight-medium">{{ row.name }}</span>
                </template>
                <template v-else-if="col.key === 'memberType'">
                  <span class="text-caption text-medium-emphasis">{{ memberTypeLabel(row.memberType) }}</span>
                </template>
                <template v-else-if="col.key === 'disposition'">
                  <v-chip
                    :color="dispositionColor(effective(row))"
                    :data-testid="`bulk-preview-disposition-${row.userId}`"
                    size="x-small"
                    variant="tonal"
                  >
                    {{ dispositionLabel(effective(row)) }}
                  </v-chip>
                </template>
                <template v-else-if="col.key === 'memberSince'">
                  <span
                    class="text-caption text-medium-emphasis"
                    :data-testid="`bulk-preview-member-since-${row.userId}`"
                  >{{ formatMemberSince(row.memberSince) }}</span>
                </template>
                <template v-else-if="col.key === 'note'">
                  <span
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
                  </span>
                </template>
              </slot>
            </td>
            <td v-if="hasReincludable">
              <v-checkbox
                v-if="row.disposition === 'WARNING'"
                :data-testid="`bulk-preview-reinclude-${row.userId}`"
                :model-value="reincludeOverrides[row.userId] ?? false"
                color="primary"
                density="compact"
                hide-details
                @update:model-value="(v) => setReinclude(row.userId, !!v)"
              />
            </td>
          </tr>
        </tbody>
      </v-table>
    </v-form>
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

// Give the fee-type select cells a little vertical breathing room so the grouped
// selects are not cramped against each other.
.bulk-fee-cell {
  padding-top: 8px !important;
  padding-bottom: 8px !important;
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
