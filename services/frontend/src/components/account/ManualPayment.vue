<script lang="ts" setup>
/* How a member pays a contribution by hand: the same two ways the payment emails name, with the
   association's account as the api holds it. */
import {onMounted, ref} from "vue"
import {type BankAccount, readBankAccount} from "@/domains/contribution"

defineOptions({name: "ManualPayment"})

const bank = ref<BankAccount | null>(null)

onMounted(async () => {
  bank.value = await readBankAccount()
})
</script>

<template>
  <div
    class="manual-payment"
    data-testid="manual-payment"
  >
    <p>
      Without incasso you pay each contribution yourself. The treasurer emails you a payment request with the amount and
      the date to pay by, and you pay it in one of these ways.
    </p>
    <dl class="manual-payment__ways">
      <dt>Bank transfer</dt>
      <dd
        v-if="bank"
        data-testid="manual-payment-bank"
      >
        Transfer the amount to {{ bank.iban }}, in the name of {{ bank.accountName }}. For a foreign bank account the
        BIC code is {{ bank.bic }}. Put your name, your student number if you have one and "contribution" with the
        academic year in the description.
      </dd>
      <dd
        v-else
        data-testid="manual-payment-bank-unread"
      >
        The payment request names the account to transfer to and what to put in the description.
      </dd>
      <dt>Cash</dt>
      <dd>
        Put the money in an envelope, write your name, your student number if you have one and "contribution" with the
        academic year on it, and put it in postbus 49 in the Bastille.
      </dd>
    </dl>
  </div>
</template>

<style scoped>
.manual-payment {
  display: flex;
  flex-direction: column;
  gap: 0.8rem;
}

.manual-payment__ways {
  display: grid;
  grid-template-columns: max-content minmax(0, 1fr);
  gap: 0.6rem 1.4rem;
  margin: 0;
}

.manual-payment__ways dt {
  font-weight: 600;
}

.manual-payment__ways dd {
  margin: 0;
}

@media (--phone) {
  .manual-payment__ways {
    grid-template-columns: minmax(0, 1fr);
    gap: 0.2rem;
  }

  .manual-payment__ways dd {
    margin-bottom: 0.6rem;
  }
}
</style>
