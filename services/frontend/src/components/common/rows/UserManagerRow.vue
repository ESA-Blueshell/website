<template>
  <tr
    :class="{'mm-row--selected': selected}"
    :style="{cursor: selectionActive ? 'pointer' : 'default'}"
    :data-testid="`member-manager-row-${row.id}`"
    @click="onRowClick"
  >
    <!-- Row checkbox -->
    <td
      class="mm-td-checkbox"
      style="width: 48px"
    >
      <v-checkbox
        :model-value="selected"
        color="primary"
        :data-testid="`member-manager-checkbox-${row.id}`"
        density="compact"
        hide-details
        @update:model-value="emit('toggle-selection', row.id)"
      />
    </td>

    <!-- Name. The fixed table layout truncates long values with an ellipsis;
         the title attribute reveals the full value on hover. -->
    <td
      :title="row.fullName"
      class="font-weight-medium"
    >
      {{ row.fullName }}
    </td>

    <!-- Username -->
    <td
      :title="row.username"
      class="font-mono text-medium-emphasis"
    >
      {{ row.username }}
    </td>

    <!-- Role -->
    <td class="text-right">
      <v-chip
        v-if="row.role"
        size="small"
        variant="flat"
        class="text-capitalize"
      >
        {{ row.role }}
      </v-chip>
    </td>

    <!-- Status -->
    <td :data-testid="`member-manager-status-${row.id}`">
      <v-chip
        :color="statusColor(row.status)"
        size="small"
        variant="flat"
      >
        {{ row.status }}
      </v-chip>
    </td>

    <!-- Member since -->
    <td :data-testid="`member-manager-member-since-${row.id}`">
      {{ row.memberSince ?? "—" }}
    </td>

    <!-- Member in selected contribution period -->
    <td :data-testid="`member-manager-period-member-${row.id}`">
      <v-chip
        :color="row.wasMemberInPeriod ? 'green' : 'grey'"
        size="small"
        variant="flat"
        style="width: 48px; justify-content: center"
      >
        {{ row.wasMemberInPeriod ? "Yes" : "No" }}
      </v-chip>
    </td>

    <!-- Paid/Unpaid -->
    <td :data-testid="`member-manager-paid-status-${row.id}`">
      <v-chip
        :color="row.paid ? 'green' : 'red'"
        size="small"
        variant="flat"
        style="width: 56px; justify-content: center"
      >
        {{ row.paid ? "Paid" : "Unpaid" }}
      </v-chip>
    </td>

    <!-- Type / Incasso icons (notable only).
         Native title tooltips throughout this row: each v-tooltip mounts overlay +
         positioning machinery PER INSTANCE, which made virtualized rows expensive
         to mount and caused visible pop-in while scrolling. -->
    <td :data-testid="`member-manager-type-incasso-${row.id}`">
      <div class="d-flex align-center gap-1">
        <v-icon
          v-if="isNotableType(row)"
          :aria-label="typeLabel(row)"
          :icon="typeIcon(row)"
          :title="typeLabel(row)"
          size="18"
          color="primary"
        />
        <v-icon
          v-if="row.latestIncasso"
          aria-label="Incasso active"
          icon="mdi-bank-transfer"
          size="18"
          color="teal"
          title="Incasso active"
        />
      </div>
    </td>

    <!-- Actions -->
    <td>
      <div class="d-flex align-center gap-1">
        <v-btn
          :aria-label="row.paid ? 'Mark unpaid' : 'Mark paid'"
          :data-testid="`member-manager-toggle-paid-btn-${row.id}`"
          :disabled="toggleDisabled"
          :loading="isSaving"
          :title="row.paid ? 'Mark unpaid' : 'Mark paid'"
          icon
          size="small"
          variant="text"
          @click="emit('toggle-paid', row.id)"
        >
          <v-icon
            :icon="row.paid ? 'mdi-cash-remove' : 'mdi-cash-check'"
            size="18"
          />
        </v-btn>

        <v-btn
          :data-testid="`member-manager-manage-membership-btn-${row.id}`"
          aria-label="Manage memberships"
          icon
          size="small"
          title="Manage memberships"
          variant="text"
          @click="emit('manage-membership', row)"
        >
          <v-icon
            icon="mdi-card-account-details"
            size="18"
          />
        </v-btn>

        <v-btn
          :data-testid="`member-manager-edit-profile-btn-${row.id}`"
          aria-label="Edit profile"
          icon
          size="small"
          title="Edit profile"
          variant="text"
          @click="emit('edit-profile', row)"
        >
          <v-icon
            icon="mdi-pencil"
            size="18"
          />
        </v-btn>

        <v-btn
          :data-testid="`member-manager-delete-btn-${row.id}`"
          :disabled="row.role === 'admin'"
          aria-label="Delete user"
          color="red"
          icon
          size="small"
          title="Delete user"
          variant="text"
          @click="emit('delete', row)"
        >
          <v-icon
            icon="mdi-delete"
            size="18"
          />
        </v-btn>
      </div>
    </td>
  </tr>
</template>

<script lang="ts" setup>
import {onUpdated, ref} from "vue"
import {isNotableType, typeIcon, typeLabel, statusColor, type MemberRow} from "@/composables/useUserRows"
import {isClickOnInteractiveTarget} from "@/utils/rowInteraction"

const props = defineProps<{
  row: MemberRow
  selected: boolean
  selectionActive: boolean
  toggleDisabled: boolean
  isSaving: boolean
}>()

const emit = defineEmits<{
  "toggle-selection": [id: number]
  "row-click": [event: MouseEvent, id: number]
  "toggle-paid": [id: number]
  "manage-membership": [row: MemberRow]
  "edit-profile": [row: MemberRow]
  "delete": [row: MemberRow]
}>()

// ── Re-render isolation tracking (used by unit tests) ─────────────────────────
const __updateCount = ref(0)
onUpdated(() => {
  __updateCount.value++
})
defineExpose({__updateCount})

// ── Row click handler ─────────────────────────────────────────────────────────

function onRowClick(event: MouseEvent) {
  if (!props.selectionActive) return
  if (isClickOnInteractiveTarget(event.target as HTMLElement)) return
  emit("row-click", event, props.row.id)
}
</script>

<style lang="scss" scoped>
/**
 * Row height 36px (density="compact" contract). ResizeObserver contract is load-bearing:
 * any row > 36px triggers full recalculation + window shift (visible flicker). Cells must
 * not stretch the row. With table-layout:fixed, text needs ellipsis+nowrap to maintain height.
 */
tr {
  height: 36px;

  > td {
    padding-top: 0;
    padding-bottom: 0;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  :deep(.v-selection-control) {
    min-height: 0;
    height: 36px;
  }

  :deep(.v-btn--icon.v-btn--size-small) {
    width: 32px;
    height: 32px;
  }
}

// Align checkbox center in cell to match header; prevent left-anchor against cell padding.
.mm-td-checkbox {
  padding-right: 0;
  text-align: center;

  :deep(.v-selection-control) {
    justify-content: center;
  }
}

// Selected row highlight (mirrors parent's .mm-row--selected)
.mm-row--selected > td {
  background: rgba(var(--v-theme-primary), 0.07) !important;
}

.gap-1 {
  gap: 4px;
}
</style>
