<script lang="ts">
/** Where the money goes, as far as the reader may see it: the board gets it whole. */
export interface PayTo {
  holder: string | null
  iban: string
  bic: string | null
}
</script>

<script lang="ts" setup>
import {computed} from "vue"
import {
  day,
  type Declaration,
  DeclarationState,
  euros,
  isoDay,
  sentBack,
  stepAt,
  totalOf,
} from "../declarations"

/**
 * A declaration as the paper form laid it out, which is what the board files and prints. The
 * editor previews it while it is filled in.
 */
defineOptions({name: "DeclarationSheet"})

const {declaration, payTo} = defineProps<{
  declaration: Pick<Declaration, "submitter" | "expenses" | "receipts" | "confirmed" | "state" | "steps">
  /** The submitter's bank account as it is now, which is where the money goes. */
  payTo: PayTo | null
}>()

const total = computed(() => totalOf(declaration.expenses))
const submitted = computed(() => declaration.state === DeclarationState.DRAFT ? null : stepAt(declaration, DeclarationState.SUBMITTED))
const approved = computed(() => stepAt(declaration, DeclarationState.APPROVED))
const paid = computed(() => stepAt(declaration, DeclarationState.PAID))
const rejected = computed(() => declaration.state === DeclarationState.REJECTED ? stepAt(declaration, DeclarationState.REJECTED) : null)
const returned = computed(() => sentBack(declaration))

</script>

<template>
  <div
    class="sheet"
    data-testid="declaration-sheet"
  >
    <article class="sheet__page">
      <!-- Not an h2: the site's print styles set headings in the display face. -->
      <p
        aria-level="2"
        class="sheet__title"
        role="heading"
      >
        Declaration form Blueshell E-Sports Vereniging Enschede
      </p>

      <div class="sheet__parties">
        <dl class="sheet__submitter">
          <dt>Submitted by</dt>
          <dd>{{ declaration.submitter }}</dd>
          <template v-if="payTo?.holder">
            <dt>Account holder</dt>
            <dd>{{ payTo.holder }}</dd>
          </template>
          <dt>IBAN</dt>
          <dd>{{ payTo?.iban || "Not added" }}</dd>
          <template v-if="payTo?.bic">
            <dt>BIC</dt>
            <dd>{{ payTo.bic }}</dd>
          </template>
        </dl>
        <address class="sheet__association">
          <strong>Blueshell E-Sports Vereniging Enschede</strong><br>
          Bastille 49, Postbus 217<br>
          7500 AE Enschede<br>
          Netherlands
        </address>
      </div>

      <table class="sheet__table">
        <thead>
          <tr>
            <th scope="col">
              Date
            </th>
            <th scope="col">
              Description
            </th>
            <th scope="col">
              Committee or event
            </th>
            <th
              class="sheet__amount"
              scope="col"
            >
              Amount
            </th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="expense in declaration.expenses"
            :key="expense.id"
          >
            <td class="sheet__date">
              {{ expense.paidOn ? isoDay(expense.paidOn) : "" }}
            </td>
            <td>
              {{ expense.description }}
              <span
                v-if="expense.receipts.length > 1"
                class="sheet__receipts"
              >{{ expense.receipts.length }} receipts</span>
            </td>
            <td>{{ expense.for?.name ?? "" }}</td>
            <td class="sheet__amount">
              {{ euros(Number.parseFloat(expense.amount || "0") || 0) }}
            </td>
          </tr>
          <tr v-if="declaration.expenses.length === 0">
            <td
              class="sheet__empty"
              colspan="4"
            >
              No expenses yet.
            </td>
          </tr>
        </tbody>
        <tfoot>
          <tr>
            <th
              colspan="3"
              scope="row"
            >
              Total
            </th>
            <td class="sheet__amount">
              {{ euros(total) }}
            </td>
          </tr>
        </tfoot>
      </table>
      <p
        v-if="declaration.receipts.length"
        class="sheet__receipts sheet__whole"
      >
        {{ declaration.receipts.length === 1 ? "1 receipt" : `${declaration.receipts.length} receipts` }} for the whole declaration
      </p>

      <dl class="sheet__signed">
        <dt>Statement</dt>
        <dd v-if="submitted">
          Submitted on {{ day(submitted.at) }} by {{ submitted.by }}. They said the expenses were for
          Blueshell and the receipts are real.
        </dd>
        <dd v-else>
          {{ declaration.confirmed ? "Ticked. Not submitted yet." : "Not submitted yet." }}
        </dd>
        <dt>Board</dt>
        <dd v-if="rejected">
          Rejected on {{ day(rejected.at) }} by {{ rejected.by }}: {{ rejected.reason }}
        </dd>
        <dd v-else-if="returned">
          Sent back on {{ day(returned.at) }} by {{ returned.by }}: {{ returned.reason }}
        </dd>
        <dd v-else-if="approved">
          Approved on {{ day(approved.at) }} by {{ approved.by }}.
          <template v-if="paid">
            Paid on {{ day(paid.at) }}.
          </template>
        </dd>
        <dd v-else>
          Not approved yet.
        </dd>
      </dl>
    </article>
  </div>
</template>

<style scoped>
/* Paper in either theme, since this is the page the board prints. */
.sheet {
  --sheet-ink: #1c1c1c;
  --sheet-rule: #1c1c1c;
  --sheet-faint: #666;
  display: flex;
  flex-direction: column;
  gap: 1rem;
  color: var(--sheet-ink);
  font-family: Verdana, "DejaVu Sans", sans-serif;
  font-size: 11px;
  line-height: 1.45;
}

.sheet__page {
  padding: 2.2rem 2rem;
  background: #fff;
  box-shadow: 0 1px 4px rgb(0 0 0 / 0.25);
}

.sheet__title {
  padding-bottom: 0.9rem;
  margin: 0 0 1rem;
  border-bottom: 3px solid var(--sheet-rule);
  font-family: inherit;
  font-size: 15px;
  letter-spacing: normal;
  text-transform: none;
  font-weight: 700;
}

.sheet__parties {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 1rem;
  margin-bottom: 1.4rem;
}

.sheet__submitter {
  display: grid;
  grid-template-columns: max-content minmax(0, 1fr);
  gap: 0.1rem 0.8rem;
  margin: 0;
}

.sheet__submitter dt {
  font-weight: 700;
}

.sheet__submitter dd {
  margin: 0;
  overflow-wrap: anywhere;
}

.sheet__association {
  font-style: normal;
}

.sheet__table {
  width: 100%;
  border-collapse: collapse;
}

.sheet__table th,
.sheet__table td {
  padding: 0.35rem 0.45rem;
  border: 1px solid var(--sheet-rule);
  text-align: left;
  vertical-align: top;
}

.sheet__table thead th {
  font-weight: 400;
}

.sheet__table tfoot th {
  text-align: right;
}

.sheet__date {
  white-space: nowrap;
}

.sheet__amount {
  text-align: right !important;
  white-space: nowrap;
}

.sheet__receipts {
  display: block;
  color: var(--sheet-faint);
}

.sheet__whole {
  margin: 0.4rem 0 0;
}

.sheet__empty {
  color: var(--sheet-faint);
}

.sheet__signed {
  display: grid;
  grid-template-columns: max-content minmax(0, 1fr);
  gap: 0.5rem 0.8rem;
  margin: 1.6rem 0 0;
}

.sheet__signed dt {
  font-weight: 700;
}

.sheet__signed dd {
  margin: 0;
}

@media print {
  .sheet {
    gap: 0;
  }

  .sheet__page {
    padding: 0;
    box-shadow: none;
  }
}
</style>
