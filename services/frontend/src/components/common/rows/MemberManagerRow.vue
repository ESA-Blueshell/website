<template>
  <tr
    :class="{'mm-row--selected': selected}"
    :style="{cursor: selectionActive ? 'pointer' : 'default'}"
    :data-testid="`member-manager-row-${row.id}`"
    @click="onRowClick"
  >
    <!-- Row checkbox -->
    <td style="padding-right: 0; width: 48px">
      <v-checkbox
        :model-value="selected"
        color="primary"
        :data-testid="`member-manager-checkbox-${row.id}`"
        density="compact"
        hide-details
        @update:model-value="emit('toggle-selection', row.id)"
      />
    </td>

    <!-- Name -->
    <td class="font-weight-medium">
      {{ row.fullName }}
    </td>

    <!-- Username -->
    <td class="font-mono text-medium-emphasis">
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

    <!-- Type / Incasso icons (notable only) -->
    <td :data-testid="`member-manager-type-incasso-${row.id}`">
      <div class="d-flex align-center gap-1">
        <v-tooltip
          v-if="isNotableType(row)"
          :text="typeLabel(row)"
          location="top"
        >
          <template #activator="{ props: activatorProps }">
            <v-icon
              v-bind="activatorProps"
              :icon="typeIcon(row)"
              size="18"
              color="primary"
            />
          </template>
        </v-tooltip>
        <v-tooltip
          v-if="row.latestIncasso"
          text="Incasso active"
          location="top"
        >
          <template #activator="{ props: activatorProps }">
            <v-icon
              v-bind="activatorProps"
              icon="mdi-bank-transfer"
              size="18"
              color="teal"
            />
          </template>
        </v-tooltip>
      </div>
    </td>

    <!-- Actions -->
    <td>
      <div class="d-flex align-center gap-1">
        <v-tooltip
          :text="row.paid ? 'Mark unpaid' : 'Mark paid'"
          location="top"
        >
          <template #activator="{ props: activatorProps }">
            <v-btn
              v-bind="activatorProps"
              :data-testid="`member-manager-toggle-paid-btn-${row.id}`"
              :disabled="toggleDisabled"
              :loading="isSaving"
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
          </template>
        </v-tooltip>

        <v-tooltip
          text="Manage memberships"
          location="top"
        >
          <template #activator="{ props: activatorProps }">
            <v-btn
              v-bind="activatorProps"
              :data-testid="`member-manager-manage-membership-btn-${row.id}`"
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
          </template>
        </v-tooltip>

        <v-tooltip
          text="Edit profile"
          location="top"
        >
          <template #activator="{ props: activatorProps }">
            <v-btn
              v-bind="activatorProps"
              :data-testid="`member-manager-edit-profile-btn-${row.id}`"
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
          </template>
        </v-tooltip>

        <v-tooltip
          text="Delete user"
          location="top"
        >
          <template #activator="{ props: activatorProps }">
            <v-btn
              v-bind="activatorProps"
              :data-testid="`member-manager-delete-btn-${row.id}`"
              :disabled="row.role === 'admin'"
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
          </template>
        </v-tooltip>
      </div>
    </td>
  </tr>
</template>

<script lang="ts" setup>
import {onUpdated, ref} from "vue"
import {isNotableType, typeIcon, typeLabel, statusColor, type MemberRow} from "@/composables/useMemberRows"

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
// Selected row highlight (mirrors parent's .mm-row--selected)
.mm-row--selected > td {
  background: rgba(var(--v-theme-primary), 0.07) !important;
}

.gap-1 {
  gap: 4px;
}
</style>
