<script lang="ts" setup>
import NoticeBox from "@/components/island/NoticeBox.vue"
import FormFields from "@/components/island/FormFields.vue"
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
      <VvField
        v-model="guest.name"
        label="Full name*"
        name="name"
        test-id="guest-form-name"
        rules="required"
      />
      <VvField
        v-model="guest.discord"
        label="Discord username*"
        name="discord"
        test-id="guest-form-discord"
        rules="required"
      />
    </form-fields>

    <form-fields>
      <VvField
        v-model="guest.email"
        test-id="guest-form-email"
        :component-props="{ hint: `We'll use this to send you a link you can use to edit your sign-up form later` }"
        label="Email*"
        name="email"
        rules="required|email"
      />
      <VvField
        v-model="guest.phoneNumber"
        test-id="guest-form-phone"
        :component-props="{kind: 'phone', defaultCountry: 'NL'}"
        :rules="`required|phoneMobile:${country}`"
        label="Phone Number*"
        name="phoneNumber"
        @update:country="onCountryUpdate"
      />
    </form-fields>
  </Form>
</template>

<style lang="scss" scoped>
.guest-form__notice {
  margin-bottom: 1rem;
}

.form-fields + .form-fields {
  margin-top: 0.5rem;
}
</style>
