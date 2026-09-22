<script lang="ts" setup>
import {computed} from "vue"
import {useStore} from "vuex"
import {Form} from "vee-validate"
import VvField from "@/components/form/fields/VvField.vue"
import type {CreateGuestRequest, GuestResponse} from "@/domains/events"
import {useCountry, useVeeForm} from "@/composables/formUtils"

type GuestFormModel = CreateGuestRequest & Partial<GuestResponse>

const guest = defineModel<GuestFormModel>({
  default: () => ({
    name: "",
    discord: "",
    email: "",
    phoneNumber: "",
  }),
})

/** A board member editing somebody else's guest details is logged in, and still needs the fields. */
const props = withDefaults(defineProps<{force?: boolean}>(), {force: false})

const store = useStore()
const isLoggedIn = computed<boolean>(() => store.getters.isLoggedIn)
const shown = computed<boolean>(() => props.force || !isLoggedIn.value)

const {country, onCountryUpdate} = useCountry("NL")
const {formRef, validate} = useVeeForm()

defineExpose({validate})
</script>

<template>
  <Form
    v-if="shown"
    ref="formRef"
    as="div"
    class="mb-2"
  >
    <v-alert
      v-if="!force"
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
          test-id="guest-form-name"
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
          test-id="guest-form-discord"
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
          test-id="guest-form-email"
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
          test-id="guest-form-phone"
          component="VPhoneInput"
          :component-props="{
            defaultCountry: 'NL',
            mode: 'international',
            placeholder: 'Phone Number',
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
