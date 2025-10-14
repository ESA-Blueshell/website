<script lang="ts" setup>
import {computed, type Ref, ref} from "vue"
import {useStore} from "vuex"
import {Field, Form} from "vee-validate"
import type {VForm} from "vuetify/lib/components"

const store = useStore()
const isLoggedIn = computed<boolean>(() => store.getters.isLoggedIn)
const guestForm: Ref<VForm | undefined> = ref()

/**
 * If the user is not logged in, we allow them to enter temporary guest data
 */
const guestData = ref(
  store.getters.getGuestData ?? {
    name: "",
    discord: "",
    email: "",
  },
)

</script>

<template>
  <Form
    v-if="!isLoggedIn"
    ref="guestForm"
    as="div"
    class="mb-4"
  >
    <v-alert
      class="mb-4"
      text="It seems you are not logged in. You can still sign up for this event, but we'll need some extra info from you."
      type="info"
      variant="outlined"
    />
    <Field
      v-slot="{ value, errors, handleChange, handleBlur }"
      v-model="guestData.name"
      name="name"
      rules="required"
    >
      <v-text-field
        :error-messages="errors"
        :model-value="value"
        label="Name"
        @blur="handleBlur"
        @update:model-value="handleChange"
      />
    </Field>
    <Field
      v-slot="{ value, errors, handleChange, handleBlur }"
      v-model="guestData.discord"
      name="discord"
      rules="required"
    >
      <v-text-field
        :error-messages="errors"
        :model-value="value"
        label="Discord username"
        @blur="handleBlur"
        @update:model-value="handleChange"
      />
    </Field>
    <Field
      v-slot="{ value, errors, handleChange, handleBlur }"
      v-model="guestData.email"
      name="email"
      rules="required|email|noStudentEmail"
    >
      <v-text-field
        :error-messages="errors"
        :model-value="value"
        hint="We'll use this to send you a link you can use to edit your sign-up form later"
        label="Email"
        @blur="handleBlur"
        @update:model-value="handleChange"
      />
    </Field>
  </Form>
</template>

<style lang="scss" scoped>
.v-checkbox .v-selection-control {
  min-height: 40px !important;
}
</style>
