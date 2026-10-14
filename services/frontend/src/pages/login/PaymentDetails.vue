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
import {computed} from "vue"
import {useStore} from "vuex"
import IncassoSetUp from "@/components/account/IncassoSetUp.vue"
import ManualPayment from "@/components/account/ManualPayment.vue"
import OwnContributions from "@/components/account/OwnContributions.vue"
import SectionTitle from "@/components/account/SectionTitle.vue"
import AccountFrame from "@/components/common/AccountFrame.vue"

defineOptions({name: "AccountPaymentDetails"})

const store = useStore()
const login = computed(() => store.getters.getLogin)
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
