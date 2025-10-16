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
            name="initials"
            label="Initials*"
            rules="required"
            :disabled="isReadonly"
          />
        </v-col>

        <v-col cols="8">
          <VvField
            v-model="user.firstName"
            name="firstName"
            label="First Name*"
            rules="required"
            :disabled="isReadonly"
          />
        </v-col>
      </v-row>

      <v-row>
        <v-col cols="4">
          <VvField
            v-model="user.prefix"
            name="prefix"
            label="Surname Prefix"
            :disabled="isReadonly"
          />
        </v-col>

        <v-col cols="8">
          <VvField
            v-model="user.lastName"
            name="lastName"
            label="Surname*"
            rules="required"
            :disabled="isReadonly"
          />
        </v-col>
      </v-row>

      <v-row>
        <v-col cols="6">
          <VvField
            v-model="user.username"
            name="username"
            label="Username*"
            rules="required|alphaNum"
            :disabled="isReadonly"
          />
        </v-col>

        <v-col cols="6">
          <VvField
            v-model="user.discord"
            name="discord"
            label="Discord*"
            rules="required"
          />
        </v-col>
      </v-row>

      <v-row>
        <v-col cols="6">
          <VvField
            v-model="user.email"
            name="email"
            label="E-mail*"
            rules="required|email|noStudentEmail"
            :disabled="isReadonly"
          />
        </v-col>

        <v-col cols="6">
          <VvField
            v-model="user.phoneNumber"
            name="phoneNumber"
            :rules="`required|phoneMobile:${country}`"
            :component="VPhoneInput"
            :component-props="{
              defaultCountry: 'NL',
              countryIconMode: 'svg',
              mode: 'international',
              placeholder: 'Phone Number'
            }"
            label="Phone Number*"
            @update:country="onCountryUpdate"
          />
        </v-col>
      </v-row>

      <v-row v-if="showPassword">
        <v-col cols="6">
          <VvField
            v-model="user.password"
            name="password"
            label="Password*"
            rules="required|minChars:8|maxChars:100|hasLower|hasUpper|hasNumber|hasSpecial"
            :component-props="{
              type: isPasswordVisible ? 'text' : 'password',
              'append-inner-icon': isPasswordVisible ? 'mdi-eye' : 'mdi-eye-off',
              'onClick:append-inner': () => (isPasswordVisible = !isPasswordVisible)
            }"
            @click:append-inner="isPasswordVisible = !isPasswordVisible"
          />
        </v-col>

        <v-col cols="6">
          <VvField
            v-model="confirmPassword"
            name="confirmPassword"
            label="Password (repeated)"
            rules="required|match:@password"
            :component-props="{
              type: isPasswordVisible ? 'text' : 'password',
              'append-inner-icon': isPasswordVisible ? 'mdi-eye' : 'mdi-eye-off',
              'onClick:append-inner': () => (isPasswordVisible = !isPasswordVisible)
            }"
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
            name="newsletter"
            label="Newsletter"
            :component="VCheckbox"
            :component-props="{ hideDetails: true }"
          />
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
            type="button"
            :prepend-icon="isCreating ? 'mdi-content-save' : 'mdi-content-save-edit'"
            :loading="isSaving"
            :disabled="isSaving"
            size="large"
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
import {createGuestUser, type SimpleUser, updateGuestUser} from "@/services/api"
import {type CountryCode} from "libphonenumber-js/max"
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

const {apply} = useBackendValidation()
const store = useStore()

const isCreating = computed<boolean>(() => !user.value?.id)
const country: Ref<CountryCode> = ref("NL")

const formRef = ref<FormContext>()
const confirmPassword = ref<string>("")
const isPasswordVisible = ref<boolean>(false)
const isSaving = ref<boolean>(false)
const isLoggedIn = computed((): boolean => store.getters.isLoggedIn)
const isBoard = computed((): boolean => store.getters.isBoard)
const isReadonly = computed(() => isLoggedIn.value && !isBoard.value)

const onCountryUpdate = (newCountry: string): void => {
  country.value = newCountry as CountryCode
}

const validate = async (): Promise<boolean> => {
  const result = await formRef.value?.validate()
  return !!result?.valid
}

const save = async (): Promise<SimpleUser | null> => {
  if (!(await validate())) {
    emit("submitted", false)
    return null
  }

  isSaving.value = true
  try {
    const hasId = Boolean(user.value?.id)
    const resp = hasId
      ? await updateGuestUser({
        path: {id: user.value!.id!},
        body: user.value!,
        throwOnError: true,
      })
      : await createGuestUser({
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
