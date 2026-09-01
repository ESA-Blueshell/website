<script lang="ts" setup>
import {ContributionEmailKind, type ContributionPeriodResponse} from "@/services/api"
import type {BulkFeeType, BulkRow} from "@/utils/bulkRow"
import {contributionEmailLabels, kindFor} from "@/utils/contributionEmail"
import {effectiveAmount, feeTypeLabels} from "@/utils/feePreview"

/**
 * Step 3 — the two dates, then what each recipient gets.
 *
 * The date is stated once, in the field that sets it, so the list below is only the things
 * that differ per member.
 */
defineOptions({name: "PaymentEmailReviewStep"})

const props = defineProps<{
  rows: BulkRow[]
  kinds: Record<number, ContributionEmailKind>
  fees: Record<number, BulkFeeType>
  period: ContributionPeriodResponse | null
  paymentDueDate: string
  debitDate: string
  sendsReminders: boolean
  sendsNotifications: boolean
  /** Why each date cannot be sent, composed by the wizard from the rule the api mirrors. */
  dateProblems: {paymentDueDate: string | null; debitDate: string | null}
}>()

const emit = defineEmits<{
  (e: "update:paymentDueDate", value: string): void
  (e: "update:debitDate", value: string): void
  (e: "preview", userId: number): void
}>()

function kindLabel(row: BulkRow): string {
  return contributionEmailLabels[kindFor(row, props.kinds)]
}

function feeLabel(row: BulkRow): string {
  const feeType = props.fees[row.userId] ?? row.recommendedFeeType
  return feeType ? feeTypeLabels[feeType] : "—"
}

function amountLabel(row: BulkRow): string {
  const amount = effectiveAmount(props.fees[row.userId] ?? row.recommendedFeeType, props.period)
  return amount == null ? "—" : `€ ${amount.toFixed(2)}`
}

/** A member cannot be previewed before the date their own email quotes is given. */
function canPreview(row: BulkRow): boolean {
  return kindFor(row, props.kinds) === ContributionEmailKind.INCASSO_NOTIFICATION
    ? !!props.debitDate
    : !!props.paymentDueDate
}
</script>

<template>
  <div class="d-flex ga-3 mb-4">
    <v-text-field
      data-testid="payment-emails-payment-due-date"
      :error-messages="dateProblems.paymentDueDate ?? undefined"
      hide-details="auto"
      :hint="sendsReminders
        ? undefined
        : 'Optional — nobody in this batch is being asked to transfer.'"
      label="Payment due date"
      :model-value="paymentDueDate"
      persistent-hint
      placeholder="YYYY-MM-DD"
      prepend-inner-icon="mdi-calendar"
      type="date"
      @update:model-value="(v) => emit('update:paymentDueDate', v)"
    />
    <v-text-field
      data-testid="payment-emails-debit-date"
      :error-messages="dateProblems.debitDate ?? undefined"
      hide-details="auto"
      :hint="sendsNotifications
        ? undefined
        : 'Optional — nobody in this batch is on direct debit.'"
      label="Debit date"
      :model-value="debitDate"
      persistent-hint
      placeholder="YYYY-MM-DD"
      prepend-inner-icon="mdi-calendar-arrow-right"
      type="date"
      @update:model-value="(v) => emit('update:debitDate', v)"
    />
  </div>

  <div
    v-for="row in rows"
    :key="row.userId"
    class="payment-email-recipient d-flex align-center ga-3 py-2"
    :data-testid="`payment-emails-recipient-${row.userId}`"
  >
    <div class="font-weight-medium payment-email-recipient__name">
      {{ row.name }}
    </div>
    <div class="text-body-2">
      {{ kindLabel(row) }}
    </div>
    <div class="text-body-2 text-medium-emphasis">
      {{ feeLabel(row) }}
    </div>
    <div class="text-body-2">
      {{ amountLabel(row) }}
    </div>
    <v-spacer />
    <v-btn
      :data-testid="`payment-emails-preview-${row.userId}`"
      :disabled="!canPreview(row)"
      prepend-icon="mdi-email-search-outline"
      size="small"
      variant="text"
      @click="emit('preview', row.userId)"
    >
      Preview
    </v-btn>
  </div>
</template>

<style lang="scss" scoped>
.payment-email-recipient + .payment-email-recipient {
  border-top: thin solid rgba(var(--v-border-color), var(--v-border-opacity));
}

.payment-email-recipient__name {
  min-width: 200px;
}
</style>
