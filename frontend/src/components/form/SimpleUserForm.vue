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
            v-model="simpleUser.initials"
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
            v-model="simpleUser.firstName"
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
            v-model="simpleUser.prefix"
            name="prefix"
          >
            <v-text-field
              :error-messages="errors"
              :model-value="value"
              label="SurPrefix"
              @blur="handleBlur"
              @update:model-value="handleChange"
            />
          </Field>
        </v-col>

        <v-col cols="8">
          <Field
            v-slot="{ value, errors, handleChange, handleBlur }"
            v-model="simpleUser.lastName"
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
            v-model="simpleUser.username"
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
            v-model="simpleUser.discord"
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
            v-model="simpleUser.email"
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
            v-model="simpleUser.phoneNumber"
            :rules="`required|phoneMobile:${country}`"
            name="phoneNumber"
          >
            <v-phone-input
              ref="phoneInput"
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
            v-model="simpleUser.password"
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

      <v-row
        align="center"
        justify="space-evenly"
      >
        <v-col cols="auto">
          <Field
            v-slot="{ value, handleChange }"
            v-model="simpleUser.newsletter"
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
        align="end"
        justify="end"
      >
        <v-col
          v-if="showSaveButton"
          cols="auto"
        >
          <v-btn
            :prepend-icon="isEditing ? 'mdi-content-save-edit' : 'mdi-content-save'"
            :loading="isSaving"
            :disabled="isSaving"
            @click="onSave"
          >
            Save
          </v-btn>
        </v-col>
      </v-row>
    </Form>
  </div>
</template>

<script lang="ts" setup>
import {type Ref, ref, watch} from "vue"
import {createGuestUser, type SimpleUser} from "@/services/api"
import type {FormContext} from "vee-validate"
import {Field, Form} from "vee-validate"
import {useBackendValidation} from "@/plugins/serverValidation.ts"
import {$handleNetworkError} from "@/plugins/handleNetworkError.ts"
import {VPhoneInput} from "v-phone-input"
import type {CountryCode} from "libphonenumber-js/max"

interface Props {
  modelValue?: SimpleUser
  showPasswordFields?: boolean
  showSaveButton?: boolean
  isEditing?: boolean
}

type Emits = (e: "update:modelValue", user: SimpleUser) => void

const props = withDefaults(defineProps<Props>(), {
  showPasswordFields: true,
  showSaveButton: false,
  isEditing: false,
  modelValue: () => ({
      discord: "",
      email: "",
      initials: "",
      firstName: "",
      lastName: "",
      username: "",
      newsletter: true,
      password: "",
    } as SimpleUser
  ),
})

const emit = defineEmits<Emits>()

const simpleUser: Ref<SimpleUser> = ref({...props.modelValue})
const country: Ref<CountryCode> = ref("NL")
const confirmPassword = ref<string>("")
const showPass = ref<boolean>(false)
const isSaving = ref<boolean>(false)


const updateCountry = (newCountry: string): void => {
  country.value = newCountry as CountryCode
}

watch(
  () => props.modelValue,
  (val) => {
    if (!val) return
    simpleUser.value = {...val}
  },
  {deep: true, immediate: true},
)

watch(
  simpleUser,
  (newVal) => {
    emit("update:modelValue", newVal)
  },
  {deep: true},
)

const {apply} = useBackendValidation()

const formRef = ref<FormContext>()

const validate = async (): Promise<boolean> => {
  const result = await formRef.value?.validate()
  return !!result?.valid
}

const save = async (): Promise<SimpleUser | null> => {
  if (!(await validate())) return null

  isSaving.value = true
  try {
    const resp = await createGuestUser({
      body: simpleUser.value,
      throwOnError: true,
    })
    simpleUser.value = resp.data!
    return resp.data!
  } catch (e: unknown) {
    if (!formRef.value || apply(formRef.value, e)) {
      $handleNetworkError(e)
    }
    return null
  } finally {
    isSaving.value = false
  }
}

const onSave = async () => {
  await save()
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
