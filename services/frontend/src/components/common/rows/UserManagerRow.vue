<script lang="ts" setup>
import {onUpdated, ref} from "vue"
import {isNotableType, statusColor, typeIcon, typeLabel, type MemberRow} from "@/composables/useUserRows"

defineOptions({name: "UserManagerRow"})

/**
 * One member in the desktop table.
 *
 * Its own component so that changing one row's state patches one row: with the whole table
 * inlined in the page, every cell of every row re-rendered whenever the selection changed.
 * Vue compares props here and skips the rows whose props stayed the same.
 */
const props = defineProps<{
  row: MemberRow
  selected: boolean
  /** True when no contribution period is selected, so paid status cannot be changed. */
  toggleDisabled: boolean
  /** True while this row's paid status is being written. */
  saving: boolean
}>()

const emit = defineEmits<{
  "toggle-selection": [id: number]
  "toggle-paid": [id: number]
  "manage-membership": [row: MemberRow]
  "edit-profile": [row: MemberRow]
  delete: [row: MemberRow]
}>()

// Read by the re-render isolation test, which has no other way to observe that a row was
// left alone while its neighbour changed.
const updateCount = ref(0)
onUpdated(() => {
  updateCount.value++
})
defineExpose({updateCount})

const paidActionLabel = () => (props.row.paid ? "Mark unpaid" : "Mark paid")
</script>

<template>
  <tr
    :class="{'mm-row--selected': selected}"
    :data-testid="`member-manager-row-${row.id}`"
  >
    <td class="mm-select-cell">
      <v-checkbox-btn
        :data-testid="`member-manager-checkbox-${row.id}`"
        density="compact"
        :model-value="selected"
        @update:model-value="emit('toggle-selection', row.id)"
      />
    </td>

    <!-- Name -->
    <td
      class="font-weight-medium"
      :title="row.fullName"
    >
      {{ row.fullName }}
    </td>

    <!-- Username -->
    <td
      class="font-mono text-medium-emphasis"
      :title="row.username"
    >
      {{ row.username }}
    </td>

    <!-- Role -->
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

    <!-- Member in the selected contribution period -->
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

    <!-- Paid in the selected contribution period -->
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

    <!-- Type / Incasso icons (notable only). Native titles rather than v-tooltip: a tooltip
         mounts overlay and positioning machinery per instance, and this table renders six of
         them per row. -->
    <td :data-testid="`member-manager-type-incasso-${row.id}`">
      <div class="d-flex align-center gap-1">
        <v-icon
          v-if="isNotableType(row)"
          :aria-label="typeLabel(row)"
          color="primary"
          :icon="typeIcon(row)"
          size="18"
          :title="typeLabel(row)"
        />
        <v-icon
          v-if="row.latestIncasso"
          aria-label="Incasso active"
          color="teal"
          icon="mdi-bank-transfer"
          size="18"
          title="Incasso active"
        />
      </div>
    </td>

    <!-- Actions -->
    <td>
      <div class="d-flex align-center gap-1">
        <v-btn
          :aria-label="paidActionLabel()"
          :data-testid="`member-manager-toggle-paid-btn-${row.id}`"
          :disabled="toggleDisabled"
          icon
          :loading="saving"
          size="small"
          :title="paidActionLabel()"
          variant="text"
          @click="emit('toggle-paid', row.id)"
        >
          <v-icon
            :icon="row.paid ? 'mdi-cash-remove' : 'mdi-cash-check'"
            size="18"
          />
        </v-btn>

        <v-btn
          aria-label="Manage memberships"
          :data-testid="`member-manager-manage-membership-btn-${row.id}`"
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
          aria-label="Edit profile"
          :data-testid="`member-manager-edit-profile-btn-${row.id}`"
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
          aria-label="Delete user"
          color="red"
          :data-testid="`member-manager-delete-btn-${row.id}`"
          :disabled="row.role === 'admin'"
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

<style lang="scss" scoped>
// The virtual scroller positions rows by multiplying this height by an index, so a row that
// grows — a name wrapping onto a second line under the fixed table layout — would put every
// row below it in the wrong place. Cells truncate instead, and carry a title with the full
// value.
tr {
  height: 44px;

  > td {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
}

// The cells belong to this component, so the rules that reach them live here: a page-level
// scoped rule carries the page's scope id and would only match this row's root element.
.mm-row--selected > td {
  background: rgba(var(--v-theme-primary), 0.14);
}

// The checkbox column carries no label and should not take room from the ones that do. The
// header cell sets the column's width; this keeps the box centred under it.
.mm-select-cell {
  padding-inline: 4px !important;
  text-align: center;

  :deep(.v-selection-control) {
    justify-content: center;
  }
}

.gap-1 {
  gap: 4px;
}
</style>
