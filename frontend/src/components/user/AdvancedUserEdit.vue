<template>
  <div>
    <v-form
      ref="form"
      v-model="valid"
    >
      <!-- Reuse SimpleUserEdit -->
      <SimpleUserEdit
        ref="simpleRef"
        :model-value="simpleModel"
        :show-passwords="creating"
        @update:model-value="(val: SimpleUser) => simpleModel = val"
      />

      <v-row class="mt-10">
        <v-col cols="12">
          <v-phone-input
            ref="phoneInput"
            v-model="userData.phoneNumber"
            :default-country="'NL'"
            :disabled="disableEdit && !creating"
            :rules="phoneNumberRules"
            country-icon-mode="svg"
            label="Phone Number"
            mode="international"
            placeholder="Phone Number"
            @update:country="updateCountry"
          />
        </v-col>
      </v-row>
      <v-row>
        <v-col cols="6">
          <v-text-field
            v-model="userData.studentNumber"
            :disabled="disableEdit && !creating"
            label="Student Number"
          />
        </v-col>
        <v-col cols="6">
          <v-text-field
            v-model="userData.dateOfBirth"
            :disabled="disableEdit && !creating"
            :rules="dateOfBirthRules"
            label="Date of Birth"
            type="date"
          />
        </v-col>
      </v-row>

      <v-row>
        <v-col cols="6">
          <v-text-field
            v-model="userData.gender"
            :disabled="disableEdit && !creating"
            label="Gender"
          />
        </v-col>
        <v-col cols="6">
          <nationality-select
            v-model="userData.nationality"
            :disabled="disableEdit && !creating"
            label="Nationality"
          />
        </v-col>
      </v-row>

      <!-- Checkboxes -->
      <v-row
        align="center"
        justify="space-evenly"
      >
        <v-col cols="auto">
          <v-checkbox
            v-model="userData.ehbo"
            :disabled="disableEdit && !creating"
            :hide-details="true"
            label="EHBO Diploma"
          />
        </v-col>
        <v-col cols="auto">
          <v-checkbox
            v-model="userData.bhv"
            :disabled="disableEdit && !creating"
            :hide-details="true"
            label="BHV Diploma"
          />
        </v-col>
      </v-row>

      <v-row
        align="center"
        class="mb-3"
        justify="space-evenly"
      >
        <v-col cols="auto">
          <v-checkbox
            v-model="userData.photoConsent"
            :disabled="disableEdit && !creating"
            :hide-details="true"
            label="Give consent for your photo to be taken at events"
          />
        </v-col>
        <v-col
          v-if="!creating"
          cols="auto"
        >
          <v-tooltip
            location="top"
            text="Save changes"
          >
            <template #activator="{ props }">
              <v-btn
                :disabled="disableEdit"
                :loading="submitting"
                icon="mdi-content-save"
                v-bind="props"
                @click="save"
              />
            </template>
          </v-tooltip>
        </v-col>
      </v-row>
    </v-form>
  </div>
</template>

<script lang="ts" setup>
import {computed, ref, type Ref, watch} from "vue"
import "flag-icons/css/flag-icons.min.css"
import "v-phone-input/dist/v-phone-input.css"
import {VPhoneInput} from "v-phone-input"
import store from "@/plugins/store.ts"
import {type AdvancedUser, createMember, createUser, type SimpleUser, updateUser} from "@/lib"
import type {VForm} from "vuetify/components"
import {type CountryCode, parsePhoneNumber, type PhoneNumber} from "libphonenumber-js/max"
import SimpleUserEdit from "@/components/user/SimpleUserEdit.vue"
import NationalitySelect from "@/components/select/NationalitySelect.vue"

interface Props {
  editing?: boolean;
  creating?: boolean;
  modelValue: AdvancedUser;
}

interface Emits {
  (e: "update:modelValue", user: AdvancedUser): void;

  (e: "user-changed", user: AdvancedUser): void;
}

const props = withDefaults(defineProps<Props>(), {
  editing: false,
  creating: false,
})

const emit = defineEmits<Emits>()

// Computed properties
const roles = computed(() => store.getters.getLogin?.roles)
const disableEdit = computed(() => !props.creating && !props.editing && (!roles.value || !(roles.value.includes("BOARD") || roles.value.includes("ADMIN"))))

// Reactive state
const userData: Ref<AdvancedUser> = ref({...props.modelValue})
const country: Ref<CountryCode> = ref("NL")
const valid: Ref<boolean> = ref(true)
const submitting: Ref<boolean> = ref(false)
const form: Ref<VForm | undefined> = ref()
const simpleRef = ref<InstanceType<typeof SimpleUserEdit> | null>(null)

// Bridge SimpleUserEdit v-model into AdvancedUserEdit v-model
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
    userData.value = {
      ...userData.value,
      ...val,
    }
    emit("update:modelValue", userData.value)
  },
})

// Watch for prop changes
watch(
  () => props.modelValue,
  (newVal) => {
    if (JSON.stringify(userData.value) !== JSON.stringify(newVal)) {
      userData.value = {...newVal}
    }
  },
  {deep: true, immediate: true},
)

// Watch for local changes and emit
watch(
  userData,
  (newVal) => {
    emit("update:modelValue", newVal)
  },
  {deep: true},
)

// Validation rules (only ones needed for fields not covered by SimpleUserEdit)
const dateOfBirthRules = [(v: string) => !!v || "Date of birth is required"]

const phoneNumberRules = [
  (v: string) => {
    if (!v) return "Phone number is required"
    try {
      const phoneNumber: PhoneNumber = parsePhoneNumber(v, country.value)
      if (!phoneNumber.isValid()) {
        return "Enter a valid phone number"
      }
      return phoneNumber.getType() === "MOBILE" || "Enter a mobile phone number"
    } catch {
      return "Enter a valid phone number"
    }
  },
]

// Methods
const updateCountry = (newCountry: string): void => {
  country.value = newCountry as CountryCode
}

const validateForm = async (): Promise<boolean> => {
  // Validate child (SimpleUserEdit) and this form
  const childValid = (await simpleRef.value?.validateForm?.()) ?? true
  if (!form.value) return false
  const selfResult = await form.value.validate()
  return childValid && selfResult.valid
}

const save = async (): Promise<void> => {
  const isValid = await validateForm()
  if (!isValid) return

  submitting.value = true

  try {
    let response
    if (userData.value?.id) {
      response = await updateUser({
        path: {userId: userData.value.id},
        body: userData.value,
      })
    } else {
      if (roles.value && roles.value.includes("BOARD")) {
        response = await createMember({
          body: userData.value,
        })
      } else {
        response = await createUser({
          body: userData.value,
        })
      }
    }

    if (response.data) {
      userData.value = response.data
      emit("user-changed", userData.value)
      emit("update:modelValue", userData.value)
    }
  } catch (error: unknown) {
    console.error("Failed to save user:", error)
    throw error
  } finally {
    submitting.value = false
  }
}

// Expose methods
defineExpose({
  validateForm,
  save,
})
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
