<template>
  <account-frame heading="Payment details">
    <div class="mx-3">
      <div
        class="mx-auto my-10"
        style="max-width: 800px"
      >
        <p class="mb-6">
          Here you set up or change how you pay your contribution. With incasso the association collects it from your
          bank account once a contribution period, and emails you before it does.
        </p>
        <incasso-set-up
          v-if="login"
          :address-id="login.addressId ?? null"
        />
      </div>
    </div>
  </account-frame>
</template>

<script lang="ts" setup>
import {computed} from "vue"
import {useStore} from "vuex"
import IncassoSetUp from "@/components/account/IncassoSetUp.vue"
import AccountFrame from "@/components/common/AccountFrame.vue"

defineOptions({name: "AccountPaymentDetails"})

const store = useStore()
const login = computed(() => store.getters.getLogin)
</script>
