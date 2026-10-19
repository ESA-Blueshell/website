<template>
  <account-frame heading="Payment details">
    <div class="mx-3">
      <div
        class="payment-details mx-auto my-10"
        style="max-width: 800px"
      >
        <p>
          Here you set up or change how you pay your contribution, and see which contributions you have paid.
        </p>
        <template v-if="login">
          <section
            class="payment-details__part"
            data-testid="payment-details-bank-account"
          >
            <section-title>Bank account</section-title>
            <cut-row
              :meta="account ? `Added ${formatDay(account.addedAt)}. Declarations are paid into it, and incasso collects from it.` : 'Add the bank account your declarations are paid into.'"
              testid="payment-details-bank-account-row"
              :title="account ? account.preview : 'Bank account'"
              to="/account/payment-details/bank-account"
            >
              <template #glyph>
                <bank-glyph />
              </template>
              <template #end>
                <state-tag :tone="account ? 'ok' : 'warn'">
                  {{ account ? "Added" : "Not added" }}
                </state-tag>
              </template>
            </cut-row>
          </section>
          <section
            class="payment-details__part"
            data-testid="payment-details-method"
          >
            <section-title>Payment method</section-title>
            <cut-row
              :meta="methodNote"
              testid="payment-details-method-row"
              :title="standing ? 'Direct debit' : 'Bank transfer or cash'"
              to="/account/payment-details/payment-method"
            >
              <template #glyph>
                <bank-glyph />
              </template>
            </cut-row>
          </section>
          <section
            id="how-to-pay"
            class="payment-details__part"
            data-testid="payment-manual"
          >
            <section-title>How to pay</section-title>
            <manual-payment />
          </section>
          <own-contributions class="payment-details__part" />
        </template>
      </div>
    </div>
  </account-frame>
</template>

<script lang="ts" setup>
import {computed, nextTick, onMounted, ref} from "vue"
import {useRoute} from "vue-router"
import {useStore} from "vuex"
import CutRow from "@/components/island/CutRow.vue"
import StateTag from "@/components/island/StateTag.vue"
import {type BankAccountPreview, BankGlyph, readOwnBankAccount} from "@/domains/banking"
import {IncassoStanding, type OwnMandateResponse, readOwnMandate} from "@/domains/user"
import {formatDay} from "@/utils/timestamps"
import ManualPayment from "@/components/account/ManualPayment.vue"
import OwnContributions from "@/components/account/OwnContributions.vue"
import SectionTitle from "@/components/account/SectionTitle.vue"
import AccountFrame from "@/components/common/AccountFrame.vue"

defineOptions({name: "AccountPaymentDetails"})

const store = useStore()
const login = computed(() => store.getters.getLogin)
const route = useRoute()
const account = ref<BankAccountPreview | null>(null)
const own = ref<OwnMandateResponse | null>(null)
// A paper mandate may stand before its account is on file: the member pays by direct debit, with nothing to collect from yet.
const standing = computed(() => !!own.value && own.value.standing !== IncassoStanding.NONE)
const methodNote = computed(() => {
  if (own.value?.standing === IncassoStanding.MANDATE_RECORDED) {
    return `Direct debit from ${own.value.ibanCountry} •• … ••${own.value.ibanLastTwo} since ${formatDay(own.value.signedOn)}.`
  }
  return standing.value
    ? "Direct debit. Add a bank account so we can collect your contribution."
    : "You pay by bank transfer or in cash. Set up direct debit to pay automatically."
})
onMounted(async () => {
  const [held, signed] = await Promise.all([readOwnBankAccount(), readOwnMandate()])
  account.value = held
  own.value = signed
  // The router does not scroll to an anchor on a page that renders its sections after loading.
  if (route.hash) {
    await nextTick()
    document.querySelector(route.hash)?.scrollIntoView({block: "start"})
  }
})
</script>

<style scoped>
.payment-details {
  display: flex;
  flex-direction: column;
  gap: 2.2rem;
}

.payment-details__part {
  display: flex;
  flex-direction: column;
  gap: 0.8rem;
}
</style>
