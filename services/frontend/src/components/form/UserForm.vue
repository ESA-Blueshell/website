<script lang="ts" setup>
import {computed, ref, watch} from "vue"
import {
  createUser,
  signUp,
  type SignupDetailsRequest,
  updateDetails,
  findMemberProfileByUserId,
  type CreateUserRequest,
  type MemberProfileResponse,
  type UpdateUserRequest,
  type UpsertMemberProfileRequest,
  updateUser,
  type SignupSessionResponse,
  type UserDetailResponse,
} from "@/services/api"
import {toEditableUser, type EditableUser} from "@/utils/editableUser"
import NationalitySelect from "@/components/form/fields/NationalitySelect.vue"
import {defineRule, Form} from "vee-validate"
import VvField from "@/components/form/fields/VvField.vue"
import {VCheckbox} from "vuetify/components"
import {$require} from "@/plugins/require.ts"
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

const privacyPolicyUrl = $require("@/assets/documents/20260223 - ESA Blueshell Privacy Policy.pdf")

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
    /** Public registration goes through POST /signup; POST /users is board-only. */
    createVia?: "signup" | "board"
  }
  /** Present during a signup: corrections travel on the token, not a session. */
  signupToken?: string
}>(), {
  showPassword: false,
  showSubmit: false,
  submitText: "Submit",
  signupToken: undefined,
  options: () => ({
    includeMemberProfile: false,
    updateKind: "auto",
    createVia: "signup",
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
    photoConsent: false,
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
const createVia = computed<"signup" | "board">(() => props.options?.createVia ?? "signup")
// An applicant holding a signup token is correcting an account nobody has been
// able to use yet, so their own name and username are still theirs to fix. The
// email address is not: changing it invalidates the confirmation link, so it goes
// through the confirmation step instead.
const canEditIdentity = computed<boolean>(
  () => isCreating.value || Boolean(props.signupToken) || effectiveUpdateKind.value === "board",
)
const canEditEmail = computed<boolean>(() => isCreating.value || effectiveUpdateKind.value === "board")
const requiresPrivacyConsent = computed<boolean>(() => isCreating.value && effectiveUpdateKind.value !== "board")

const {country, onCountryUpdate} = useCountry("NL")
const {isSaving, withSaving} = useSaving()
const {formRef, validate} = useVeeForm()
const {submitState, showSubmitStatus, setSubmitResult} = useSubmitFeedback()
const confirmPassword = ref<string>("")
// Set by a public registration; the stepper reads it to carry the applicant on.
const signupSession = ref<SignupSessionResponse>()
const {passwordFieldProps} = usePasswordToggle()

const defaultMemberProfile = (): UpsertMemberProfileRequest => ({
  dateOfBirth: "",
  studentNumber: "",
  gender: "",
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

    // Mid-signup the client is the only one who knows the profile: nothing
    // authorises an unconfirmed applicant to read their account back, so asking
    // would answer 401 and overwrite what they typed with an empty profile.
    // signupSession covers the moment registration sets the id, which lands
    // before the parent has had a chance to pass the token back down.
    if (props.signupToken || signupSession.value) return

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
  photoConsent: model.photoConsent,
  email: model.email,
  discord: model.discord,
  phoneNumber: model.phoneNumber,
  password: model.password,
  memberProfile: toMemberProfileRequest(model.memberProfile),
})

// The signup route takes everything the first step collects except the email,
// which is changed through PATCH /signup/email so the confirmation link is
// reissued with it, and the password, which is not editable mid-signup.
const toSignupDetailsRequest = (model: EditableUser): SignupDetailsRequest => ({
  username: model.username,
  initials: model.initials,
  firstName: model.firstName,
  prefix: model.prefix,
  lastName: model.lastName,
  discord: model.discord,
  phoneNumber: model.phoneNumber,
  newsletter: model.newsletter,
  photoConsent: model.photoConsent,
  memberProfile: toMemberProfileRequest(model.memberProfile),
})

const toUpdateUserRequest = (model: EditableUser): UpdateUserRequest => {
  const base = {
    discord: model.discord,
    phoneNumber: model.phoneNumber,
    newsletter: model.newsletter,
    photoConsent: model.photoConsent,
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
    // An applicant who came back to fix a typo has an account but no session, so
    // the correction travels on the signup token.
    if (user.value?.id && props.signupToken) {
      await withSaving(async () => await updateDetails({
        headers: {"X-Signup-Token": props.signupToken!},
        body: toSignupDetailsRequest(user.value!),
        throwOnError: true,
      }))
      emit("submitted", true)
      setSubmitResult(true)
      return user.value
    }

    if (!user.value?.id && createVia.value === "signup") {
      const session = await withSaving(async () => await signUp({
        body: toCreateUserRequest(user.value),
        throwOnError: true,
      }))
      signupSession.value = session.data!
      // Nothing authorises an anonymous applicant to read the account back, so the
      // form keeps what was typed and takes the id from the session.
      user.value = {...user.value, id: session.data!.userId, email: session.data!.email, password: ""}
      emit("submitted", true)
      setSubmitResult(true)
      return user.value
    }

    const resp = await withSaving(async () => {
      if (user.value?.id) {
        return await updateUser({
          path: {id: user.value.id!},
          body: toUpdateUserRequest(user.value),
          throwOnError: true,
        })
      }
      return await createUser({
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

defineExpose({validate, save, signupSession})
</script>

<template>
  <div>
    <Form
      ref="formRef"
      as="div"
    >
      <v-row>
        <v-col
          cols="12"
          sm="4"
        >
          <VvField
            v-model="user.initials"
            test-id="user-form-initials-field"
            :disabled="isReadonly || !canEditIdentity"
            label="Initials*"
            name="initials"
            :rules="canEditIdentity ? 'required' : ''"
          />
        </v-col>
        <v-col
          cols="12"
          sm="8"
        >
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
        <v-col
          cols="12"
          sm="4"
        >
          <VvField
            v-model="user.prefix"
            test-id="user-form-prefix-field"
            :disabled="isReadonly || !canEditIdentity"
            label="Surname Prefix"
            name="prefix"
          />
        </v-col>
        <v-col
          cols="12"
          sm="8"
        >
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
        <v-col
          cols="12"
          sm="6"
        >
          <VvField
            v-model="user.username"
            test-id="user-form-username-field"
            :disabled="isReadonly || !canEditIdentity"
            label="Username*"
            name="username"
            :rules="canEditIdentity ? 'required|alphaNum' : ''"
          />
        </v-col>
        <v-col
          cols="12"
          sm="6"
        >
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
        <v-col
          cols="12"
          sm="6"
        >
          <VvField
            v-model="user.email"
            test-id="user-form-email-field"
            :disabled="isReadonly || !canEditEmail"
            :rules="canEditEmail ? 'required|email|noStudentEmail' : ''"
            label="E-mail*"
            name="email"
          />
        </v-col>
        <v-col
          cols="12"
          sm="6"
        >
          <VvField
            v-model="user.phoneNumber"
            test-id="user-form-phone-number-field"
            component="VPhoneInput"
            :component-props="{
              defaultCountry: 'NL',
              mode: 'international',
              placeholder: 'Phone Number',
            }"
            :rules="`required|phoneMobile:${country}`"
            label="Phone Number*"
            name="phoneNumber"
            @update:country="onCountryUpdate"
          />
        </v-col>
      </v-row>

      <v-row v-if="showPassword">
        <v-col
          cols="12"
          sm="6"
        >
          <VvField
            v-model="user.password"
            test-id="user-form-password-field"
            :component-props="passwordFieldProps"
            label="Password*"
            name="password"
            rules="required|minChars:8|maxChars:100|hasLower|hasUpper|hasNumber|hasSpecial"
          />
        </v-col>

        <v-col
          cols="12"
          sm="6"
        >
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

      <template v-if="includeMemberProfile">
        <v-row>
          <v-col
            cols="12"
            sm="6"
          >
            <VvField
              v-model="memberProfileModel.dateOfBirth"
              test-id="user-form-date-of-birth-field"
              :component-props="{ type: 'date' }"
              label="Date of Birth*"
              name="dateOfBirth"
              rules="dateRequired"
            />
          </v-col>
          <v-col
            cols="12"
            sm="6"
          >
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
          <v-col
            cols="12"
            sm="6"
          >
            <VvField
              v-model="memberProfileModel.gender"
              test-id="user-form-gender-field"
              label="Gender"
              name="gender"
            />
          </v-col>
          <v-col
            cols="12"
            sm="6"
          >
            <VvField
              v-model="memberProfileModel.studentNumber"
              test-id="user-form-student-number-field"
              label="Student Number"
              name="studentNumber"
            />
          </v-col>
        </v-row>

        <div class="checkbox-row">
          <VvField
            v-model="memberProfileModel.ehbo"
            test-id="user-form-ehbo-field"
            :component="VCheckbox"
            :component-props="{ hideDetails: true, class: 'w-100' }"
            label="I hold a valid EHBO (first aid) diploma."
            name="ehbo"
          />
        </div>

        <div class="checkbox-row">
          <VvField
            v-model="memberProfileModel.bhv"
            test-id="user-form-bhv-field"
            :component="VCheckbox"
            :component-props="{ hideDetails: true, class: 'w-100' }"
            label="I hold a valid BHV diploma."
            name="bhv"
          />
        </div>
      </template>

      <div class="checkbox-row">
        <VvField
          v-model="user.newsletter"
          test-id="user-form-newsletter-field"
          :component="VCheckbox"
          :component-props="{ hideDetails: true, class: 'w-100' }"
          label="I want to receive the month ESA Blueshell newsletter by email."
          name="newsletter"
        />
      </div>

      <div class="checkbox-row">
        <VvField
          v-model="user.photoConsent"
          test-id="user-form-photo-consent-field"
          :component="VCheckbox"
          :component-props="{ hideDetails: true, class: 'w-100' }"
          label="I give consent to having my picture taken at ESA Blueshell events."
          name="photoConsent"
        />
      </div>

      <div
        v-if="requiresPrivacyConsent"
        class="checkbox-row checkbox-row--multiline"
      >
        <VvField
          v-model="user.consentPrivacy"
          test-id="user-form-privacy-consent-field"
          :component="VCheckbox"
          :component-props="{ hideDetails: true, class: 'w-100' }"
          name="consentPrivacy"
          :rules="requiresPrivacyConsent ? 'acceptedPrivacyPolicy' : ''"
        >
          <template #label>
            <span class="checkbox-label-text">I have read and agree to the <a
              :href="privacyPolicyUrl"
              class="text-primary"
              target="_blank"
              @click.stop
            >Privacy Policy</a> and consent to the processing of my personal data as described therein.</span>
          </template>
        </VvField>
      </div>

      <v-row
        align="end"
        class="mb-5 mt-3"
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
  width: 100%;
}

.checkbox-row :deep(.v-selection-control) {
  align-items: center;
}

.checkbox-row :deep(.v-label) {
  white-space: normal;
  text-wrap: pretty;
}

.checkbox-row--multiline :deep(.v-selection-control) {
  align-items: flex-start;
}

.checkbox-label-text {
  font-weight: normal;
}
</style>
