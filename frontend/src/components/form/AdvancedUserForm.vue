<template>
  <div>
    <Form
      ref="formRef"
      as="div"
    >
      <v-row>
        <v-col cols="4">
          <Field
            v-slot="{ value, errors, handleChange, handleBlur }"
            v-model="user.initials"
            name="initials"
            rules="required"
          >
            <v-text-field
              :error-messages="errors"
              :model-value="value"
              :disabled="isReadonly"
              label="Initials"
              @blur="handleBlur"
              @update:model-value="handleChange"
            />
          </Field>
        </v-col>

        <v-col cols="8">
          <Field
            v-slot="{ value, errors, handleChange, handleBlur }"
            v-model="user.firstName"
            name="firstName"
            rules="required"
          >
            <v-text-field
              :error-messages="errors"
              :model-value="value"
              :disabled="isReadonly"
              label="First Name"
              @blur="handleBlur"
              @update:model-value="handleChange"
            />
          </Field>
        </v-col>
      </v-row>

      <v-row>
        <v-col cols="4">
          <Field
            v-slot="{ value, errors, handleChange, handleBlur }"
            v-model="user.prefix"
            name="prefix"
          >
            <v-text-field
              :error-messages="errors"
              :model-value="value"
              :disabled="isReadonly"
              label="Prefix"
              @blur="handleBlur"
              @update:model-value="handleChange"
            />
          </Field>
        </v-col>

        <v-col cols="8">
          <Field
            v-slot="{ value, errors, handleChange, handleBlur }"
            v-model="user.lastName"
            name="lastName"
            rules="required"
          >
            <v-text-field
              :error-messages="errors"
              :model-value="value"
              :disabled="isReadonly"
              label="Surname"
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
            v-model="user.username"
            name="username"
            rules="required|alphaNum"
          >
            <v-text-field
              :error-messages="errors"
              :model-value="value"
              :disabled="isReadonly"
              label="Username"
              @blur="handleBlur"
              @update:model-value="handleChange"
            />
          </Field>
        </v-col>

        <v-col cols="6">
          <Field
            v-slot="{ value, errors, handleChange, handleBlur }"
            v-model="user.discord"
            name="discord"
            rules="required"
          >
            <v-text-field
              :error-messages="errors"
              :model-value="value"
              label="Discord"
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
            v-model="user.email"
            :disabled="isReadonly"
            name="email"
            rules="required|email|noStudentEmail"
          >
            <v-text-field
              :error-messages="errors"
              :model-value="value"
              label="E-mail"
              @blur="handleBlur"
              @update:model-value="handleChange"
            />
          </Field>
        </v-col>

        <v-col cols="6">
          <Field
            v-slot="{ value, errors, handleChange, handleBlur }"
            v-model="user.phoneNumber"
            :rules="`required|phoneMobile:${country}`"
            name="phoneNumber"
          >
            <v-phone-input
              :default-country="'NL'"
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

      <v-row v-if="showPassword">
        <v-col cols="6">
          <Field
            v-slot="{ value, errors, handleChange, handleBlur }"
            v-model="user.password"
            name="password"
            rules="required|minChars:8|maxChars:100|hasLower|hasUpper|hasNumber|hasSpecial"
          >
            <v-text-field
              :append-inner-icon="showPass ? 'mdi-eye' : 'mdi-eye-off'"
              :error-messages="errors"
              :model-value="value"
              :type="showPass ? 'text' : 'password'"
              label="Password"
              @blur="handleBlur"
              @click:append-inner="showPass = !showPass"
              @update:model-value="handleChange"
            />
          </Field>
        </v-col>

        <v-col cols="6">
          <Field
            v-slot="{ value, errors, handleChange, handleBlur }"
            v-model="confirmPassword"
            name="confirmPassword"
            rules="required|match:@password"
          >
            <v-text-field
              :append-inner-icon="showPass ? 'mdi-eye' : 'mdi-eye-off'"
              :error-messages="errors"
              :model-value="value"
              :type="showPass ? 'text' : 'password'"
              label="Password (repeated)"
              @blur="handleBlur"
              @click:append-inner="showPass = !showPass"
              @update:model-value="handleChange"
            />
          </Field>
        </v-col>
      </v-row>

      <v-row>
        <v-col cols="6">
          <Field
            v-slot="{ value, errors, handleChange, handleBlur }"
            v-model="user.dateOfBirth"
            name="dateOfBirth"
            rules="dateRequired"
          >
            <v-text-field
              :error-messages="errors"
              :model-value="value"
              label="Date of Birth"
              type="date"
              @blur="handleBlur"
              @update:model-value="handleChange"
            />
          </Field>
        </v-col>

        <v-col cols="6">
          <Field
            v-slot="{ value, errors, handleChange, handleBlur }"
            v-model="user.nationality"
            rules="required"
            name="nationality"
          >
            <nationality-select
              :error-messages="errors"
              :model-value="value"
              label="Nationality"
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
            v-model="user.gender"
            name="gender"
          >
            <v-text-field
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
            v-model="user.studentNumber"
            name="studentNumber"
          >
            <v-text-field
              :error-messages="errors"
              :model-value="value"
              label="Student Number"
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
            v-model="user.ehbo"
            name="ehbo"
          >
            <v-checkbox
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
            v-model="user.bhv"
            name="bhv"
          >
            <v-checkbox
              :hide-details="true"
              :model-value="value"
              label="BHV Diploma"
              @update:model-value="handleChange"
            />
          </Field>
        </v-col>

        <v-col cols="auto">
          <Field
            v-slot="{ value, handleChange }"
            v-model="user.newsletter"
            name="newsletter"
          >
            <v-checkbox
              :hide-details="true"
              :model-value="value"
              label="Subscribe to newsletter"
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
            v-model="user.photoConsent"
            name="photoConsent"
          >
            <v-checkbox
              :hide-details="true"
              :model-value="value"
              label="Give consent for your photo to be taken at events"
              @update:model-value="handleChange"
            />
          </Field>
        </v-col>
      </v-row>

      <v-row
        align="end"
        justify="end"
        class="mb-5"
      >
        <v-col
          v-if="showSubmit"
          cols="auto"
        >
          <v-btn
            :prepend-icon="isCreating ? 'mdi-content-save' : 'mdi-content-save-edit'"
            :loading="isSaving"
            :disabled="isSaving"
            size="large"
            @click="save"
          >
            Save
          </v-btn>
        </v-col>
      </v-row>
    </Form>
  </div>
</template>

<script lang="ts" setup>
import {computed, ref, type Ref} from "vue"
import "flag-icons/css/flag-icons.min.css"
import "v-phone-input/dist/v-phone-input.css"
import {VPhoneInput} from "v-phone-input"
import {type AdvancedUser, createUser, updateUser} from "@/services/api"
import {type CountryCode} from "libphonenumber-js/max"
import NationalitySelect from "@/components/form/fields/NationalitySelect.vue"
import {Field, Form, type FormContext} from "vee-validate"
import {$handleNetworkError} from "@/plugins/handleNetworkError.ts"
import {useBackendValidation} from "@/plugins/serverValidation.ts"
import {useStore} from "vuex"

const {
  showPassword = false,
  showSubmit = false,
} = defineProps<{
  showPassword?: boolean
  showSubmit?: boolean
}>()

const user = defineModel<AdvancedUser>({
  default: () => ({
    discord: "",
    email: "",
    initials: "",
    firstName: "",
    lastName: "",
    username: "",
    newsletter: true,
    dateOfBirth: "",
    phoneNumber: "",
    ehbo: false,
    bhv: false,
    photoConsent: false,
    gender: "",
    nationality: "NL",
    studentNumber: "",
    password: "",
  }),
})

const {apply} = useBackendValidation()
const store = useStore()

const isCreating = computed<boolean>(() => !user.value?.id)
const country: Ref<CountryCode> = ref("NL")

const formRef = ref<FormContext>()
const confirmPassword = ref<string>("")
const showPass = ref<boolean>(false)
const isSaving = ref<boolean>(false)
const isLoggedIn = computed((): boolean => store.getters.isLoggedIn)
const isBoard = computed((): boolean => store.getters.isBoard)
const isReadonly = computed(() => isLoggedIn.value && !isBoard.value)

const updateCountry = (newCountry: string): void => {
  country.value = newCountry as CountryCode
}

const validate = async (): Promise<boolean> => {
  const result = await formRef.value?.validate()
  return !!result?.valid
}

const save = async (): Promise<AdvancedUser | null> => {
  if (!(await validate())) return null

  isSaving.value = true
  try {
    const hasId = Boolean(user.value?.id)
    const resp = hasId
      ? await updateUser({
        path: {id: user.value!.id!},
        body: user.value!,
        throwOnError: true,
      })
      : await createUser({
        body: user.value!,
        throwOnError: true,
      })

    user.value = resp.data!
    return resp.data!
  } catch (error: unknown) {
    if (!formRef.value || apply(formRef.value, error)) {
      $handleNetworkError(error)
    }
    return null
  } finally {
    isSaving.value = false
  }
}

defineExpose({validate, save})
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
