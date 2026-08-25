<script lang="ts" setup>
import {onUpdated, ref} from "vue"
import {type MemberRow} from "@/composables/useUserRows"

defineOptions({name: "UserManagerMobileRow"})

/**
 * One member in the narrow-layout list. Extracted alongside the desktop row so both idioms
 * patch per row rather than per table.
 */
const props = defineProps<{
  row: MemberRow
  /** True when no contribution period is selected, so paid status cannot be changed. */
  toggleDisabled: boolean
  /** True while this row's paid status is being written. */
  saving: boolean
}>()

const emit = defineEmits<{
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
  <v-list-item
    class="member-manager-mobile-row"
    :data-testid="`member-manager-mobile-row-${row.id}`"
  >
    <!-- Line 1: name, with the actions on the trailing edge -->
    <v-list-item-title class="text-truncate">
      {{ row.fullName }}
    </v-list-item-title>

    <template #append>
      <div class="d-flex align-center flex-shrink-0">
        <v-btn
          :aria-label="paidActionLabel()"
          class="btn-tight"
          :data-testid="`member-manager-mobile-toggle-paid-btn-${row.id}`"
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
        <v-btn
          aria-label="Manage memberships"
          class="btn-tight"
          :data-testid="`member-manager-mobile-manage-membership-btn-${row.id}`"
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
          aria-label="Edit profile"
          class="btn-tight"
          :data-testid="`member-manager-mobile-edit-profile-btn-${row.id}`"
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
          aria-label="Delete user"
          class="btn-tight"
          color="red"
          :data-testid="`member-manager-mobile-delete-btn-${row.id}`"
          :disabled="row.role === 'admin'"
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

    <!-- Line 2: username, role and whether the member counted in the selected period -->
    <v-list-item-subtitle class="d-flex align-center">
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

<style lang="scss" scoped>
// Compact, single-line rows — table-like rather than tall.
.member-manager-mobile-row {
  min-height: 40px;
}

.btn-tight {
  padding-inline: 6px !important;
  min-width: auto !important;
}
</style>
