<script lang="ts" setup>
defineOptions({name: "BulkActionsMenu"})

interface Props {
  disabled?: boolean
  /** True when no contribution period is selected (period-relative actions are disabled). */
  noPeriod?: boolean
}

withDefaults(defineProps<Props>(), {
  disabled: false,
  noPeriod: false,
})

const emit = defineEmits<{
  (e: "markPaid"): void
  (e: "markUnpaid"): void
  (e: "sendReminder"): void
  (e: "sendIncasso"): void
  (e: "endMembership"): void
  (e: "resumeMembership"): void
}>()
</script>

<template>
  <v-menu location="bottom end">
    <template #activator="{ props: menuProps }">
      <v-btn
        v-bind="menuProps"
        :disabled="disabled"
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
        :disabled="noPeriod"
        data-testid="bulk-action-mark-paid"
        prepend-icon="mdi-cash-check"
        title="Mark as paid"
        @click="emit('markPaid')"
      />
      <v-list-item
        :disabled="noPeriod"
        data-testid="bulk-action-mark-unpaid"
        prepend-icon="mdi-cash-remove"
        title="Mark as unpaid"
        @click="emit('markUnpaid')"
      />
      <v-divider />
      <v-list-item
        :disabled="noPeriod"
        data-testid="bulk-action-send-reminder"
        prepend-icon="mdi-email-fast"
        title="Send contribution reminder"
        @click="emit('sendReminder')"
      />
      <v-list-item
        :disabled="noPeriod"
        data-testid="bulk-action-send-incasso"
        prepend-icon="mdi-bank-transfer"
        title="Send incasso notification"
        @click="emit('sendIncasso')"
      />
      <v-divider />
      <v-list-item
        data-testid="bulk-action-end-membership"
        prepend-icon="mdi-account-cancel"
        title="End membership"
        @click="emit('endMembership')"
      />
      <v-list-item
        data-testid="bulk-action-resume-membership"
        prepend-icon="mdi-account-reactivate"
        title="Resume / start membership"
        @click="emit('resumeMembership')"
      />
    </v-list>
  </v-menu>
</template>
