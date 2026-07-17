<template>
  <v-list-item
    class="member-manager-mobile-row"
    :style="{cursor: selectionActive ? 'pointer' : 'default'}"
    :data-testid="`member-manager-mobile-row-${row.id}`"
    @click="onRowClick"
  >
    <!-- Row checkbox prepend -->
    <template #prepend>
      <v-checkbox
        :model-value="selected"
        color="primary"
        :data-testid="`member-manager-mobile-checkbox-${row.id}`"
        density="compact"
        hide-details
        @update:model-value="emit('toggle-selection', row.id)"
      />
    </template>

    <!-- Line 1: Name (title) + action buttons (append slot) -->
    <v-list-item-title class="text-truncate">
      {{ row.fullName }}
    </v-list-item-title>

    <template #append>
      <div class="d-flex align-center flex-shrink-0">
        <v-btn
          :data-testid="`member-manager-mobile-toggle-paid-btn-${row.id}`"
          :disabled="toggleDisabled"
          :loading="isSaving"
          class="btn-tight"
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
          :data-testid="`member-manager-mobile-manage-membership-btn-${row.id}`"
          class="btn-tight"
          icon
          size="small"
          variant="text"
          @click="emit('manage-membership', row)"
        >
          <v-icon
            icon="mdi-card-account-details"
            size="18"
          />
        </v-btn>
        <v-btn
          :data-testid="`member-manager-mobile-edit-profile-btn-${row.id}`"
          class="btn-tight"
          icon
          size="small"
          variant="text"
          @click="emit('edit-profile', row)"
        >
          <v-icon
            icon="mdi-pencil"
            size="18"
          />
        </v-btn>
        <v-btn
          :data-testid="`member-manager-mobile-delete-btn-${row.id}`"
          :disabled="row.role === 'admin'"
          class="btn-tight"
          color="red"
          icon
          size="small"
          variant="text"
          @click="emit('delete', row)"
        >
          <v-icon
            icon="mdi-delete"
            size="18"
          />
        </v-btn>
      </div>
    </template>

    <!-- Line 2: Username + role chip -->
    <v-list-item-subtitle class="d-flex align-center gap-2">
      <span
        class="font-mono text-medium-emphasis text-truncate flex-grow-1"
        style="min-width: 0"
      >{{ row.username }}</span>
      <v-chip
        v-if="row.role"
        class="text-capitalize flex-shrink-0"
        size="x-small"
        variant="flat"
      >
        {{ row.role }}
      </v-chip>
      <v-chip
        :color="row.wasMemberInPeriod ? 'green' : 'grey'"
        size="x-small"
        variant="flat"
      >
        {{ row.wasMemberInPeriod ? "In period" : "Not in period" }}
      </v-chip>
    </v-list-item-subtitle>
  </v-list-item>
</template>

<script lang="ts" setup>
import {onUpdated, ref} from "vue"
import {type MemberRow} from "@/composables/useMemberRows"

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

function isClickOnInteractiveTarget(target: HTMLElement): boolean {
  return !!target.closest("button, a, input, label, .v-selection-control, [role=button]")
}

function onRowClick(event: MouseEvent) {
  if (!props.selectionActive) return
  if (isClickOnInteractiveTarget(event.target as HTMLElement)) return
  emit("row-click", event, props.row.id)
}
</script>

<style lang="scss" scoped>
// Compact, single-line mobile rows (table-like, not tall).
.member-manager-mobile-row {
  min-height: 40px;

  .mm-username {
    min-width: 0;
  }
}

.btn-tight {
  padding-inline: 6px !important;
  min-width: auto !important;
}
</style>
