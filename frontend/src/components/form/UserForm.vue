<script lang="ts" setup>
import {computed, ref, watch} from "vue"
import "flag-icons/css/flag-icons.min.css"
import "v-phone-input/styles"
import {VPhoneInput} from "v-phone-input"
import {
  createUser,
  findMemberProfileByUserId,
  type CreateUserRequest,
  type MemberProfileResponse,
  type UpdateUserRequest,
  type UpsertMemberProfileRequest,
  updateUser,
  type UserDetailResponse,
} from "@/services/api"
import {toEditableUser, type EditableUser} from "@/utils/editableUser"
import NationalitySelect from "@/components/form/fields/NationalitySelect.vue"
import {defineRule, Form} from "vee-validate"
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

defineOptions({name: "UserForm"})
defineRule(
  "acceptedPrivacyPolicy",
  (value: unknown) => value === true || "You must agree to the privacy policy to create an account.",
)

const props = withDefaults(defineProps<{
  showPassword?: boolean
  showSubmit?: boolean
  submitText?: string
  options?: {
    includeMemberProfile?: boolean
    updateKind?: "auto" | "user" | "board"
  }
}>(), {
  showPassword: false,
  showSubmit: false,
  submitText: "Submit",
  options: () => ({
    includeMemberProfile: false,
    updateKind: "auto",
  }),
})

const user = defineModel<EditableUser>({
  default: () => ({
    discord: "",
    email: "",
    phoneNumber: "",
    initials: "",
    firstName: "",
    lastName: "",
    username: "",
    newsletter: true,
    consentPrivacy: false,
    password: "",
  }),
})

const emit = defineEmits<{
  (e: "submitted", ok: boolean): void
}>()

const {isReadonly, isBoard} = useReadonly()
const isCreating = computed<boolean>(() => !user.value?.id)

const includeMemberProfile = computed<boolean>(() => props.options?.includeMemberProfile ?? false)
const configuredUpdateKind = computed<"auto" | "user" | "board">(() => props.options?.updateKind ?? "auto")
const effectiveUpdateKind = computed<"user" | "board">(() => {
  if (configuredUpdateKind.value === "auto") {
    return isBoard.value ? "board" : "user"
  }
  return configuredUpdateKind.value
})
const canEditIdentity = computed<boolean>(() => isCreating.value || effectiveUpdateKind.value === "board")
const requiresPrivacyConsent = computed<boolean>(() => isCreating.value && effectiveUpdateKind.value !== "board")

const {country, onCountryUpdate} = useCountry("NL")
const {isSaving, withSaving} = useSaving()
const {formRef, validate} = useVeeForm()
const {submitState, showSubmitStatus, setSubmitResult} = useSubmitFeedback()
const confirmPassword = ref<string>("")
const {passwordFieldProps} = usePasswordToggle()

const defaultMemberProfile = (): UpsertMemberProfileRequest => ({
  dateOfBirth: "",
  studentNumber: "",
  gender: "",
  photoConsent: false,
  nationality: "NL",
  bhv: false,
  ehbo: false,
})

const toMemberProfileRequest = (
  profile: UpsertMemberProfileRequest | undefined,
): UpsertMemberProfileRequest | undefined => {
  if (!includeMemberProfile.value) return undefined
  return {
    ...defaultMemberProfile(),
    ...profile,
  }
}

const ensureMemberProfile = (): UpsertMemberProfileRequest => {
  if (!user.value.memberProfile) {
    user.value.memberProfile = defaultMemberProfile()
  }
  return user.value.memberProfile
}

const memberProfileModel = computed<UpsertMemberProfileRequest>({
  get: () => ensureMemberProfile(),
  set: (value) => {
    user.value.memberProfile = value
  },
})

const fromMemberProfileResponse = (data: MemberProfileResponse): UpsertMemberProfileRequest => ({
  dateOfBirth: data.dateOfBirth ?? "",
  studentNumber: data.studentNumber ?? "",
  gender: data.gender ?? "",
  photoConsent: data.photoConsent ?? false,
  nationality: data.nationality ?? "NL",
  bhv: data.bhv ?? false,
  ehbo: data.ehbo ?? false,
  version: data.version,
})

let loadedMemberProfileUserId: number | null = null

watch(
  () => [includeMemberProfile.value, user.value?.id] as const,
  async ([enabled, userId]) => {
    if (!enabled) {
      user.value.memberProfile = undefined
      loadedMemberProfileUserId = null
      return
    }

    ensureMemberProfile()

    if (!userId || loadedMemberProfileUserId === userId) {
      return
    }

    const response = await findMemberProfileByUserId({
      path: {userId},
    })

    if (response.status === 200 && response.data) {
      user.value.memberProfile = fromMemberProfileResponse(response.data)
    }

    loadedMemberProfileUserId = userId
  },
  {immediate: true},
)

const toCreateUserRequest = (model: EditableUser): CreateUserRequest => ({
  username: model.username,
  initials: model.initials,
  firstName: model.firstName,
  prefix: model.prefix,
  lastName: model.lastName,
  newsletter: model.newsletter,
  consentPrivacy: model.consentPrivacy,
  email: model.email,
  discord: model.discord,
  phoneNumber: model.phoneNumber,
  password: model.password,
  memberProfile: toMemberProfileRequest(model.memberProfile),
})

const toUpdateUserRequest = (model: EditableUser): UpdateUserRequest => {
  const base = {
    discord: model.discord,
    phoneNumber: model.phoneNumber,
    newsletter: model.newsletter,
    version: model.version ?? 0,
    memberProfile: toMemberProfileRequest(model.memberProfile),
  }

  if (effectiveUpdateKind.value === "board") {
    return {
      kind: "board",
      username: model.username,
      initials: model.initials,
      firstName: model.firstName,
      prefix: model.prefix,
      lastName: model.lastName,
      email: model.email,
      ...base,
    } as UpdateUserRequest
  }

  return {
    kind: "user",
    ...base,
  } as UpdateUserRequest
}

const fromUserDetail = (
  data: UserDetailResponse,
  current: EditableUser | undefined,
): EditableUser => ({
  ...toEditableUser(data, current),
  password: "",
})

const save = async (): Promise<EditableUser | null> => {
  if (!(await validate())) {
    emit("submitted", false)
    setSubmitResult(false)
    return null
  }
  try {
    const resp = await withSaving(async () => {
      const hasId = Boolean(user.value?.id)
      return hasId
        ? await updateUser({
          path: {id: user.value.id!},
          body: toUpdateUserRequest(user.value),
          throwOnError: true,
        })
        : await createUser({
          body: toCreateUserRequest(user.value),
          throwOnError: true,
        })
    })

    const updated = fromUserDetail(resp.data!, user.value)

    if (includeMemberProfile.value && updated.id) {
      const profileResponse = await findMemberProfileByUserId({
        path: {userId: updated.id},
      })
      if (profileResponse.status === 200 && profileResponse.data) {
        updated.memberProfile = fromMemberProfileResponse(profileResponse.data)
      }
    }

    user.value = updated
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
            test-id="user-form-initials-field"
            :disabled="isReadonly || !canEditIdentity"
            label="Initials*"
            name="initials"
            :rules="canEditIdentity ? 'required' : ''"
          />
        </v-col>
        <v-col cols="8">
          <VvField
            v-model="user.firstName"
            test-id="user-form-first-name-field"
            :disabled="isReadonly || !canEditIdentity"
            label="First Name*"
            name="firstName"
            :rules="canEditIdentity ? 'required' : ''"
          />
        </v-col>
      </v-row>

      <v-row>
        <v-col cols="4">
          <VvField
            v-model="user.prefix"
            test-id="user-form-prefix-field"
            :disabled="isReadonly || !canEditIdentity"
            label="Surname Prefix"
            name="prefix"
          />
        </v-col>
        <v-col cols="8">
          <VvField
            v-model="user.lastName"
            test-id="user-form-last-name-field"
            :disabled="isReadonly || !canEditIdentity"
            label="Surname*"
            name="lastName"
            :rules="canEditIdentity ? 'required' : ''"
          />
        </v-col>
      </v-row>

      <v-row>
        <v-col cols="6">
          <VvField
            v-model="user.username"
            test-id="user-form-username-field"
            :disabled="isReadonly || !canEditIdentity"
            label="Username*"
            name="username"
            :rules="canEditIdentity ? 'required|alphaNum' : ''"
          />
        </v-col>
        <v-col cols="6">
          <VvField
            v-model="user.discord"
            test-id="user-form-discord-field"
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
            test-id="user-form-email-field"
            :disabled="isReadonly || !canEditIdentity"
            :rules="canEditIdentity ? 'required|email|noStudentEmail' : ''"
            label="E-mail*"
            name="email"
          />
        </v-col>
        <v-col cols="6">
          <VvField
            v-model="user.phoneNumber"
            test-id="user-form-phone-number-field"
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
            test-id="user-form-password-field"
            :component-props="passwordFieldProps"
            label="Password*"
            name="password"
            rules="required|minChars:8|maxChars:100|hasLower|hasUpper|hasNumber|hasSpecial"
          />
        </v-col>

        <v-col cols="6">
          <VvField
            v-model="confirmPassword"
            test-id="user-form-password-repeat-field"
            :component-props="passwordFieldProps"
            label="Password (repeated)"
            name="confirmPassword"
            rules="required|match:@password"
          />
        </v-col>
      </v-row>

      <v-row
        v-if="requiresPrivacyConsent"
        class="checkbox-row"
      >
        <v-col
          class="checkbox-col"
          cols="12"
          lg="10"
          md="11"
        >
          <VvField
            v-model="user.consentPrivacy"
            test-id="user-form-privacy-consent-field"
            :component="VCheckbox"
            :component-props="{ hideDetails: true, class: 'w-100' }"
            label="I have read and agree to the ESA Blueshell Privacy Policy for account creation and processing of my personal data needed to provide my account."
            name="consentPrivacy"
            :rules="requiresPrivacyConsent ? 'acceptedPrivacyPolicy' : ''"
          />
        </v-col>
      </v-row>

      <template v-if="includeMemberProfile">
        <v-row>
          <v-col cols="6">
            <VvField
              v-model="memberProfileModel.dateOfBirth"
              test-id="user-form-date-of-birth-field"
              :component-props="{ type: 'date' }"
              label="Date of Birth*"
              name="dateOfBirth"
              rules="dateRequired"
            />
          </v-col>
          <v-col cols="6">
            <VvField
              v-model="memberProfileModel.nationality"
              test-id="user-form-nationality-field"
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
              v-model="memberProfileModel.gender"
              test-id="user-form-gender-field"
              label="Gender"
              name="gender"
            />
          </v-col>
          <v-col cols="6">
            <VvField
              v-model="memberProfileModel.studentNumber"
              test-id="user-form-student-number-field"
              label="Student Number*"
              name="studentNumber"
              rules="required"
            />
          </v-col>
        </v-row>

        <v-row class="checkbox-row">
          <v-col
            class="checkbox-col"
            cols="12"
            lg="10"
            md="11"
          >
            <VvField
              v-model="memberProfileModel.ehbo"
              test-id="user-form-ehbo-field"
              :component="VCheckbox"
              :component-props="{ hideDetails: true, class: 'w-100' }"
              label="I hold a valid EHBO (first aid) diploma and allow ESA Blueshell to store this so event organizers can identify first-aid qualified members when needed."
              name="ehbo"
            />
          </v-col>
        </v-row>

        <v-row class="checkbox-row">
          <v-col
            class="checkbox-col"
            cols="12"
            lg="10"
            md="11"
          >
            <VvField
              v-model="memberProfileModel.bhv"
              test-id="user-form-bhv-field"
              :component="VCheckbox"
              :component-props="{ hideDetails: true, class: 'w-100' }"
              label="I hold a valid BHV diploma and allow ESA Blueshell to store this so organizers can identify members trained for emergency response and evacuation support."
              name="bhv"
            />
          </v-col>
        </v-row>

        <v-row class="checkbox-row">
          <v-col
            class="checkbox-col"
            cols="12"
            lg="10"
            md="11"
          >
            <VvField
              v-model="memberProfileModel.photoConsent"
              test-id="user-form-photo-consent-field"
              :component="VCheckbox"
              :component-props="{ hideDetails: true, class: 'w-100' }"
              label="I understand photos may be taken during association events under the privacy policy and allow ESA Blueshell to store my photo preference in my member profile for organizer reference."
              name="photoConsent"
            />
          </v-col>
        </v-row>
      </template>

      <v-row class="checkbox-row">
        <v-col
          class="checkbox-col"
          cols="12"
          lg="10"
          md="11"
        >
          <VvField
            v-model="user.newsletter"
            test-id="user-form-newsletter-field"
            :component="VCheckbox"
            :component-props="{ hideDetails: true, class: 'w-100' }"
            label="I want to receive the ESA Blueshell newsletter by e-mail with association updates, event announcements, and relevant member information. I can change this later in my account settings."
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
          <submit-button
            :disabled="isSaving"
            :icon="isCreating ? 'mdi-content-save' : 'mdi-content-save-edit'"
            :loading="isSaving"
            :show-submit-status="showSubmitStatus"
            :submit-state="submitState"
            :text="submitText"
            data-testid="user-form-submit-btn"
            :data-submit-mode="isCreating ? 'create' : 'update'"
            @click="save"
          />
        </v-col>
      </v-row>
    </Form>
  </div>
</template>

<style lang="scss" scoped>
span {
  font-weight: bold;
}

.btn-tight {
  padding-inline: 6px !important;
  min-width: auto !important;
}

.checkbox-row {
  justify-content: flex-end;
}

.checkbox-col {
  display: flex;
  justify-content: flex-end;
}

.checkbox-col :deep(.v-selection-control) {
  align-items: flex-start;
}

.checkbox-col :deep(.v-label) {
  text-wrap: pretty;
}
</style>
