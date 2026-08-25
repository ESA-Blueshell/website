<script lang="ts" setup>
import type {MemberRow} from "@/composables/useUserRows"

defineOptions({name: "UserManagerMobileRow"})

/**
 * One user in the narrow-layout list.
 *
 * Deliberately narrower than the desktop row rather than a squeezed copy of it: no selection
 * checkbox, and the columns that do not survive the width are folded into two lines.
 */
defineProps<{
  row: MemberRow
  saving: boolean
  toggleDisabled: boolean
}>()

const emit = defineEmits<{
  "toggle-paid": [id: number]
  "manage-membership": [row: MemberRow]
  "edit-profile": [row: MemberRow]
  "delete-user": [row: MemberRow]
}>()
</script>

<template>
  <v-list-item
    class="member-manager-mobile-row"
    :data-testid="`member-manager-mobile-row-${row.id}`"
  >
    <v-list-item-title class="text-truncate">
      {{ row.fullName }}
    </v-list-item-title>

    <template #append>
      <div class="d-flex align-center flex-shrink-0">
        <v-btn
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
          class="btn-tight"
          color="red"
          :data-testid="`member-manager-mobile-delete-btn-${row.id}`"
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
      </div>
    </template>

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

<style lang="scss" scoped>
.member-manager-mobile-row {
  min-height: 40px;
}

// The four action buttons have to fit beside a name on a narrow screen.
.btn-tight {
  padding-inline: 6px !important;
  min-width: auto !important;
}
</style>
