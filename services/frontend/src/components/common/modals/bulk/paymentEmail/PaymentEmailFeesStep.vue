<script lang="ts" setup>
import {computed} from "vue"
import {ContributionEmailKind, type ContributionPeriodResponse} from "@/domains/contribution"
import {useNarrowLayout} from "@/composables/useNarrowLayout"
import {formatBulkDate} from "@/utils/bulkDisposition"
import {BulkFeeType, type BulkRow} from "@/utils/bulkRow"
import {
  contributionEmailItems,
  isReCharged,
  isSwitched,
  lastAskedOn,
  reChargedDescription,
  switchedDescription,
  kindFor,
  switchedNote,
} from "@/domains/contribution"
import {effectiveAmount, feeTypeItems} from "@/utils/feePreview"

/**
 * Step 2 — which email each recipient gets and which fee prices it.
 *
 * The two changes carry different risks, so they are warned about separately: the wrong
 * email can make a member pay twice, the wrong fee bills the wrong amount.
 */
defineOptions({name: "PaymentEmailFeesStep"})
const {narrow} = useNarrowLayout()

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
    v-if="!narrow"
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
          Last payment email
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
            class="text-caption text-medium-emphasis"
            :data-testid="`payment-emails-last-ask-${row.userId}`"
          >{{ formatBulkDate(lastAskedOn(row)) }}</span>
        </td>
      </tr>
    </tbody>
  </v-table>

  <v-list
    v-else
    data-testid="payment-emails-fees-list"
    density="compact"
  >
    <template
      v-for="(row, index) in rows"
      :key="row.userId"
    >
      <v-list-item :data-testid="`payment-emails-fee-row-${row.userId}`">
        <v-list-item-title class="font-weight-medium text-truncate">
          {{ row.name }}
        </v-list-item-title>

        <!-- Said in words: under a column header an em dash means no, but in a list an
             absent icon means nothing at all. -->
        <div
          class="text-caption"
          :class="paysByDirectDebit(row) ? 'text-primary' : 'text-medium-emphasis'"
          :data-testid="`payment-emails-mandate-${row.userId}`"
        >
          {{ paysByDirectDebit(row) ? "Pays by direct debit" : "No direct-debit mandate" }}
        </div>

        <!-- Stacked and labelled: side by side neither fits, and without the column
             headers a bare value does not say which choice it is. -->
        <v-select
          class="payment-email-select"
          :data-testid="`payment-emails-kind-${row.userId}`"
          density="compact"
          hide-details
          item-title="title"
          item-value="value"
          :items="contributionEmailItems"
          label="Email type"
          :model-value="kindFor(row, kinds)"
          variant="plain"
          @update:model-value="(v) => setKind(row.userId, v)"
        />
        <v-select
          class="payment-email-select"
          :data-testid="`payment-emails-feetype-${row.userId}`"
          density="compact"
          hide-details
          item-title="title"
          item-value="value"
          :items="feeTypeItems"
          label="Fee type"
          :model-value="fees[row.userId] ?? row.recommendedFeeType"
          variant="plain"
          @update:model-value="(v) => setFee(row.userId, v)"
        />

        <v-list-item-subtitle class="text-caption">
          <span
            v-if="rowAmount(row) != null"
            :data-testid="`payment-emails-amount-${row.userId}`"
          >€ {{ rowAmount(row)!.toFixed(2) }}</span>
          <span class="mx-1">·</span>
          Last payment email
          <span :data-testid="`payment-emails-last-ask-${row.userId}`">{{
            formatBulkDate(lastAskedOn(row))
          }}</span>
        </v-list-item-subtitle>

        <div
          v-if="isSwitched(row, kinds)"
          class="text-caption text-warning d-flex align-center ga-1"
          :data-testid="`payment-emails-switched-${row.userId}`"
        >
          <v-icon
            icon="mdi-alert-outline"
            size="14"
          />
          <span class="text-truncate">{{ switchedNote(row) }}</span>
        </div>
      </v-list-item>
      <v-divider v-if="index < rows.length - 1" />
    </template>
  </v-list>

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
