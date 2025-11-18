<script lang="ts" setup>
import {computed} from "vue"
import {useStore} from "vuex"
import {Form} from "vee-validate"
import VvField from "@/components/form/fields/VvField.vue"
import "flag-icons/css/flag-icons.min.css"
import "v-phone-input/dist/v-phone-input.css"
import {VPhoneInput} from "v-phone-input"
import type {Guest} from "@/services/api"
import {useCountry, useVeeForm} from "@/composables/formUtils"

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

const {country, onCountryUpdate} = useCountry("NL")
const {formRef, validate} = useVeeForm()

defineExpose({validate})
</script>

<template>
  <Form
    v-if="!isLoggedIn"
    ref="formRef"
    as="div"
    class="mb-2"
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
          label="Full name*"
          name="name"
          rules="required"
        />
      </v-col>
      <v-col
        cols="12"
        md="6"
      >
        <VvField
          v-model="guest.discord"
          label="Discord username*"
          name="discord"
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
          :component-props="{ hint: `We'll use this to send you a link you can use to edit your sign-up form later` }"
          label="Email*"
          name="email"
          rules="required|email|noStudentEmail"
        />
      </v-col>

      <v-col
        cols="12"
        md="6"
      >
        <VvField
          v-model="guest.phoneNumber"
          :component="VPhoneInput"
          :component-props="{
            defaultCountry: 'NL',
            countryIconMode: 'svg',
            mode: 'international',
            placeholder: 'Phone Number'
          }"
          :rules="`required|phoneMobile:${country}`"
          label="Phone Number*"
          name="phoneNumber"
          @update:country="onCountryUpdate"
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
