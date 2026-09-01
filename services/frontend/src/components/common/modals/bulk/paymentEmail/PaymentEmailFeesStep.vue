<script lang="ts" setup>
import {computed} from "vue"
import {ContributionEmailKind, type ContributionPeriodResponse} from "@/services/api"
import {BulkFeeType, type BulkRow} from "@/utils/bulkRow"
import {
  contributionEmailItems,
  isReCharged,
  isSwitched,
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

const switchedNames = computed(() =>
  props.rows.filter((row) => isSwitched(row, props.kinds)).map((row) => row.name),
)

const reChargedNames = computed(() =>
  props.rows.filter((row) => isReCharged(row, props.fees)).map((row) => row.name),
)

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
  <v-alert
    v-if="switchedNames.length"
    class="mb-3"
    data-testid="payment-emails-kind-warning"
    density="compact"
    icon="mdi-email-sync-outline"
    type="warning"
    variant="tonal"
  >
    <div class="text-body-2 font-weight-medium">
      {{ switchedNames.length }}
      {{ switchedNames.length === 1 ? "member is" : "members are" }}
      moved off the email their direct-debit flag chose
    </div>
    <div class="text-body-2">
      {{ switchedNames.join(", ") }} — an incasso notification says money is taken, a
      contribution reminder asks for a transfer. Sending the wrong one can make a member pay
      twice.
    </div>
  </v-alert>

  <v-alert
    v-if="reChargedNames.length"
    class="mb-3"
    data-testid="payment-emails-fee-warning"
    density="compact"
    icon="mdi-cash-sync"
    type="warning"
    variant="tonal"
  >
    <div class="text-body-2 font-weight-medium">
      {{ reChargedNames.length }}
      {{ reChargedNames.length === 1 ? "member is" : "members are" }}
      charged a fee type other than the one that applies
    </div>
    <div class="text-body-2">
      {{ reChargedNames.join(", ") }} — they are billed the amount that fee sets, not the one
      their membership works out to.
    </div>
  </v-alert>

  <v-table
    class="payment-email-table"
    data-testid="payment-emails-fees-table"
    density="compact"
  >
    <thead>
      <tr>
        <th>Member</th>
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
            Switched — {{ switchedNote(row) }}
          </div>
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
