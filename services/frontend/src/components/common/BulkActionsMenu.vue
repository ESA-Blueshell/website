<script lang="ts" setup>
import {computed} from "vue"

defineOptions({name: "BulkActionsMenu"})

interface Props {
  /** True when at least one user is selected, so there is something to act on. */
  hasSelection?: boolean
  /** True when no contribution period is selected (period-relative actions are disabled). */
  noPeriod?: boolean
}

const props = withDefaults(defineProps<Props>(), {
  hasSelection: false,
  noPeriod: false,
})

const emit = defineEmits<{
  (e: "addUser"): void
  (e: "markPaid"): void
  (e: "markUnpaid"): void
  (e: "sendPaymentEmails"): void
  (e: "endMembership"): void
  (e: "startMembership"): void
}>()

// Marking contributions needs a selection to act on and a period to book them against.
const bulkDisabled = computed(() => !props.hasSelection || props.noPeriod)
</script>

<template>
  <v-menu location="bottom end">
    <template #activator="{ props: menuProps }">
      <v-btn
        v-bind="menuProps"
        aria-label="User actions"
        data-testid="bulk-actions-menu-btn"
        icon
        size="small"
        variant="text"
      >
        <v-icon icon="mdi-dots-vertical" />
      </v-btn>
    </template>

    <v-list
      data-testid="bulk-actions-menu"
      density="compact"
      min-width="220"
    >
      <v-list-item
        data-testid="member-manager-add-user-btn"
        prepend-icon="mdi-account-plus"
        title="Add user"
        @click="emit('addUser')"
      />
      <v-divider />
      <v-list-item
        :disabled="bulkDisabled"
        data-testid="bulk-action-mark-paid"
        prepend-icon="mdi-cash-check"
        title="Mark as paid"
        @click="emit('markPaid')"
      />
      <v-list-item
        :disabled="bulkDisabled"
        data-testid="bulk-action-mark-unpaid"
        prepend-icon="mdi-cash-remove"
        title="Mark as unpaid"
        @click="emit('markUnpaid')"
      />
      <v-divider />
      <v-list-item
        :disabled="bulkDisabled"
        data-testid="bulk-action-send-payment-emails"
        prepend-icon="mdi-email-fast"
        title="Send payment emails"
        @click="emit('sendPaymentEmails')"
      />
      <v-divider />
      <!--
        Membership acts on the member rather than on a period, so these need a selection
        and nothing else.
      -->
      <v-list-item
        :disabled="!hasSelection"
        data-testid="bulk-action-end-membership"
        prepend-icon="mdi-account-remove"
        title="End membership"
        @click="emit('endMembership')"
      />
      <v-list-item
        :disabled="!hasSelection"
        data-testid="bulk-action-start-membership"
        prepend-icon="mdi-account-plus"
        title="Start membership"
        @click="emit('startMembership')"
      />
    </v-list>
  </v-menu>
</template>
