<script lang="ts" setup>
import {computed, ref, type Ref} from "vue"
import {useStore} from "vuex"
import {Form, type FormContext} from "vee-validate"
import VvField from "@/components/form/fields/VvField.vue"

import "flag-icons/css/flag-icons.min.css"
import "v-phone-input/dist/v-phone-input.css"
import {VPhoneInput} from "v-phone-input"
import type {CountryCode} from "libphonenumber-js/max"
import type {Guest} from "@/services/api"

const guest = defineModel<Guest>({
  default: () => ({
    name: "",
    discord: "",
    email: "",
    phoneNumber: "",
  }),
})

const store = useStore()
const isLoggedIn = computed<boolean>(() => store.getters.isLoggedIn)

const country: Ref<CountryCode> = ref("NL")
const updateCountry = (newCountry: string): void => {
  country.value = newCountry as CountryCode
}

const formRef = ref<FormContext>()
const isSaving = ref<boolean>(false)

const validate = async (): Promise<boolean> => {
  const result = await formRef.value?.validate()
  return !!result?.valid
}

defineExpose({validate})
</script>

<template>
  <Form
    v-if="!isLoggedIn"
    ref="formRef"
    as="div"
    class="mb-4"
  >
    <v-alert
      class="mb-4"
      text="It seems you are not logged in. You can still sign up for this event, but we'll need some extra info from you."
      type="info"
      variant="outlined"
    />

    <v-row>
      <v-col
        cols="12"
        md="6"
      >
        <VvField
          v-model="guest.name"
          name="name"
          label="Full name*"
          rules="required"
        />
      </v-col>

      <v-col
        cols="12"
        md="6"
      >
        <VvField
          v-model="guest.discord"
          name="discord"
          label="Discord username*"
          rules="required"
        />
      </v-col>
    </v-row>

    <v-row>
      <v-col
        cols="12"
        md="6"
      >
        <VvField
          v-model="guest.email"
          name="email"
          label="Email*"
          rules="required|email|noStudentEmail"
          :component-props="{
            hint: `We'll use this to send you a link you can use to edit your sign-up form later`
          }"
        />
      </v-col>

      <v-col
        cols="12"
        md="6"
      >
        <VvField
          v-model="guest.phoneNumber"
          name="phoneNumber"
          label="Phone Number*"
          :rules="`required|phoneMobile:${country}`"
          :component="VPhoneInput"
          :component-props="{
            defaultCountry: 'NL',
            countryIconMode: 'svg',
            mode: 'international',
            placeholder: 'Phone Number'
          }"
          @update:country="updateCountry"
        />
      </v-col>
    </v-row>
  </Form>
</template>

<style lang="scss" scoped>
.v-checkbox .v-selection-control {
  min-height: 40px !important;
}
</style>
