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
            data-testid="payment-automated"
          >
            <section-title>Automated payment</section-title>
            <incasso-set-up :address-id="login.addressId ?? null" />
          </section>
          <section
            class="payment-details__part"
            data-testid="payment-manual"
          >
            <section-title>Manual payment</section-title>
            <manual-payment />
          </section>
          <own-contributions class="payment-details__part" />
        </template>
      </div>
    </div>
  </account-frame>
</template>

<script lang="ts" setup>
import {computed, onMounted, ref} from "vue"
import {useStore} from "vuex"
import CutRow from "@/components/island/CutRow.vue"
import StateTag from "@/components/island/StateTag.vue"
import {type BankAccountPreview, BankGlyph, readOwnBankAccount} from "@/domains/banking"
import {formatDay} from "@/utils/timestamps"
import IncassoSetUp from "@/components/account/IncassoSetUp.vue"
import ManualPayment from "@/components/account/ManualPayment.vue"
import OwnContributions from "@/components/account/OwnContributions.vue"
import SectionTitle from "@/components/account/SectionTitle.vue"
import AccountFrame from "@/components/common/AccountFrame.vue"

defineOptions({name: "AccountPaymentDetails"})

const store = useStore()
const login = computed(() => store.getters.getLogin)
const account = ref<BankAccountPreview | null>(null)
onMounted(async () => {
  account.value = await readOwnBankAccount()
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
