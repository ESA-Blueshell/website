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
            label="Prefix"
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
        <v-col>
          <VvField
            v-model="user.username"
            :disabled="isReadonly"
            label="Username*"
            name="username"
            rules="required|alphaNum"
          />
        </v-col>

        <v-col>
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
            label="E-mail*"
            name="email"
            rules="required|email|noStudentEmail"
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
            @update:country="updateCountry"
          />
        </v-col>
      </v-row>

      <v-row v-if="showPassword">
        <v-col cols="6">
          <VvField
            v-model="user.password"
            :component-props="{
              type: showPass ? 'text' : 'password',
              'append-inner-icon': showPass ? 'mdi-eye' : 'mdi-eye-off'
            }"
            label="Password*"
            name="password"
            rules="required|minChars:8|maxChars:100|hasLower|hasUpper|hasNumber|hasSpecial"
            @click:append-inner="showPass = !showPass"
          />
        </v-col>

        <v-col cols="6">
          <VvField
            v-model="confirmPassword"
            :component-props="{
              type: showPass ? 'text' : 'password',
              'append-inner-icon': showPass ? 'mdi-eye' : 'mdi-eye-off'
            }"
            label="Password (repeated)"
            name="confirmPassword"
            rules="required|match:@password"
            @click:append-inner="showPass = !showPass"
          />
        </v-col>
      </v-row>

      <v-row>
        <v-col cols="6">
          <VvField
            v-model="user.dateOfBirth"
            :component-props="{ type: 'date' }"
            label="Date of Birth*"
            name="dateOfBirth"
            rules="dateRequired"
          />
        </v-col>

        <v-col cols="6">
          <VvField
            v-model="user.nationality"
            :component="NationalitySelect"
            label="Nationality*"
            name="nationality"
            rules="required"
          />
        </v-col>
      </v-row>

      <v-row>
        <v-col cols="6">
          <VvField
            v-model="user.gender"
            label="Gender"
            name="gender"
          />
        </v-col>

        <v-col cols="6">
          <VvField
            v-model="user.studentNumber"
            label="Student Number"
            name="studentNumber"
          />
        </v-col>
      </v-row>

      <v-row
        align="center"
        justify="space-evenly"
      >
        <v-col cols="auto">
          <VvField
            v-model="user.ehbo"
            :component="VCheckbox"
            :component-props="{ hideDetails: true }"
            label="EHBO Diploma"
            name="ehbo"
          />
        </v-col>

        <v-col cols="auto">
          <VvField
            v-model="user.bhv"
            :component="VCheckbox"
            :component-props="{ hideDetails: true }"
            label="BHV Diploma"
            name="bhv"
          />
        </v-col>

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
        align="center"
        justify="space-evenly"
      >
        <v-col cols="auto">
          <VvField
            v-model="user.photoConsent"
            :component="VCheckbox"
            :component-props="{ hideDetails: true }"
            label="Consent to pictures being taken at event"
            name="photoConsent"
          />
        </v-col>
      </v-row>

      <v-row
        align="end"
        class="mb-5"
        justify="end"
      >
        <v-col
          v-if="showSubmit"
          cols="auto"
        >
          <v-btn
            :disabled="isSaving"
            :loading="isSaving"
            :prepend-icon="isCreating ? 'mdi-content-save' : 'mdi-content-save-edit'"
            size="large"
            type="button"
            @click="save"
          >
            {{ submitText }}
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
import {Form, type FormContext} from "vee-validate"
import {$handleNetworkError} from "@/plugins/handleNetworkError.ts"
import {useBackendValidation} from "@/plugins/serverValidation.ts"
import {useStore} from "vuex"
import VvField from "@/components/form/fields/VvField.vue"
import {VCheckbox} from "vuetify/components"

const {
  showPassword = false,
  showSubmit = false,
  submitText = "Submit",
} = defineProps<{
  showPassword?: boolean
  showSubmit?: boolean
  submitText?: string
}>()

const emit = defineEmits<{
  (e: "submitted", ok: boolean): void
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
  if (!(await validate())) {
    emit("submitted", false)
    return null
  }

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
    emit("submitted", true)
    return resp.data!
  } catch (error: unknown) {
    if (!formRef.value || !apply(formRef.value, error)) {
      $handleNetworkError(error)
    }
    emit("submitted", false)
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
