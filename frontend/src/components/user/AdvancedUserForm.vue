<template>
  <div>
    <!-- Provide a VeeValidate form context (render as a div to avoid nested <form> tags) -->
    <Form as="div">
      <v-form
        ref="form"
        v-model="valid"
      >
        <SimpleUserForm
          ref="simpleRef"
          :model-value="simpleModel"
          :show-passwords="creating"
          @update:model-value="(val: SimpleUser) => simpleModel = val"
        />

        <v-row class="mt-10">
          <v-col cols="12">
            <Field
              v-slot="{ value, errors, handleChange, handleBlur }"
              v-model="userData.phoneNumber"
              :rules="`required|phoneMobile:${country}`"
              name="phoneNumber"
            >
              <v-phone-input
                ref="phoneInput"
                :default-country="'NL'"
                :disabled="disableEdit && !creating"
                :error-messages="errors"
                :model-value="value"
                country-icon-mode="svg"
                label="Phone Number"
                mode="international"
                placeholder="Phone Number"
                @blur="handleBlur"
                @update:model-value="handleChange"
                @update:country="updateCountry"
              />
            </Field>
          </v-col>
        </v-row>

        <v-row>
          <v-col cols="6">
            <Field
              v-slot="{ value, errors, handleChange, handleBlur }"
              v-model="userData.studentNumber"
              name="studentNumber"
            >
              <v-text-field
                :disabled="disableEdit && !creating"
                :error-messages="errors"
                :model-value="value"
                label="Student Number"
                @blur="handleBlur"
                @update:model-value="handleChange"
              />
            </Field>
          </v-col>

          <v-col cols="6">
            <Field
              v-slot="{ value, errors, handleChange, handleBlur }"
              v-model="userData.dateOfBirth"
              name="dateOfBirth"
              rules="dateRequired"
            >
              <v-text-field
                :disabled="disableEdit && !creating"
                :error-messages="errors"
                :model-value="value"
                label="Date of Birth"
                type="date"
                @blur="handleBlur"
                @update:model-value="handleChange"
              />
            </Field>
          </v-col>
        </v-row>

        <v-row>
          <v-col cols="6">
            <Field
              v-slot="{ value, errors, handleChange, handleBlur }"
              v-model="userData.gender"
              name="gender"
            >
              <v-text-field
                :disabled="disableEdit && !creating"
                :error-messages="errors"
                :model-value="value"
                label="Gender"
                @blur="handleBlur"
                @update:model-value="handleChange"
              />
            </Field>
          </v-col>

          <v-col cols="6">
            <Field
              v-slot="{ value, errors, handleChange, handleBlur }"
              v-model="userData.nationality"
              name="nationality"
            >
              <nationality-select
                :disabled="disableEdit && !creating"
                :error-messages="errors"
                :model-value="value"
                label="Nationality"
                @blur="handleBlur"
                @update:model-value="handleChange"
              />
            </Field>
          </v-col>
        </v-row>

        <v-row
          align="center"
          justify="space-evenly"
        >
          <v-col cols="auto">
            <Field
              v-slot="{ value, handleChange }"
              v-model="userData.ehbo"
              name="ehbo"
            >
              <v-checkbox
                :disabled="disableEdit && !creating"
                :hide-details="true"
                :model-value="value"
                label="EHBO Diploma"
                @update:model-value="handleChange"
              />
            </Field>
          </v-col>

          <v-col cols="auto">
            <Field
              v-slot="{ value, handleChange }"
              v-model="userData.bhv"
              name="bhv"
            >
              <v-checkbox
                :disabled="disableEdit && !creating"
                :hide-details="true"
                :model-value="value"
                label="BHV Diploma"
                @update:model-value="handleChange"
              />
            </Field>
          </v-col>
        </v-row>

        <v-row
          align="center"
          class="mb-3"
          justify="space-evenly"
        >
          <v-col cols="auto">
            <Field
              v-slot="{ value, handleChange }"
              v-model="userData.photoConsent"
              name="photoConsent"
            >
              <v-checkbox
                :disabled="disableEdit && !creating"
                :hide-details="true"
                :model-value="value"
                label="Give consent for your photo to be taken at events"
                @update:model-value="handleChange"
              />
            </Field>
          </v-col>
        </v-row>
      </v-form>
    </Form>
  </div>
</template>

<script lang="ts" setup>
import {computed, ref, type Ref, watch} from "vue"
import "flag-icons/css/flag-icons.min.css"
import "v-phone-input/dist/v-phone-input.css"
import {VPhoneInput} from "v-phone-input"
import store from "@/plugins/store.ts"
import {type AdvancedUser, type SimpleUser} from "@/lib"
import type {VForm} from "vuetify/components"
import {type CountryCode} from "libphonenumber-js/max"
import SimpleUserForm from "@/components/user/SimpleUserForm.vue"
import NationalitySelect from "@/components/select/NationalitySelect.vue"
import {Field, Form, useForm} from "vee-validate"

interface Props {
  editing?: boolean;
  creating?: boolean;
  modelValue: AdvancedUser;
}

interface Emits {
  (e: "update:modelValue", user: AdvancedUser): void;
}

const props = withDefaults(defineProps<Props>(), {
  editing: false,
  creating: false,
})

const emit = defineEmits<Emits>()

const roles = computed(() => store.getters.getLogin?.roles)
const disableEdit = computed(
  () =>
    !props.creating &&
    !props.editing &&
    (!roles.value || !(roles.value.includes("BOARD") || roles.value.includes("ADMIN"))),
)

const userData: Ref<AdvancedUser> = ref({...props.modelValue})
const country: Ref<CountryCode> = ref("NL")
const valid: Ref<boolean> = ref(true)
const form: Ref<VForm | undefined> = ref()
const simpleRef = ref<InstanceType<typeof SimpleUserForm> | null>(null)

let simpleModel = computed<SimpleUser>({
  get: () => ({
    initials: userData.value.initials,
    firstName: userData.value.firstName,
    prefix: userData.value.prefix,
    lastName: userData.value.lastName,
    username: userData.value.username,
    discord: userData.value.discord,
    email: userData.value.email,
    password: userData.value.password,
    newsletter: userData.value.newsletter,
  } as SimpleUser),
  set: (val: SimpleUser) => {
    userData.value = {...userData.value, ...val}
    emit("update:modelValue", userData.value)
  },
})

watch(
  () => props.modelValue,
  (newVal) => {
    if (JSON.stringify(userData.value) !== JSON.stringify(newVal)) {
      userData.value = {...newVal}
    }
  },
  {deep: true, immediate: true},
)

watch(
  userData,
  (newVal) => emit("update:modelValue", newVal),
  {deep: true},
)

const updateCountry = (newCountry: string): void => {
  country.value = newCountry as CountryCode
}

// Use VeeValidate's form validation as the source of truth now
const {validate: vvValidate} = useForm()

const validateForm = async (): Promise<boolean> => {
  const childValid = (await simpleRef.value?.validateForm?.()) ?? true
  if (!childValid) return false
  const {valid} = await vvValidate()
  return valid
}

defineExpose({validateForm})
</script>

<style lang="scss">
.v-col:first-child {
  padding-left: 0;
}

.v-col:last-child {
  padding-right: 0;
}

.v-col {
  padding-bottom: 0;
  padding-top: 0;
}
</style>
