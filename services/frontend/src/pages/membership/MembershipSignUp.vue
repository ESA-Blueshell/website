<template>
  <v-main>
    <top-banner title="Membership Form" />

    <div
      class="mx-auto my-6"
      style="max-width: 800px"
    >
      <v-stepper
        v-if="!finished"
        v-model="currentStep"
        data-testid="membership-signup-stepper"
        :items="stepItems"
        hide-actions
      >
        <!-- Step 1: who they are -->
        <template #[`item.1`]>
          <v-card class="pa-4">
            <div
              v-if="preparing"
              class="d-flex align-center justify-center pa-6"
              data-testid="membership-details-loading"
            >
              <v-progress-circular
                class="mr-3"
                indeterminate
                size="28"
              />
              <span class="text-body-1">Fetching the details we already hold.</span>
            </div>
            <user-form
              v-else
              ref="userRef"
              v-model="user"
              :options="{ includeMemberProfile: true, createVia: 'signup' }"
              :show-password="isNewApplicant"
              :signup-token="signupToken"
            />
            <v-row align="center">
              <v-spacer />
              <v-col cols="auto">
                <v-btn
                  :disabled="submitting || preparing"
                  :loading="submitting || preparing"
                  color="primary"
                  data-testid="membership-details-next-btn"
                  @click="saveDetails"
                >
                  Next
                </v-btn>
              </v-col>
            </v-row>
          </v-card>
        </template>

        <!-- Step 2: where they live -->
        <template #[`item.2`]>
          <v-card class="pa-4">
            <address-form
              ref="addressRef"
              v-model="address"
              :user-id="user?.id"
              :signup-token="signupToken"
            />
            <v-row align="center">
              <v-col cols="auto">
                <v-btn
                  data-testid="membership-address-back-btn"
                  variant="outlined"
                  @click="currentStep = Steps.Details"
                >
                  Previous
                </v-btn>
              </v-col>
              <v-spacer />
              <v-col cols="auto">
                <v-btn
                  :disabled="submitting"
                  :loading="submitting"
                  color="primary"
                  data-testid="membership-address-next-btn"
                  @click="saveAddressStep"
                >
                  Next
                </v-btn>
              </v-col>
            </v-row>
          </v-card>
        </template>

        <!-- Step 3: the application itself -->
        <template #[`item.3`]>
          <v-card class="pa-4">
            <v-alert
              v-if="applicationSubmitted"
              border="start"
              class="mb-4"
              color="success"
              data-testid="membership-conditions-accepted"
              variant="tonal"
            >
              Your application is in and you agreed to the membership conditions.
              Details and address can still be changed; the agreement stands.
            </v-alert>
            <membership-form
              v-else
              ref="membershipRef"
              v-model="membership"
              :signup-token="signupToken"
            />
            <v-row align="center">
              <v-col cols="auto">
                <v-btn
                  data-testid="membership-conditions-back-btn"
                  variant="outlined"
                  @click="currentStep = Steps.Address"
                >
                  Previous
                </v-btn>
              </v-col>
              <v-spacer />
              <v-col cols="auto">
                <v-btn
                  v-if="applicationSubmitted && awaitsEmailConfirmation"
                  color="primary"
                  data-testid="membership-conditions-continue-btn"
                  @click="currentStep = Steps.ConfirmEmail"
                >
                  Continue
                </v-btn>
                <v-btn
                  v-else
                  :disabled="submitting"
                  :loading="submitting"
                  color="primary"
                  data-testid="membership-conditions-submit-btn"
                  @click="submitApplication"
                >
                  Complete Membership
                </v-btn>
              </v-col>
            </v-row>
          </v-card>
        </template>

        <!-- Step 4: confirm the email address. New applicants only. -->
        <template #[`item.4`]>
          <email-confirmation-panel
            :email="user?.email ?? ''"
            :username="user?.username ?? ''"
            :continuation-token="signupToken"
            confirmation-consequence="Your membership starts as soon as you do."
            @back="currentStep = Steps.Membership"
            @email-corrected="onEmailCorrected"
          />
        </template>
      </v-stepper>

      <v-card
        v-else
        class="pa-6 text-center"
        data-testid="membership-complete-panel"
      >
        <v-icon
          class="mb-4"
          color="success"
          size="64"
        >
          mdi-check-circle
        </v-icon>
        <p class="text-h6 font-weight-medium mb-2">
          You're a member
        </p>
        <p class="text-body-1 text-medium-emphasis">
          Welcome to Blueshell. Your membership starts today.
        </p>
        <v-btn
          class="mt-4"
          color="primary"
          data-testid="membership-home-btn"
          @click="$goto('/')"
        >
          Go to Homepage
        </v-btn>
      </v-card>
    </div>
  </v-main>
</template>

<script lang="ts" setup>
import {computed, onMounted, onUnmounted, ref, watch} from "vue"
import TopBanner from "@/components/common/banners/TopBanner.vue"
import UserForm from "@/components/form/UserForm.vue"
import AddressForm from "@/components/form/AddressForm.vue"
import MembershipForm from "@/components/form/MembershipForm.vue"
import EmailConfirmationPanel from "@/components/form/EmailConfirmationPanel.vue"
import {
  type AddressResponse,
  findAddressById,
  findUserById,
  type MembershipResponse,
  resumeSignup,
  Role,
  type SignupOutcomeResponse,
  type SignupResumeResponse,
} from "@/services/api"
import store from "@/plugins/store"
import {$handleNetworkError} from "@/plugins/handleNetworkError"
import {$goto} from "@/plugins/goto"
import router from "@/plugins/router.ts"
import {toEditableUser, type EditableUser} from "@/utils/editableUser"
import {
  forgetSignupToken,
  onAccountActivated,
  onSignupTokenRejected,
  readSignupToken,
  rememberSignupToken,
  SIGNUP_TOKEN_HEADER,
} from "@/plugins/signupContinuation"

const Steps = {Details: 1, Address: 2, Membership: 3, ConfirmEmail: 4} as const

// A step whose form never mounted is this page being wrong rather than the applicant.
// Said out loud all the same: a button that answers a press with nothing at all is the
// thing that stranded people here in the first place.
const STEP_DID_NOT_OPEN = "that step did not open, so reload the page and try again"

const currentStep = ref<number>(Steps.Details)
const submitting = ref(false)
// True while the account's own details are still being fetched. Nothing on the
// first step is offered until they land: a form standing in for data that has not
// arrived is one a signed-in applicant can submit empty, and the arrival would
// overwrite whatever they had typed into it.
const preparing = ref(false)
const finished = ref(false)
const applicationSubmitted = ref(false)
// Set when another tab confirms the address, which is what retires the step that
// asks for it.
const emailConfirmed = ref(false)

const user = ref<EditableUser>()
// Partial, because a signup read back on its token carries what the form asks for and
// not the id or the version: the signup route upserts, so there is nothing to track.
const address = ref<Partial<AddressResponse>>()
const membership = ref<MembershipResponse>()
const signupToken = ref<string | undefined>(readSignupToken())

const userRef = ref<InstanceType<typeof UserForm>>()
const addressRef = ref<InstanceType<typeof AddressForm>>()
const membershipRef = ref<InstanceType<typeof MembershipForm>>()

const isLoggedIn = computed<boolean>(() => store.getters.isLoggedIn)
const login = computed(() => store.getters.getLogin)

// A signed-in applicant confirmed their address when they activated the account,
// so they never see the confirmation step.
const isNewApplicant = computed<boolean>(() => !isLoggedIn.value)

// Whether a step asking for the address confirmation is still on the stepper.
// Read by everything that navigates there, so nothing can send an applicant to a
// step that is no longer rendered.
const awaitsEmailConfirmation = computed<boolean>(() => isNewApplicant.value && !emailConfirmed.value)

const stepItems = computed<Array<{title: string; value: number}>>(() => {
  const items = [
    {title: "Your details", value: Steps.Details as number},
    {title: "Address", value: Steps.Address as number},
    {title: "Membership", value: Steps.Membership as number},
  ]
  if (awaitsEmailConfirmation.value) {
    items.push({title: "Confirm email", value: Steps.ConfirmEmail})
  }
  return items
})

function rememberToken(token: string) {
  signupToken.value = token
  rememberSignupToken(token)
}

function forgetToken() {
  signupToken.value = undefined
  forgetSignupToken()
}

async function withSubmitting(action: () => Promise<void>) {
  try {
    submitting.value = true
    await action()
  } finally {
    submitting.value = false
  }
}

// Each step keeps what it saved on this page rather than in the step component.
// A stepper renders only the active step, so anything a form holds privately is
// gone the moment the applicant moves on — and `defineModel`'s own default lives
// in the child, which means nested edits never reach a parent that started out
// undefined. Adopting what save() hands back is what makes going back and forth
// keep the details and the address.
const saveDetails = () => withSubmitting(async () => {
  // Guarded here as well as on the affordance: the rule is that nothing is saved
  // from a step still waiting for what it is meant to show, and a disabled button
  // only states that for the one way in.
  if (preparing.value) return
  if (!userRef.value) {
    store.commit("setStatusSnackbarMessage", STEP_DID_NOT_OPEN)
    return
  }
  const saved = await userRef.value.save()
  if (!saved) return
  const session = userRef.value?.signupSession
  if (session) rememberToken(session.signupToken)
  // The account's identity comes from the session rather than from the form's
  // model. A step that is about to be unmounted is the wrong place to keep the
  // fact that an account now exists, and it is that fact which decides whether
  // the form asks for a password again.
  user.value = session
    ? {...saved, id: session.userId, email: session.email}
    : saved
  currentStep.value = Steps.Address
})

const saveAddressStep = () => withSubmitting(async () => {
  // The form is mounted by the step, so its absence is this page being wrong rather
  // than the applicant. Said out loud all the same: a button that answers nothing at
  // all is the thing that stranded people here.
  if (!addressRef.value) {
    store.commit("setStatusSnackbarMessage", STEP_DID_NOT_OPEN)
    return
  }
  const saved = await addressRef.value.save()
  if (!saved) return
  address.value = saved as Partial<AddressResponse>
  currentStep.value = Steps.Membership
})

const submitApplication = () => withSubmitting(async () => {
  if (!membershipRef.value) {
    store.commit("setStatusSnackbarMessage", STEP_DID_NOT_OPEN)
    return
  }
  const result = await membershipRef.value.save()
  if (!result) return
  settleOutcome(result as SignupOutcomeResponse)
})

/**
 * Whichever of the two facts lands second starts the membership, so the ending is
 * read from the response rather than assumed from which step we are on.
 */
function settleOutcome(outcome: SignupOutcomeResponse) {
  if (outcome.membershipStarted) {
    forgetToken()
    finished.value = true
    return
  }
  // The agreement is not retractable, so the conditions step becomes a record of
  // it while details and address stay open for edits.
  applicationSubmitted.value = true
  if (!awaitsEmailConfirmation.value) {
    // There is no confirmation step to send them to and the membership still did not
    // start, which leaves nothing here to press: the api only answers this once a
    // membership already exists. Offering the same button again just re-posted to the
    // same answer, so this hands over to the page that account is reachable from.
    void standDownForExistingMembership()
    return
  }
  currentStep.value = Steps.ConfirmEmail
}

async function standDownForExistingMembership() {
  forgetToken()
  store.commit("setStatusSnackbarMessage", "your application is in, and this account is already a member, so sign in")
  await router.replace({name: "login"})
}

function onEmailCorrected(email: string) {
  if (user.value) user.value.email = email
}

/**
 * Puts a signup back together from the token this tab still holds.
 *
 * Session storage keeps the token so the signup survives a reload, and the account has to be
 * read back from it: without that the form comes up empty, the first step keys on an id it does
 * not have, and Next registers again — telling the applicant their own username is taken.
 */
async function resumeFromToken(token: string) {
  const {data} = await resumeSignup({headers: {[SIGNUP_TOKEN_HEADER]: token}, throwOnError: true})
  if (!data) return
  adoptResumedSignup(data)
}

function adoptResumedSignup(resumed: SignupResumeResponse) {
  user.value = {
    id: resumed.userId,
    email: resumed.email,
    username: resumed.username,
    initials: resumed.initials,
    firstName: resumed.firstName,
    prefix: resumed.prefix ?? undefined,
    lastName: resumed.lastName,
    discord: resumed.discord ?? "",
    phoneNumber: resumed.phoneNumber ?? "",
    newsletter: resumed.newsletter,
    photoConsent: resumed.photoConsent,
    consentPrivacy: true,
    password: "",
    memberProfile: resumed.memberProfile
      ? {
        dateOfBirth: resumed.memberProfile.dateOfBirth ?? "",
        studentNumber: resumed.memberProfile.studentNumber ?? "",
        gender: resumed.memberProfile.gender ?? "",
        nationality: resumed.memberProfile.nationality ?? "NL",
        bhv: resumed.memberProfile.bhv,
        ehbo: resumed.memberProfile.ehbo,
        nameOnRosters: resumed.memberProfile.nameOnRosters,
      }
      : undefined,
  } as EditableUser
  if (resumed.address) address.value = {...resumed.address}
  emailConfirmed.value = resumed.emailConfirmed
  applicationSubmitted.value = resumed.conditionsAccepted
  // Both facts in and an address on file means the membership had everything it needed
  // and still did not start, which the api only answers when one already exists. There
  // is nothing on any step to press, so this hands over rather than showing a form.
  if (resumed.conditionsAccepted && resumed.emailConfirmed && resumed.address) {
    void standDownForExistingMembership()
    return
  }
  currentStep.value = stepReached(resumed)
}

/**
 * The furthest step whose answers are already in, so a resumed applicant carries on
 * rather than retyping what the api just handed back.
 *
 * The address comes first even when the conditions were already agreed to: without one
 * the membership cannot start, so a step past it would offer a button that could only
 * fail. The agreement is not lost by going back through it — it is not retractable, and
 * the conditions step says so rather than asking again.
 */
function stepReached(resumed: SignupResumeResponse): number {
  if (!resumed.address) return Steps.Address
  if (resumed.conditionsAccepted && awaitsEmailConfirmation.value) return Steps.ConfirmEmail
  return Steps.Membership
}

async function loadSignedInApplicant() {
  const userId = login.value?.userId
  if (!userId) return
  try {
    const {data} = await findUserById({path: {userId}, throwOnError: true})
    if (data) user.value = toEditableUser(data)
  } catch (e) {
    $handleNetworkError(e)
    return
  }
  const addressId = login.value?.addressId
  if (!addressId) return
  try {
    // Throws, so the handler below is reachable: a read that failed would otherwise leave the
    // address step blank as though nothing were recorded.
    const {data} = await findAddressById({path: {id: addressId}, throwOnError: true})
    if (data) address.value = data
  } catch (e) {
    $handleNetworkError(e)
  }
}

// Somebody who is already a member has nothing to apply for.
watch(user, async (val) => {
  if (!val?.roles?.includes(Role.MEMBER)) return
  store.commit("setStatusSnackbarMessage", "you are already a member")
  await router.replace("/")
})

/**
 * Another tab activated this account, and what that costs depends on how far this
 * one had got.
 *
 * ADR-025 keeps the token alive through confirmation so an applicant "must be able
 * to carry on in the tab they are already in", so before the application is in
 * only the step asking them to go and confirm retires. After it, activation
 * completes the pair: the membership starts, the server retires the token, and
 * nothing here can be saved again.
 */
async function standDownForActivation() {
  if (finished.value) return
  if (!applicationSubmitted.value) {
    emailConfirmed.value = true
    if (currentStep.value === Steps.ConfirmEmail) currentStep.value = Steps.Membership

    store.commit("setStatusSnackbarMessage", "your email address is confirmed, so you can finish here")
    return
  }
  forgetToken()
  store.commit("setStatusSnackbarMessage", "your membership started, so you can sign in")
  await router.replace({name: "login"})
}

/**
 * The token this tab holds is gone, which no amount of retrying here mends.
 * Detected on the way out of a step so a stale tab cannot go back and press on
 * against a token the server has already retired.
 */
async function standDownForDeadToken() {
  forgetToken()
  store.commit("setStatusSnackbarMessage", "this signup expired, so sign in or start again")
  await router.replace({name: "login"})
}

let stopListeningForActivation: (() => void) | undefined
let stopListeningForRejection: (() => void) | undefined

onMounted(async () => {
  stopListeningForActivation = onAccountActivated(() => void standDownForActivation())
  stopListeningForRejection = onSignupTokenRejected(() => void standDownForDeadToken())
  const token = signupToken.value
  if (!isLoggedIn.value && !token) return
  preparing.value = true
  try {
    if (isLoggedIn.value) {
      await loadSignedInApplicant()
      return
    }
    await resumeFromToken(token!)
  } catch (e) {
    // A token the api refuses is reported once by the client and stands this tab down.
    // Anything else leaves the applicant at the first step, which is where they were.
    $handleNetworkError(e)
  } finally {
    preparing.value = false
  }
})

onUnmounted(() => {
  stopListeningForActivation?.()
  stopListeningForRejection?.()
})
</script>

<style lang="scss" scoped>
.v-stepper {
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.v-card {
  border-radius: 12px;
}
</style>
