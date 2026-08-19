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
            <membership-form
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
          <v-card
            class="pa-6"
            data-testid="membership-confirm-email-step"
          >
            <div class="d-flex align-center mb-2">
              <v-icon
                class="mr-2"
                color="primary"
                size="28"
              >
                mdi-email-fast-outline
              </v-icon>
              <span class="text-h6 font-weight-medium">Confirm your email address</span>
            </div>

            <v-alert
              border="start"
              class="mb-4"
              color="primary"
              variant="tonal"
            >
              Open the link we sent to <strong>{{ user?.email }}</strong> to confirm your
              address. Your membership starts as soon as you do.
            </v-alert>

            <v-form
              v-if="correcting"
              data-testid="membership-correct-email-form"
              @submit.prevent="correctEmailAddress"
            >
              <v-text-field
                v-model="correctedEmail"
                data-testid="membership-corrected-email-field"
                label="Email address"
                type="email"
              />
              <v-row
                align="center"
                justify="end"
              >
                <v-col cols="auto">
                  <v-btn
                    variant="text"
                    @click="correcting = false"
                  >
                    Cancel
                  </v-btn>
                </v-col>
                <v-col cols="auto">
                  <v-btn
                    :loading="submitting"
                    color="primary"
                    data-testid="membership-corrected-email-submit-btn"
                    type="submit"
                  >
                    Send to this address
                  </v-btn>
                </v-col>
              </v-row>
            </v-form>

            <v-row
              v-else
              align="center"
            >
              <v-col cols="auto">
                <v-btn
                  data-testid="membership-correct-email-btn"
                  variant="outlined"
                  @click="startCorrectingEmail"
                >
                  Wrong address?
                </v-btn>
              </v-col>
              <v-spacer />
              <v-col cols="auto">
                <v-btn
                  color="primary"
                  data-testid="membership-sign-in-btn"
                  prepend-icon="mdi-login"
                  @click="$goto('/login')"
                >
                  Sign in
                </v-btn>
              </v-col>
            </v-row>
          </v-card>
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
import {
  type AddressResponse,
  correctEmail,
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
const correcting = ref(false)
const correctedEmail = ref("")

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

const saveDetails = () => withSubmitting(async () => {
  const saved = await userRef.value?.save()
  if (!saved) return
  const session = userRef.value?.signupSession
  if (session) rememberToken(session.signupToken)
  currentStep.value = Steps.Address
})

const saveAddressStep = () => withSubmitting(async () => {
  if (!await addressRef.value?.save()) return
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
  currentStep.value = Steps.ConfirmEmail
}

function startCorrectingEmail() {
  correctedEmail.value = user.value?.email ?? ""
  correcting.value = true
}

const correctEmailAddress = () => withSubmitting(async () => {
  const token = signupToken.value
  if (!token || !correctedEmail.value) return
  try {
    await correctEmail({
      headers: {"X-Signup-Token": token},
      body: {email: correctedEmail.value},
      throwOnError: true,
    })
    if (user.value) user.value.email = correctedEmail.value
    correcting.value = false
    store.commit("setStatusSnackbarMessage", `Confirmation sent to ${correctedEmail.value}`)
  } catch (e) {
    $handleNetworkError(e)
  }
})

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
