<script lang="ts" setup>
import {DateTime} from "luxon"
import {computed, onMounted, ref} from "vue"
import AccountFrame from "@/components/common/AccountFrame.vue"
import CutButton from "@/components/island/CutButton.vue"
import CutRow from "@/components/island/CutRow.vue"
import FactList, {type Fact} from "@/components/island/FactList.vue"
import StateTag from "@/components/island/StateTag.vue"
import {
  type BankAccountResponse,
  contributionRow,
  euros,
  noteHeld,
  owedIn,
  type OwnContributions,
  readBankAccount,
  readOwnContributions,
} from "@/domains/contribution"

defineOptions({name: "PaymentDetailsPage"})

const BOARD_EMAIL = "board@blueshell.utwente.nl"

const own = ref<OwnContributions | null>(null)
const bank = ref<BankAccountResponse | null>(null)
const loaded = ref(false)

const rows = computed(() => (own.value?.periods ?? []).map(period => ({id: period.periodId, ...contributionRow(period)})))
const owed = computed(() => owedIn(own.value?.periods ?? []))
const lastPaid = computed(() =>
  (own.value?.periods ?? []).map(period => period.paidAt).filter((at): at is string => Boolean(at)).sort().at(-1))

const facts = computed<Fact[]>(() => [
  {
    label: "To pay",
    value: owed.value.count === 0 ? "Nothing" : euros(owed.value.total),
    sub: owed.value.count === 0 ? undefined : owed.value.count === 1 ? "1 unpaid contribution" : `${owed.value.count} unpaid contributions`,
    testid: "payment-details-owed",
  },
  {
    label: "Pays by",
    value: own.value?.paysByIncasso ? "Direct debit" : "Transfer or cash",
    testid: "payment-details-method",
  },
  {
    label: "Last paid",
    value: lastPaid.value ? DateTime.fromISO(lastPaid.value).toFormat("d LLLL yyyy") : "Nothing yet",
    testid: "payment-details-last-paid",
  },
])

onMounted(async () => {
  const [read, account] = await Promise.all([readOwnContributions(), readBankAccount()])
  own.value = read
  bank.value = account
  if (read) noteHeld(read)
  loaded.value = true
})
</script>

<template>
  <account-frame
    heading="Payment details"
    island-content
  >
    <template v-if="loaded">
      <p
        v-if="!own"
        class="payment__note"
        data-testid="payment-details-unread"
      >
        Your payment details could not be loaded. Try again later.
      </p>

      <p
        v-else-if="!own.held"
        class="payment__note"
        data-testid="payment-details-none"
      >
        You are not a member, so there is nothing to pay.
        <router-link to="/membership">
          Become a member
        </router-link>
      </p>

      <template v-else>
        <fact-list
          class="payment__facts"
          :facts="facts"
        />

        <section
          class="payment__section"
          data-testid="payment-details-contributions"
        >
          <h2 class="payment__title">
            Your contributions
          </h2>
          <p
            v-if="rows.length === 0"
            class="payment__note"
            data-testid="payment-details-no-periods"
          >
            Nothing yet. Your first contribution shows here once the board adds this year's contribution period.
          </p>
          <div
            v-else
            class="payment__rows"
          >
            <cut-row
              v-for="row in rows"
              :key="row.id"
              :meta="row.meta"
              :testid="`payment-details-period-${row.id}`"
              :title="row.years"
            >
              <template #end>
                <state-tag :tone="row.tone">
                  {{ row.state }}
                </state-tag>
              </template>
            </cut-row>
          </div>
        </section>

        <section
          class="payment__section"
          data-testid="payment-details-how"
        >
          <h2 class="payment__title">
            How to pay
          </h2>
          <p
            v-if="own.paysByIncasso"
            class="payment__lead"
            data-testid="payment-details-incasso"
          >
            You pay by direct debit. To stop it, email the board.
          </p>
          <dl class="payment__ways">
            <dt>Bank transfer</dt>
            <dd data-testid="payment-details-bank">
              <template v-if="bank">
                Transfer to {{ bank.iban }}, in the name of {{ bank.accountName }}. From a foreign bank account, the BIC is {{ bank.bic }}.
              </template>
              <template v-else>
                The account could not be loaded. Email the board to ask for it.
              </template>
              In the description, put your name, your student number if you have one and "contribution" with the year.
            </dd>
            <dt>Cash</dt>
            <dd>
              Put the money in an envelope with your name, your student number if you have one and "contribution" with the year on it. Drop it in postbus 49 in the Bastille.
            </dd>
            <template v-if="!own.paysByIncasso">
              <dt>Direct debit</dt>
              <dd data-testid="payment-details-switch">
                Fill in the mandate form from <router-link to="/documents">
                  Documents
                </router-link> and email it to the board.
                It may not cover what you owe now. Pay that by transfer or in cash, unless the board tells you otherwise.
              </dd>
            </template>
          </dl>
          <cut-button
            class="payment__ask"
            :href="`mailto:${BOARD_EMAIL}`"
            testid="payment-details-email-board"
          >
            Email the board
          </cut-button>
        </section>
      </template>
    </template>
  </account-frame>
</template>

<style scoped>
.payment__facts {
  padding: 1.6rem 0 1.8rem;
}

.payment__section + .payment__section {
  margin-top: 2.5rem;
}

.payment__title {
  font-family: var(--font-body);
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 0.3em;
  text-transform: uppercase;
  color: var(--color-eyebrow);
}

.payment__rows {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin-top: 0.9rem;
}

.payment__note {
  margin-top: 1.5rem;
  color: var(--color-ash);
}

.payment__section .payment__note {
  margin-top: 0.9rem;
}

.payment__lead {
  margin-top: 0.9rem;
  font-weight: 600;
}

.payment__ways {
  display: grid;
  grid-template-columns: max-content minmax(0, 1fr);
  gap: 0.6rem 1.4rem;
  margin: 0.9rem 0 0;
}

.payment__ways dt {
  font-weight: 600;
}

.payment__ways dd {
  margin: 0;
}

.payment__ask {
  margin-top: 1.4rem;
}

@media (--phone) {
  .payment__ways {
    grid-template-columns: minmax(0, 1fr);
    gap: 0.2rem;
  }

  .payment__ways dd {
    margin-bottom: 0.6rem;
  }
}
</style>
