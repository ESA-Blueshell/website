<template>
  <v-main>
    <top-banner title="My address" />
    <div class="mx-3">
      <div
        class="mx-auto my-10"
        style="max-width: 800px"
      >
        <p class="mb-6">
          Here you can edit your address details. This address may be used for communication and billing purposes,
          so please make sure it is correct before saving.
        </p>
        <address-form
          v-model="address"
          :user-id="login.userId"
          show-submit
          submit-text="Save address"
        />
      </div>
    </div>
  </v-main>
</template>


<script lang="ts" setup>
import {computed, onMounted, ref} from "vue"
import {useStore} from "vuex"
import {useRoute} from "vue-router"
import TopBanner from "@/components/common/banners/TopBanner.vue"
import {$handleNetworkError} from "@/plugins/handleNetworkError.ts"
import {type Address, findAddressById, type Login} from "@/services/api"
import AddressForm from "@/components/form/AddressForm.vue"

const address = ref<Address>()
const store = useStore()
const route = useRoute()

const login = computed<Login>(() => store.getters.getLogin)

onMounted(async () => {
  const login = store.getters.getLogin
  if (!login) return

  try {
    const addressId = route.params.id as string | undefined

    if (addressId) {
      const addressResponse = await findAddressById({
        path: {
          id: Number(addressId),
        },
        throwOnError: true,
      })
      address.value = addressResponse.data!
    }
  } catch (e) {
    $handleNetworkError(e)
  }
})
</script>
