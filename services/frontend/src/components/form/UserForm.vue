<script lang="ts" setup>
import {vFirstField} from "@/utils/firstField"
import {computed, ref, watch} from "vue"
import {useStore} from "vuex"
import {needsStepUp, StepUpDialog} from "@/domains/auth"
import {
  type CreateUserRequest,
  type MemberProfileResponse,
  readMemberProfile,
  saveNewUser,
  saveSignupDetails,
  saveUser,
  type SignupDetailsRequest,
  type SignupSessionResponse,
  startSignup,
  type UpdateUserRequest,
  type UpsertMemberProfileRequest,
  type UserDetailResponse,
} from "@/domains/user"
import {toEditableUser, type EditableUser} from "@/utils/editableUser"
import NationalitySelect from "@/components/form/fields/NationalitySelect.vue"
import DiscordMemberPicker from "@/domains/discord/island/DiscordMemberPicker.vue"
import {defineRule, Form} from "vee-validate"
import VvField from "@/components/form/fields/VvField.vue"
import CheckBox from "@/components/island/CheckBox.vue"
import FormFields from "@/components/island/FormFields.vue"
import {$require} from "@/plugins/require.ts"
import type {FieldMap} from "@/plugins/validation"
import SubmitButton from "@/components/form/SubmitButton.vue"

import {
  handleSubmitError,
  useCountry,
  usePasswordToggle,
  useReadonly,
  useSaving,
  useVeeForm,
} from "@/composables/formUtils"

defineOptions({name: "UserForm"})

const privacyPolicyUrl = $require("@/assets/documents/20261003 - ESA Blueshell Privacy Policy.pdf")

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
    /**
     * Whether the member fields must be filled in, which they must for a member and nobody else.
     * TWIN: the api's MemberProfileCompleteness, which refuses the same two fields.
     */
    memberProfileRequired?: boolean
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
const memberProfileRequired = computed<boolean>(() => props.options?.memberProfileRequired ?? true)
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

// A password is only ever set while the account is being created. Every update
// path leaves it alone — board, self-service, and a signup correction alike — so
// asking for it again would present an empty required field that blocks the form
// and could not be saved even when filled in.
const canSetPassword = computed<boolean>(() => props.showPassword && isCreating.value)
const requiresPrivacyConsent = computed<boolean>(() => isCreating.value && effectiveUpdateKind.value !== "board")

const {country, onCountryUpdate} = useCountry("NL")
const {isSaving, withSaving} = useSaving()
const {formRef, validate} = useVeeForm()
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
  nameOnRosters: false,
})

const toMemberProfileRequest = (
  profile: UpsertMemberProfileRequest | null | undefined,
): UpsertMemberProfileRequest | undefined => {
  if (!includeMemberProfile.value) return undefined
  const whole = {...defaultMemberProfile(), ...profile}
  // An empty date is no date: the api reads "" as a date it cannot parse.
  return {...whole, dateOfBirth: whole.dateOfBirth || undefined}
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
  nameOnRosters: data.nameOnRosters ?? false,
  version: data.version,
})

let loadedMemberProfileUserId: number | null = null

// The read in flight, so a save that arrives first can wait for it rather than
// judge the profile fields on the blanks that stand in until it lands.
let memberProfileLoad: Promise<void> | null = null

async function loadMemberProfile(userId: number): Promise<void> {
  const profile = await readMemberProfile(userId)

  if (profile) {
    user.value.memberProfile = fromMemberProfileResponse(profile)
  }

  loadedMemberProfileUserId = userId
}

watch(
  () => [includeMemberProfile.value, user.value?.id] as const,
  ([enabled, userId]) => {
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

    memberProfileLoad = loadMemberProfile(userId)
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
  discordId: model.discordId,
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
  discordId: model.discordId,
  phoneNumber: model.phoneNumber,
  newsletter: model.newsletter,
  photoConsent: model.photoConsent,
  memberProfile: toMemberProfileRequest(model.memberProfile),
})

const toUpdateUserRequest = (model: EditableUser): UpdateUserRequest => {
  const base = {
    discord: model.discord,
    discordId: model.discordId,
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

// The request nests the member profile and this form renders it flat, so each
// constraint on it is reported under a path no field here answers to.
const userFieldMap: FieldMap = {
  "memberProfile.dateOfBirth": "dateOfBirth",
  "memberProfile.nationality": "nationality",
  "memberProfile.studentNumber": "studentNumber",
  "memberProfile.gender": "gender",
  "memberProfile.bhv": "bhv",
  "memberProfile.ehbo": "ehbo",
  "memberProfile.nameOnRosters": "nameOnRosters",
}

const save = async (): Promise<EditableUser | null> => {
  // The profile is read after this form mounts, so a save pressed in between was
  // refusing the account's own date of birth and gender for being blank.
  await memberProfileLoad
  if (!(await validate())) {
    emit("submitted", false)
    return null
  }
  try {
    // Holding a signup token is what says the account exists, so it is what this
    // branches on. Keying on the id instead meant a tab that had reloaded — token
    // still in session storage, model empty — registered a second time and was told
    // its own name was taken.
    if (props.signupToken) {
      await withSaving(async () =>
        await saveSignupDetails(props.signupToken!, toSignupDetailsRequest(user.value!)))
      emit("submitted", true)
      return user.value
    }

    if (!user.value?.id && createVia.value === "signup") {
      const session = await withSaving(async () => await startSignup(toCreateUserRequest(user.value)))
      signupSession.value = session
      // Nothing authorises an anonymous applicant to read the account back, so the
      // form keeps what was typed and takes the id from the session.
      const registered = {...user.value, id: session.userId, email: session.email, password: ""}
      user.value = registered
      emit("submitted", true)
      return registered
    }

    const resp = await withSaving(async () => {
      if (user.value?.id) {
        return await saveUser(user.value.id!, toUpdateUserRequest(user.value))
      }
      return await saveNewUser(toCreateUserRequest(user.value))
    })

    const updated = fromUserDetail(resp, user.value)

    if (includeMemberProfile.value && updated.id) {
      const savedProfile = await readMemberProfile(updated.id)
      if (savedProfile) {
        updated.memberProfile = fromMemberProfileResponse(savedProfile)
      }
    }

    user.value = updated
    emit("submitted", true)
    // Bound through v-model, user.value still reads the copy from before the save.
    return updated
  } catch (error: unknown) {
    if (needsStepUp(error)) stepUpOpen.value = true
    else handleSubmitError(formRef.value, error, userFieldMap)
    emit("submitted", false)
    return null
  }
}

const stepUpOpen = ref(false)
const signedInStore = useStore()
const twoFactorOn = computed<boolean>(() => signedInStore.getters.getLogin?.twoFactor?.on === true)

defineExpose({validate, save, signupSession})
</script>

<template>
  <div>
    <step-up-dialog
      v-model="stepUpOpen"
      :two-factor-on="twoFactorOn"
      @proved="save"
    />
    <Form
      ref="formRef"
      v-first-field
      as="div"
    >
      <form-fields class="user-form__third">
        <VvField
          v-model="user.initials"
          test-id="user-form-initials-field"
          :disabled="isReadonly || !canEditIdentity"
          label="Initials*"
          name="initials"
          :rules="canEditIdentity ? 'required' : ''"
        />
        <VvField
          v-model="user.firstName"
          test-id="user-form-first-name-field"
          :disabled="isReadonly || !canEditIdentity"
          label="First Name*"
          name="firstName"
          :rules="canEditIdentity ? 'required' : ''"
        />
      </form-fields>

      <form-fields class="user-form__third">
        <VvField
          v-model="user.prefix"
          test-id="user-form-prefix-field"
          :disabled="isReadonly || !canEditIdentity"
          label="Surname Prefix"
          name="prefix"
        />
        <VvField
          v-model="user.lastName"
          test-id="user-form-last-name-field"
          :disabled="isReadonly || !canEditIdentity"
          label="Surname*"
          name="lastName"
          :rules="canEditIdentity ? 'required' : ''"
        />
      </form-fields>

      <form-fields>
        <VvField
          v-model="user.username"
          test-id="user-form-username-field"
          :disabled="isReadonly || !canEditIdentity"
          label="Username*"
          name="username"
          :rules="canEditIdentity ? 'required' : ''"
        />
        <VvField
          v-model="user.discord"
          :component="DiscordMemberPicker"
          :component-props="{discordId: user.discordId, 'onUpdate:discordId': (id: string | null) => (user.discordId = id)}"
          test-id="user-form-discord-field"
          label="Discord*"
          name="discord"
          rules="required"
        />
      </form-fields>

      <form-fields>
        <VvField
          v-model="user.email"
          test-id="user-form-email-field"
          :disabled="isReadonly || !canEditEmail"
          :rules="canEditEmail ? 'required|email' : ''"
          label="E-mail*"
          name="email"
        />
        <VvField
          v-model="user.phoneNumber"
          test-id="user-form-phone-number-field"
          :component-props="{kind: 'phone', defaultCountry: 'NL'}"
          :rules="`required|phoneMobile:${country}`"
          label="Phone Number*"
          name="phoneNumber"
          @update:country="onCountryUpdate"
        />
      </form-fields>

      <form-fields v-if="canSetPassword">
        <VvField
          v-model="user.password"
          test-id="user-form-password-field"
          :component-props="passwordFieldProps"
          label="Password*"
          name="password"
          rules="required|minChars:8|maxChars:100|hasLower|hasUpper|hasNumber|hasSpecial"
        />
        <VvField
          v-model="confirmPassword"
          test-id="user-form-password-repeat-field"
          :component-props="passwordFieldProps"
          label="Password (repeated)"
          name="confirmPassword"
          rules="required|match:@password"
        />
      </form-fields>

      <template v-if="includeMemberProfile">
        <form-fields>
          <VvField
            v-model="memberProfileModel.dateOfBirth"
            test-id="user-form-date-of-birth-field"
            :component-props="{ type: 'date' }"
            :label="memberProfileRequired ? 'Date of Birth*' : 'Date of Birth'"
            name="dateOfBirth"
            :rules="memberProfileRequired ? 'dateRequired' : ''"
          />
          <VvField
            v-model="memberProfileModel.nationality"
            test-id="user-form-nationality-field"
            :component="NationalitySelect"
            :label="memberProfileRequired ? 'Nationality*' : 'Nationality'"
            name="nationality"
            :rules="memberProfileRequired ? 'required' : ''"
          />
        </form-fields>

        <form-fields>
          <VvField
            v-model="memberProfileModel.gender"
            test-id="user-form-gender-field"
            label="Gender"
            name="gender"
          />
          <VvField
            v-model="memberProfileModel.studentNumber"
            test-id="user-form-student-number-field"
            label="Student Number"
            name="studentNumber"
          />
        </form-fields>

        <div class="checkbox-row">
          <VvField
            v-model="memberProfileModel.ehbo"
            test-id="user-form-ehbo-field"
            :component="CheckBox"
            label="I hold a valid EHBO (first aid) diploma."
            name="ehbo"
          />
        </div>

        <div class="checkbox-row">
          <VvField
            v-model="memberProfileModel.bhv"
            test-id="user-form-bhv-field"
            :component="CheckBox"
            label="I hold a valid BHV diploma."
            name="bhv"
          />
        </div>

        <div class="checkbox-row">
          <VvField
            v-model="memberProfileModel.nameOnRosters"
            test-id="user-form-name-on-rosters-field"
            :component="CheckBox"
            label="Show my name next to my handle on the esports team pages."
            name="nameOnRosters"
          />
        </div>
      </template>

      <div class="checkbox-row">
        <VvField
          v-model="user.newsletter"
          test-id="user-form-newsletter-field"
          :component="CheckBox"
          label="I want to receive the monthly ESA Blueshell newsletter by email."
          name="newsletter"
        />
      </div>

      <div class="checkbox-row">
        <VvField
          v-model="user.photoConsent"
          test-id="user-form-photo-consent-field"
          :component="CheckBox"
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
          :component="CheckBox"
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

      <div
        v-if="showSubmit"
        class="user-form__save"
      >
        <submit-button
          :disabled="isSaving"
          :loading="isSaving"
          :text="submitText"
          data-testid="user-form-submit-btn"
          :data-submit-mode="isCreating ? 'create' : 'update'"
          @click="save"
        />
      </div>
    </Form>
  </div>
</template>

<style lang="scss" scoped>
.user-form__third {
  grid-template-columns: minmax(0, 1fr) minmax(0, 2fr);
}

.form-fields + .form-fields {
  margin-top: 0.5rem;
}

.checkbox-row {
  width: 100%;
  margin-top: 0.6rem;
}

.checkbox-label-text {
  font-weight: normal;
}

.user-form__save {
  display: flex;
  justify-content: flex-end;
  margin: 1.2rem 0 1.25rem;
}
</style>
