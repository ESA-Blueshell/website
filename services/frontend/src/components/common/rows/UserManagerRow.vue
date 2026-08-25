<script lang="ts" setup>
import {
  isNotableType,
  statusColor,
  typeIcon,
  typeLabel,
  type MemberRow,
} from "@/composables/useUserRows"

defineOptions({name: "UserManagerRow"})

/**
 * One user in the desktop table.
 *
 * The display helpers are pure functions of the row, so they are imported rather than passed
 * down — the component needs the row, whether it is selected, and whether it is mid-save, and
 * nothing else. Everything that needs the page's state leaves as an event.
 */
defineProps<{
  row: MemberRow
  selected: boolean
  /** A paid toggle is in flight for this row. */
  saving: boolean
  /** No contribution period is selected, so there is nothing to mark paid against. */
  toggleDisabled: boolean
}>()

const emit = defineEmits<{
  "toggle-selected": [id: number]
  "toggle-paid": [id: number]
  "manage-membership": [row: MemberRow]
  "edit-profile": [row: MemberRow]
  // The row, not the user: resolving it belongs where the user list lives.
  "delete-user": [row: MemberRow]
}>()
</script>

<template>
  <tr :data-testid="`member-manager-row-${row.id}`">
    <td class="mm-select-cell">
      <v-checkbox-btn
        :data-testid="`member-manager-checkbox-${row.id}`"
        density="compact"
        :model-value="selected"
        @update:model-value="emit('toggle-selected', row.id)"
      />
    </td>

    <td class="font-weight-medium">
      {{ row.fullName }}
    </td>

    <td class="font-mono text-medium-emphasis">
      {{ row.username }}
    </td>

    <td class="text-right">
      <v-chip
        v-if="row.role"
        class="text-capitalize"
        size="small"
        variant="flat"
      >
        {{ row.role }}
      </v-chip>
    </td>

    <td :data-testid="`member-manager-status-${row.id}`">
      <v-chip
        :color="statusColor(row.status)"
        size="small"
        variant="flat"
      >
        {{ row.status }}
      </v-chip>
    </td>

    <td :data-testid="`member-manager-member-since-${row.id}`">
      {{ row.memberSince ?? "—" }}
    </td>

    <td :data-testid="`member-manager-period-member-${row.id}`">
      <v-chip
        :color="row.wasMemberInPeriod ? 'green' : 'grey'"
        size="small"
        style="width: 48px; justify-content: center"
        variant="flat"
      >
        {{ row.wasMemberInPeriod ? "Yes" : "No" }}
      </v-chip>
    </td>

    <td :data-testid="`member-manager-paid-status-${row.id}`">
      <v-chip
        :color="row.paid ? 'green' : 'red'"
        size="small"
        style="width: 56px; justify-content: center"
        variant="flat"
      >
        {{ row.paid ? "Paid" : "Unpaid" }}
      </v-chip>
    </td>

    <!-- Type / Incasso icons (notable only) -->
    <td :data-testid="`member-manager-type-incasso-${row.id}`">
      <div class="d-flex align-center gap-1">
        <v-tooltip
          v-if="isNotableType(row)"
          location="top"
          :text="typeLabel(row)"
        >
          <template #activator="{ props }">
            <v-icon
              v-bind="props"
              color="primary"
              :icon="typeIcon(row)"
              size="18"
            />
          </template>
        </v-tooltip>
        <v-tooltip
          v-if="row.latestIncasso"
          location="top"
          text="Incasso active"
        >
          <template #activator="{ props }">
            <v-icon
              v-bind="props"
              color="teal"
              icon="mdi-bank-transfer"
              size="18"
            />
          </template>
        </v-tooltip>
      </div>
    </td>

    <td>
      <div class="d-flex align-center gap-1">
        <v-tooltip
          location="top"
          :text="row.paid ? 'Mark unpaid' : 'Mark paid'"
        >
          <template #activator="{ props }">
            <v-btn
              v-bind="props"
              :data-testid="`member-manager-toggle-paid-btn-${row.id}`"
              :disabled="toggleDisabled"
              icon
              :loading="saving"
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
          location="top"
          text="Manage memberships"
        >
          <template #activator="{ props }">
            <v-btn
              v-bind="props"
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
          location="top"
          text="Edit profile"
        >
          <template #activator="{ props }">
            <v-btn
              v-bind="props"
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
          location="top"
          text="Delete user"
        >
          <template #activator="{ props }">
            <v-btn
              v-bind="props"
              color="red"
              :data-testid="`member-manager-delete-btn-${row.id}`"
              :disabled="row.role === 'admin'"
              icon
              size="small"
              variant="text"
              @click="emit('delete-user', row)"
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

<style lang="scss" scoped>
.gap-1 {
  gap: 4px;
}
</style>
