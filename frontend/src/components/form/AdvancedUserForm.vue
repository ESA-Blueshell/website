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
            v-model="advancedUser.initials"
            name="initials"
            rules="required"
          >
            <v-text-field
              :error-messages="errors"
              :model-value="value"
              label="Initials"
              @blur="handleBlur"
              @update:model-value="handleChange"
            />
          </Field>
        </v-col>

        <v-col cols="8">
          <Field
            v-slot="{ value, errors, handleChange, handleBlur }"
            v-model="advancedUser.firstName"
            name="firstName"
            rules="required"
          >
            <v-text-field
              :error-messages="errors"
              :model-value="value"
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
            v-model="advancedUser.prefix"
            name="prefix"
          >
            <v-text-field
              :error-messages="errors"
              :model-value="value"
              label="Prefix"
              @blur="handleBlur"
              @update:model-value="handleChange"
            />
          </Field>
        </v-col>

        <v-col cols="8">
          <Field
            v-slot="{ value, errors, handleChange, handleBlur }"
            v-model="advancedUser.lastName"
            name="lastName"
            rules="required"
          >
            <v-text-field
              :error-messages="errors"
              :model-value="value"
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
            v-model="advancedUser.username"
            name="username"
            rules="required|alphaNum"
          >
            <v-text-field
              :error-messages="errors"
              :model-value="value"
              label="Username"
              @blur="handleBlur"
              @update:model-value="handleChange"
            />
          </Field>
        </v-col>

        <v-col cols="6">
          <Field
            v-slot="{ value, errors, handleChange, handleBlur }"
            v-model="advancedUser.discord"
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
            v-model="advancedUser.email"
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
            v-model="advancedUser.phoneNumber"
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

      <v-row v-if="showPasswordFields">
        <v-col cols="6">
          <Field
            v-slot="{ value, errors, handleChange, handleBlur }"
            v-model="advancedUser.password"
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
            v-model="advancedUser.studentNumber"
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

        <v-col cols="6">
          <Field
            v-slot="{ value, errors, handleChange, handleBlur }"
            v-model="advancedUser.dateOfBirth"
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
      </v-row>

      <v-row>
        <v-col cols="6">
          <Field
            v-slot="{ value, errors, handleChange, handleBlur }"
            v-model="advancedUser.gender"
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
            v-model="advancedUser.nationality"
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

      <v-row
        align="center"
        justify="space-evenly"
      >
        <v-col cols="auto">
          <Field
            v-slot="{ value, handleChange }"
            v-model="advancedUser.ehbo"
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
            v-model="advancedUser.bhv"
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
            v-model="advancedUser.newsletter"
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
        class="mb-3"
        justify="space-evenly"
      >
        <v-col cols="auto">
          <Field
            v-slot="{ value, handleChange }"
            v-model="advancedUser.photoConsent"
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
      >
        <v-col
          v-if="showSaveButton"
          cols="auto"
        >
          <v-btn
            :prepend-icon="isCreating ? 'mdi-content-save' : 'mdi-content-save-edit'"
            :loading="isSaving"
            :disabled="isSaving"
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
import {computed, ref, type Ref, watch} from "vue"
import "flag-icons/css/flag-icons.min.css"
import "v-phone-input/dist/v-phone-input.css"
import {VPhoneInput} from "v-phone-input"
import {type AdvancedUser, createUser, updateUser} from "@/services/api"
import {type CountryCode} from "libphonenumber-js/max"
import NationalitySelect from "@/components/form/fields/NationalitySelect.vue"
import {Field, Form, type FormContext} from "vee-validate"
import {$handleNetworkError} from "@/plugins/handleNetworkError.ts"
import {useBackendValidation} from "@/plugins/serverValidation.ts"

interface Props {
  modelValue?: AdvancedUser
  showPasswordFields?: boolean
  showSaveButton?: boolean
}

type Emits = (e: "update:modelValue", user: AdvancedUser) => void

const props = withDefaults(defineProps<Props>(), {
  showPasswordFields: false,
  showSaveButton: false,
  modelValue: () =>
    ({
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
    } as AdvancedUser),
})

const emit = defineEmits<Emits>()
const {apply} = useBackendValidation()

const isCreating = computed<boolean>(() => !advancedUser.value?.id)

const advancedUser: Ref<AdvancedUser> = ref({...props.modelValue})
const country: Ref<CountryCode> = ref("NL")

const formRef = ref<FormContext>()
const confirmPassword = ref<string>("")
const showPass = ref<boolean>(false)
const isSaving = ref<boolean>(false)

watch(
  () => props.modelValue,
  (val) => {
    if (!val) return
    advancedUser.value = {...val}
  },
  {deep: true, immediate: true},
)

watch(
  advancedUser,
  (newVal) => emit("update:modelValue", newVal),
  {deep: true},
)

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
    const hasId = Boolean(advancedUser.value?.id)
    const resp = hasId
      ? await updateUser({
        path: {id: advancedUser.value.id!},
        body: advancedUser.value,
        throwOnError: true,
      })
      : await createUser({
        body: advancedUser.value!,
        throwOnError: true,
      })

    advancedUser.value = resp.data!
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
