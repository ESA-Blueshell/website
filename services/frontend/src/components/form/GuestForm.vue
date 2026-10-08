<script lang="ts" setup>
import NoticeBox from "@/components/island/NoticeBox.vue"
import FormFields from "@/components/island/FormFields.vue"
import {computed, reactive} from "vue"
import {useStore} from "vuex"
import FormControl from "@/components/island/FormControl.vue"
import type {CreateGuestRequest, GuestResponse} from "@/domains/events"
import {useCountry} from "@/composables/formUtils"
import {type FieldChecks, useFormChecks} from "@/composables/useFormChecks"
import {email, phoneMobile, required} from "@/utils/checks"

type GuestFormModel = CreateGuestRequest & Partial<GuestResponse>

// A default handed to an unbound v-model stays raw, so its checks would never see an edit.
const guest = defineModel<GuestFormModel>({
  default: () => reactive({
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
const checks = useFormChecks((): Record<string, FieldChecks> => (shown.value
  ? {
    name: {value: () => guest.value.name, checks: [required]},
    discord: {value: () => guest.value.discord, checks: [required]},
    email: {value: () => guest.value.email, checks: [required, email]},
    phoneNumber: {value: () => guest.value.phoneNumber, checks: [required, phoneMobile(() => country.value)]},
  }
  : {}))
const {errorsOf, touch} = checks
const validate = async (): Promise<boolean> => checks.attempt()

defineExpose({validate})
</script>

<template>
  <div
    v-if="shown"
    class="mb-2"
  >
    <notice-box
      v-if="!force"
      class="guest-form__notice"
      testid="guest-form-signed-out"
      title="You are not signed in"
      tone="info"
    >
      <p>You can still sign up for this event, but we need a few details from you.</p>
    </notice-box>

    <form-fields>
      <div data-testid="guest-form-name">
        <form-control
          v-model="guest.name"
          :error-messages="errorsOf('name')"
          label="Full name*"
          @blur="touch('name')"
        />
      </div>
      <div data-testid="guest-form-discord">
        <form-control
          v-model="guest.discord"
          :error-messages="errorsOf('discord')"
          label="Discord username*"
          @blur="touch('discord')"
        />
      </div>
    </form-fields>

    <form-fields>
      <div data-testid="guest-form-email">
        <form-control
          v-model="guest.email"
          :error-messages="errorsOf('email')"
          hint="We'll use this to send you a link you can use to edit your sign-up form later"
          label="Email*"
          @blur="touch('email')"
        />
      </div>
      <div data-testid="guest-form-phone">
        <form-control
          v-model="guest.phoneNumber"
          default-country="NL"
          :error-messages="errorsOf('phoneNumber')"
          kind="phone"
          label="Phone Number*"
          @blur="touch('phoneNumber')"
          @update:country="onCountryUpdate"
        />
      </div>
    </form-fields>
  </div>
</template>

<style lang="scss" scoped>
.guest-form__notice {
  margin-bottom: 1rem;
}

.form-fields + .form-fields {
  margin-top: 0.5rem;
}
</style>
