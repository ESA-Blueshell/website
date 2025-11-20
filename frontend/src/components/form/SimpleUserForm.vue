<script lang="ts" setup>
import {computed, ref} from "vue"
import "flag-icons/css/flag-icons.min.css"
import "v-phone-input/dist/v-phone-input.css"
import {VPhoneInput} from "v-phone-input"
import {createGuestUser, type SimpleUser, updateGuestUser} from "@/services/api"
import {Form} from "vee-validate"
import VvField from "@/components/form/fields/VvField.vue"
import {VCheckbox} from "vuetify/components"
import SubmitButton from "@/components/form/SubmitButton.vue"

import {
  handleSubmitError,
  useCountry,
  usePasswordToggle,
  useReadonly,
  useSaving,
  useSubmitFeedback,
  useVeeForm,
} from "@/composables/formUtils"

const {showPassword = false, showSubmit = false, submitText = "Submit"} = defineProps<{
  showPassword?: boolean
  showSubmit?: boolean
  submitText?: string
}>()

const user = defineModel<SimpleUser>({
  default: () => ({
    discord: "",
    email: "",
    phoneNumber: "",
    initials: "",
    firstName: "",
    lastName: "",
    username: "",
    newsletter: true,
    password: "",
  }),
})

const emit = defineEmits<{
  (e: "submitted", ok: boolean): void
}>()

const {isReadonly} = useReadonly()
const isCreating = computed<boolean>(() => !user.value?.id)

const {country, onCountryUpdate} = useCountry("NL")
const {isSaving, withSaving} = useSaving()
const {formRef, validate} = useVeeForm()
const {submitState, showSubmitStatus, setSubmitResult} = useSubmitFeedback()
const confirmPassword = ref<string>("")
const {passwordFieldProps} = usePasswordToggle()

const save = async (): Promise<SimpleUser | null> => {
  if (!(await validate())) {
    emit("submitted", false)
    setSubmitResult(false)
    return null
  }
  try {
    const resp = await withSaving(async () => {
      const hasId = Boolean(user.value?.id)
      return hasId
        ? await updateGuestUser({path: {id: user.value!.id!}, body: user.value!, throwOnError: true})
        : await createGuestUser({body: user.value!, throwOnError: true})
    })
    user.value = resp.data!
    emit("submitted", true)
    setSubmitResult(true)
    return user.value
  } catch (error: unknown) {
    handleSubmitError(formRef.value, error)
    emit("submitted", false)
    setSubmitResult(false)
    return null
  }
}

defineExpose({validate, save})
</script>

<template>
  <div>
    <Form
      ref="formRef"
      as="div"
    >
      <v-row>
        <v-col cols="4">
          <VvField
            v-model="user.initials"
            :disabled="isReadonly"
            label="Initials*"
            name="initials"
            rules="required"
          />
        </v-col>
        <v-col cols="8">
          <VvField
            v-model="user.firstName"
            :disabled="isReadonly"
            label="First Name*"
            name="firstName"
            rules="required"
          />
        </v-col>
      </v-row>

      <v-row>
        <v-col cols="4">
          <VvField
            v-model="user.prefix"
            :disabled="isReadonly"
            label="Surname Prefix"
            name="prefix"
          />
        </v-col>
        <v-col cols="8">
          <VvField
            v-model="user.lastName"
            :disabled="isReadonly"
            label="Surname*"
            name="lastName"
            rules="required"
          />
        </v-col>
      </v-row>

      <v-row>
        <v-col cols="6">
          <VvField
            v-model="user.username"
            :disabled="isReadonly"
            label="Username*"
            name="username"
            rules="required|alphaNum"
          />
        </v-col>
        <v-col cols="6">
          <VvField
            v-model="user.discord"
            label="Discord*"
            name="discord"
            rules="required"
          />
        </v-col>
      </v-row>

      <v-row>
        <v-col cols="6">
          <VvField
            v-model="user.email"
            :disabled="isReadonly"
            :rules="isReadonly ? '' : 'required|email|noStudentEmail'"
            label="E-mail*"
            name="email"
          />
        </v-col>
        <v-col cols="6">
          <VvField
            v-model="user.phoneNumber"
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

      <v-row
        v-if="showPassword"
      >
        <v-col cols="6">
          <VvField
            v-model="user.password"
            :component-props="passwordFieldProps"
            label="Password*"
            name="password"
            rules="required|minChars:8|maxChars:100|hasLower|hasUpper|hasNumber|hasSpecial"
          />
        </v-col>

        <v-col cols="6">
          <VvField
            v-model="confirmPassword"
            :component-props="passwordFieldProps"
            label="Password (repeated)"
            name="confirmPassword"
            rules="required|match:@password"
          />
        </v-col>
      </v-row>

      <v-row
        align="center"
        justify="space-evenly"
      >
        <v-col cols="auto">
          <VvField
            v-model="user.newsletter"
            :component="VCheckbox"
            :component-props="{ hideDetails: true }"
            label="Newsletter"
            name="newsletter"
          />
        </v-col>
      </v-row>

      <v-row
        v-if="showSubmit"
        align="end"
        class="mb-5"
        justify="end"
      >
        <submit-button
          :disabled="isSaving"
          :icon="isCreating ? 'mdi-content-save' : 'mdi-content-save-edit'"
          :loading="isSaving"
          :show-submit-status="showSubmitStatus"
          :submit-state="submitState"
          :text="submitText"
          @click="save"
        />
      </v-row>
    </Form>
  </div>
</template>
