<script lang="ts" setup>
import {computed} from "vue"
import {ContributionEmailKind, type ContributionPeriodResponse} from "@/services/api"
import {BulkFeeType, type BulkRow} from "@/utils/bulkRow"
import {
  contributionEmailItems,
  isReCharged,
  isSwitched,
  reChargedDescription,
  switchedDescription,
  kindFor,
  lastSentLabel,
  lastSentOn,
  switchedNote,
} from "@/utils/contributionEmail"
import {effectiveAmount, feeTypeItems} from "@/utils/feePreview"

/**
 * Step 2 — which email each recipient gets and which fee prices it.
 *
 * The two changes carry different risks, so they are warned about separately: the wrong
 * email can make a member pay twice, the wrong fee bills the wrong amount.
 */
defineOptions({name: "PaymentEmailFeesStep"})

const props = defineProps<{
  rows: BulkRow[]
  kinds: Record<number, ContributionEmailKind>
  fees: Record<number, BulkFeeType>
  period: ContributionPeriodResponse | null
}>()

const emit = defineEmits<{
  (e: "update:kinds", value: Record<number, ContributionEmailKind>): void
  (e: "update:fees", value: Record<number, BulkFeeType>): void
}>()

const switchedLines = computed(() =>
  props.rows
    .filter((row) => isSwitched(row, props.kinds))
    .map((row) => ({userId: row.userId, text: switchedDescription(row, props.kinds)})),
)

const reChargedLines = computed(() =>
  props.rows
    .filter((row) => isReCharged(row, props.fees))
    .map((row) => ({userId: row.userId, text: reChargedDescription(row, props.fees)})),
)

/** Whether the association collects from this member, which is what chose their email. */
function paysByDirectDebit(row: BulkRow): boolean {
  return row.defaultKind === ContributionEmailKind.INCASSO_NOTIFICATION
}

function rowAmount(row: BulkRow): number | null {
  return effectiveAmount(props.fees[row.userId] ?? row.recommendedFeeType, props.period)
}

function setKind(userId: number, kind: ContributionEmailKind) {
  emit("update:kinds", {...props.kinds, [userId]: kind})
}

function setFee(userId: number, fee: BulkFeeType) {
  emit("update:fees", {...props.fees, [userId]: fee})
}
</script>

<template>
  <v-table
    class="payment-email-table"
    data-testid="payment-emails-fees-table"
    density="compact"
  >
    <thead>
      <tr>
        <th>Member</th>
        <th class="text-center">
          Direct debit
        </th>
        <th>Email type</th>
        <th>Fee type</th>
        <th class="text-end">
          Amount
        </th>
        <th class="text-center">
          Last sent
        </th>
      </tr>
    </thead>
    <tbody>
      <tr
        v-for="row in rows"
        :key="row.userId"
        :data-testid="`payment-emails-fee-row-${row.userId}`"
      >
        <td>
          <div class="font-weight-medium">
            {{ row.name }}
          </div>
          <div
            v-if="isSwitched(row, kinds)"
            class="text-caption text-warning d-flex align-center ga-1"
            :data-testid="`payment-emails-switched-${row.userId}`"
          >
            <v-icon
              icon="mdi-alert-outline"
              size="14"
            />
            {{ switchedNote(row) }}
          </div>
        </td>
        <td class="text-center">
          <v-icon
            v-if="paysByDirectDebit(row)"
            color="primary"
            :data-testid="`payment-emails-mandate-${row.userId}`"
            icon="mdi-check-circle-outline"
            size="18"
          />
          <span
            v-else
            class="text-medium-emphasis"
            :data-testid="`payment-emails-mandate-${row.userId}`"
          >—</span>
        </td>
        <td>
          <v-select
            class="payment-email-select"
            :data-testid="`payment-emails-kind-${row.userId}`"
            density="compact"
            hide-details
            item-title="title"
            item-value="value"
            :items="contributionEmailItems"
            :model-value="kindFor(row, kinds)"
            variant="plain"
            @update:model-value="(v) => setKind(row.userId, v)"
          />
        </td>
        <td>
          <v-select
            class="payment-email-select"
            :data-testid="`payment-emails-feetype-${row.userId}`"
            density="compact"
            hide-details
            item-title="title"
            item-value="value"
            :items="feeTypeItems"
            :model-value="fees[row.userId] ?? row.recommendedFeeType"
            variant="plain"
            @update:model-value="(v) => setFee(row.userId, v)"
          />
        </td>
        <td class="text-end">
          <span
            v-if="rowAmount(row) != null"
            class="text-caption"
            :data-testid="`payment-emails-amount-${row.userId}`"
          >€ {{ rowAmount(row)!.toFixed(2) }}</span>
          <span
            v-else
            class="text-medium-emphasis"
          >—</span>
        </td>
        <td class="text-center">
          <span
            class="text-caption"
            :class="lastSentOn(row, kinds) ? 'text-warning' : 'text-medium-emphasis'"
            :data-testid="`payment-emails-last-sent-${row.userId}`"
          >{{ lastSentLabel(lastSentOn(row, kinds)) }}</span>
        </td>
      </tr>
    </tbody>
  </v-table>

  <v-alert
    v-if="switchedLines.length"
    class="mt-3"
    data-testid="payment-emails-kind-warning"
    density="compact"
    icon="mdi-email-sync-outline"
    type="warning"
    variant="tonal"
  >
    <div class="text-body-2 font-weight-medium">
      {{ switchedLines.length }}
      {{ switchedLines.length === 1 ? "member gets" : "members get" }}
      the email their direct-debit flag does not call for
    </div>
    <ul class="payment-email-lines text-body-2">
      <li
        v-for="line in switchedLines"
        :key="line.userId"
      >
        {{ line.text }}
      </li>
    </ul>
    <div class="text-body-2 mt-1">
      An incasso notification says money is taken. A contribution reminder asks for a
      transfer. Sending the wrong one can make a member pay twice.
    </div>
  </v-alert>

  <v-alert
    v-if="reChargedLines.length"
    class="mt-3"
    data-testid="payment-emails-fee-warning"
    density="compact"
    icon="mdi-cash-sync"
    type="warning"
    variant="tonal"
  >
    <div class="text-body-2 font-weight-medium">
      {{ reChargedLines.length }}
      {{ reChargedLines.length === 1 ? "member is" : "members are" }}
      charged a fee that does not apply to them
    </div>
    <ul class="payment-email-lines text-body-2">
      <li
        v-for="line in reChargedLines"
        :key="line.userId"
      >
        {{ line.text }}
      </li>
    </ul>
  </v-alert>

  <p
    v-if="rows.length === 0"
    class="text-body-2 text-medium-emphasis"
    data-testid="payment-emails-fees-empty"
  >
    Nobody is ticked, so there is nothing to price. Go back and pick who this batch is for.
  </p>
</template>

<style lang="scss" scoped>
@use '@/styles/payment-email' as paymentEmail;

// A sentence per member, inside the alert's own padding.
.payment-email-lines {
  list-style: none;
  padding-left: 0;
  margin: 2px 0 0;
}

@include paymentEmail.sticky-table-header;

// The two pickers are the cells that cannot afford to be compressed: a wrapped
// "Incasso notification" is unreadable where a wrapped member name is fine.
.payment-email-select {
  min-width: max-content;

  :deep(.v-field__input) {
    white-space: nowrap;
  }
}
</style>
