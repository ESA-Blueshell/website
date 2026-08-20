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
            <user-form
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
                  :loading="submitting"
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
                  v-if="applicationSubmitted"
                  color="primary"
                  data-testid="membership-conditions-continue-btn"
                  @click="currentStep = Steps.ConfirmEmail"
                >
                  Continue
                </v-btn>
                <v-btn
                  v-else
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
import {computed, onMounted, ref, watch} from "vue"
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
  Role,
  type SignupOutcomeResponse,
} from "@/services/api"
import store from "@/plugins/store"
import {$handleNetworkError} from "@/plugins/handleNetworkError"
import {$goto} from "@/plugins/goto"
import router from "@/plugins/router.ts"
import {toEditableUser, type EditableUser} from "@/utils/editableUser"

const Steps = {Details: 1, Address: 2, Membership: 3, ConfirmEmail: 4} as const

const SIGNUP_TOKEN_STORAGE_KEY = "signup:continuation:token"

const currentStep = ref<number>(Steps.Details)
const submitting = ref(false)
const finished = ref(false)
const applicationSubmitted = ref(false)

const user = ref<EditableUser>()
const address = ref<AddressResponse>()
const membership = ref<MembershipResponse>()
const signupToken = ref<string | undefined>(readStoredToken())

const userRef = ref<InstanceType<typeof UserForm>>()
const addressRef = ref<InstanceType<typeof AddressForm>>()
const membershipRef = ref<InstanceType<typeof MembershipForm>>()

const isLoggedIn = computed<boolean>(() => store.getters.isLoggedIn)
const login = computed(() => store.getters.getLogin)

// A signed-in applicant confirmed their address when they activated the account,
// so they never see the confirmation step.
const isNewApplicant = computed<boolean>(() => !isLoggedIn.value)

const stepItems = computed<Array<{title: string; value: number}>>(() => {
  const items = [
    {title: "Your details", value: Steps.Details as number},
    {title: "Address", value: Steps.Address as number},
    {title: "Membership", value: Steps.Membership as number},
  ]
  if (isNewApplicant.value) {
    items.push({title: "Confirm email", value: Steps.ConfirmEmail})
  }
  return items
})

function readStoredToken(): string | undefined {
  if (typeof window === "undefined") return undefined
  return sessionStorage.getItem(SIGNUP_TOKEN_STORAGE_KEY) ?? undefined
}

function rememberToken(token: string) {
  signupToken.value = token
  if (typeof window !== "undefined") sessionStorage.setItem(SIGNUP_TOKEN_STORAGE_KEY, token)
}

function forgetToken() {
  signupToken.value = undefined
  if (typeof window !== "undefined") sessionStorage.removeItem(SIGNUP_TOKEN_STORAGE_KEY)
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
  const saved = await userRef.value?.save()
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
  const saved = await addressRef.value?.save()
  if (!saved) return
  address.value = saved as AddressResponse
  currentStep.value = Steps.Membership
})

const submitApplication = () => withSubmitting(async () => {
  const result = await membershipRef.value?.save()
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
  currentStep.value = Steps.ConfirmEmail
}

function onEmailCorrected(email: string) {
  if (user.value) user.value.email = email
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
    const {data} = await findAddressById({path: {id: addressId}})
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

onMounted(async () => {
  if (isLoggedIn.value) await loadSignedInApplicant()
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
