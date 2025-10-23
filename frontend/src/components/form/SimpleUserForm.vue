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
            @update:country="onCountryUpdate"
          />
        </v-col>
      </v-row>

      <v-row v-if="showPassword">
        <v-col cols="6">
          <VvField
            v-model="user.password"
            :component-props="{
              type: isPasswordVisible ? 'text' : 'password',
              'append-inner-icon': isPasswordVisible ? 'mdi-eye' : 'mdi-eye-off',
              'onClick:append-inner': () => (isPasswordVisible = !isPasswordVisible)
            }"
            label="Password*"
            name="password"
            rules="required|minChars:8|maxChars:100|hasLower|hasUpper|hasNumber|hasSpecial"
          />
        </v-col>

        <v-col cols="6">
          <VvField
            v-model="confirmPassword"
            :component-props="{
              type: isPasswordVisible ? 'text' : 'password',
              'append-inner-icon': isPasswordVisible ? 'mdi-eye' : 'mdi-eye-off',
              'onClick:append-inner': () => (isPasswordVisible = !isPasswordVisible)
            }"
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
import {createGuestUser, type SimpleUser, updateGuestUser} from "@/services/api"
import {type CountryCode} from "libphonenumber-js/max"
import {Form, type FormContext} from "vee-validate"
import {$handleNetworkError} from "@/plugins/handleNetworkError.ts"
import {apply} from "@/plugins/validation.ts"
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
  (e: "update:modelValue", value: SimpleUser): void
}>()

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
    emit("update:modelValue", user.value)
    return user.value
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
