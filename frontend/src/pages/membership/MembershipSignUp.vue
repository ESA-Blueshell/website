<template>
  <v-main>
    <top-banner title="Membership Form" />

    <div
      class="mx-auto my-6"
      style="max-width: 800px"
    >
      <v-stepper
        v-model="currentStep"
        data-testid="membership-signup-stepper"
        :items="stepItems"
        hide-actions
      >
        <!-- Step 1: Personal information -->
        <template #[`item.1`]>
          <v-card class="pa-4">
            <user-form
              ref="userRef"
              v-model="user"
              :options="{ includeMemberProfile: true }"
              :show-password="!user?.id"
            />
            <v-row align="center">
              <v-spacer />
              <v-col cols="auto">
                <v-btn
                  :loading="submitting"
                  color="primary"
                  data-testid="membership-step1-next-btn"
                  @click="nextStep"
                >
                  Next
                </v-btn>
              </v-col>
            </v-row>
          </v-card>
        </template>

        <!-- Step 2: Email confirmation -->
        <template #[`item.2`]>
          <v-card class="pa-6">
            <v-row>
              <v-col cols="12">
                <div class="d-flex align-center mb-2">
                  <v-icon
                    class="mr-2"
                    color="primary"
                    size="28"
                  >
                    mdi-email-fast-outline
                  </v-icon>
                  <span class="text-h6 font-weight-medium">Check your inbox</span>
                </div>

                <v-alert
                  border="start"
                  class="mb-4"
                  color="primary"
                  variant="tonal"
                >
                  We’ve emailed <strong>{{ infoEmail }}</strong> a link to
                  <strong>activate your account</strong>.
                </v-alert>

                <div class="text-body-2 text-medium-emphasis">
                  <p class="mb-1">
                    After activating, you’ll be redirected to the sign-in page.
                  </p>
                  <p class="mb-0">
                    Once you sign in, we’ll bring you back here and move on to your address.
                  </p>
                </div>
              </v-col>
            </v-row>

            <v-row align="center">
              <v-col cols="auto">
                <v-btn
                  data-testid="membership-step2-previous-btn"
                  variant="outlined"
                  @click="previousStep"
                >
                  Previous
                </v-btn>
              </v-col>

              <v-spacer />

              <v-col
                class="d-flex"
                cols="auto"
              >
                <v-btn
                  :loading="resendBusy"
                  class="mr-2"
                  data-testid="membership-step2-resend-btn"
                  prepend-icon="mdi-email-arrow-right-outline"
                  variant="outlined"
                  @click="resendActivation"
                >
                  Resend email
                </v-btn>

                <v-btn
                  color="primary"
                  data-testid="membership-step2-signin-btn"
                  prepend-icon="mdi-check-circle-outline"
                  @click="handleVerified"
                >
                  I’ve activated — Sign in
                </v-btn>
              </v-col>
            </v-row>
          </v-card>
        </template>

        <!-- Step 3: Address -->
        <template #[`item.3`]>
          <v-card class="pa-4">
            <address-form
              ref="addressRef"
              v-model="address"
              :user-id="user?.id"
            />
            <v-row align="center">
              <v-col cols="auto">
                <v-btn
                  data-testid="membership-step3-previous-btn"
                  variant="outlined"
                  @click="previousStep"
                >
                  Previous
                </v-btn>
              </v-col>
              <v-spacer />
              <v-col cols="auto">
                <v-btn
                  :loading="submitting"
                  color="primary"
                  data-testid="membership-step3-next-btn"
                  @click="nextStep"
                >
                  Next
                </v-btn>
              </v-col>
            </v-row>
          </v-card>
        </template>

        <!-- Step 4: Membership Information -->
        <template #[`item.4`]>
          <v-card class="pa-4">
            <membership-form
              ref="membershipRef"
              v-model="membership"
            />
            <v-row align="center">
              <v-col cols="auto">
                <v-btn
                  data-testid="membership-step4-previous-btn"
                  variant="outlined"
                  @click="previousStep"
                >
                  Previous
                </v-btn>
              </v-col>
              <v-spacer />
              <v-col cols="auto">
                <v-btn
                  :loading="submitting"
                  color="primary"
                  data-testid="membership-step4-complete-btn"
                  @click="nextStep"
                >
                  Complete Membership
                </v-btn>
              </v-col>
            </v-row>
          </v-card>
        </template>
      </v-stepper>

      <div v-if="currentStep === 5">
        <v-card class="pa-6 text-center">
          <v-icon
            class="mb-4"
            color="success"
            size="64"
          >
            mdi-check-circle
          </v-icon>
          <p class="text-h6 font-weight-medium mb-2">
            Membership Complete!
          </p>
          <p class="text-body-1 text-medium-emphasis">
            Your membership form has been successfully submitted. Welcome to Blueshell!
          </p>
          <v-btn
            class="mt-4"
            color="primary"
            @click="$goto('/')"
          >
            Go to Homepage
          </v-btn>
        </v-card>
      </div>
    </div>
  </v-main>
</template>

<script lang="ts" setup>
import {computed, onMounted, ref, watch} from "vue"
import {useRoute} from "vue-router"
import TopBanner from "@/components/common/banners/TopBanner.vue"
import UserForm from "@/components/form/UserForm.vue"
import AddressForm from "@/components/form/AddressForm.vue"
import MembershipForm from "@/components/form/MembershipForm.vue"
import {
  type AddressResponse,
  type CreateUserRequest,
  findAddressById,
  findUserById,
  type MembershipResponse,
  resendUserActivation,
  Role,
  type UserDetailResponse,
} from "@/services/api"
import store from "@/plugins/store"
import {$handleNetworkError} from "@/plugins/handleNetworkError"
import {$goto} from "@/plugins/goto"
import router from "@/plugins/router.ts"

const route = useRoute()

const Steps = {Personal: 1, ConfirmEmail: 2, Address: 3, Membership: 4, Done: 5} as const

const currentStep = ref<number>(Steps.Personal)
const submitting = ref(false)
const resendBusy = ref(false)

const user = ref<CreateUserRequest & Partial<UserDetailResponse>>()
const address = ref<AddressResponse>()
const membership = ref<MembershipResponse>()

const userRef = ref<InstanceType<typeof UserForm>>()
const addressRef = ref<InstanceType<typeof AddressForm>>()
const membershipRef = ref<InstanceType<typeof MembershipForm>>()

const isLoggedIn = computed<boolean>(() => store.getters.isLoggedIn)
const login = computed(() => store.getters.getLogin)

const stepItems = computed(() => [
  {title: "Personal Information", value: Steps.Personal},
  {title: "Confirm Email", value: Steps.ConfirmEmail},
  {title: "Address", value: Steps.Address},
  {title: "Confirm Membership", value: Steps.Membership},
])

const infoEmail = computed(() => user.value?.email ?? "")

function parseStepQuery(rawStep: unknown): number {
  const rawValue = Array.isArray(rawStep) ? rawStep[0] : rawStep
  const parsed = Number(rawValue ?? Steps.Personal)
  return Number.isFinite(parsed) ? parsed : Steps.Personal
}

function resolveStep(desiredStep: number): number {
  if (desiredStep >= Steps.Done) return Steps.Done

  let step = Math.max(Steps.Personal, Math.min(desiredStep, Steps.Membership))
  const hasUser = Boolean(user.value?.id)
  const hasAddress = Boolean(login.value?.addressId || address.value?.id)

  if (step === Steps.Membership && (!isLoggedIn.value || !hasUser || !hasAddress)) {
    step = Steps.Address
  }

  if (step === Steps.Address && (!isLoggedIn.value || !hasUser)) {
    step = Steps.ConfirmEmail
  }

  if (step === Steps.ConfirmEmail) {
    if (isLoggedIn.value && hasUser) {
      step = Steps.Address
    } else if (!hasUser) {
      step = Steps.Personal
    }
  }

  return step
}

function buildQueryForStep(step: number) {
  const nextQuery = {...route.query}
  if (step <= Steps.Personal || step >= Steps.Done) {
    delete nextQuery.step
  } else {
    nextQuery.step = String(step)
  }
  return nextQuery
}

async function syncStep(desiredStep = currentStep.value) {
  const resolvedStep = resolveStep(desiredStep)
  const stepNeedsUpdate = currentStep.value !== resolvedStep

  if (resolvedStep >= Steps.Done) {
    if (stepNeedsUpdate) currentStep.value = resolvedStep
    return
  }

  const currentHasStep = route.query.step != null
  const targetHasStep = resolvedStep > Steps.Personal
  const currentQueryStep = parseStepQuery(route.query.step)
  const queryNeedsUpdate = targetHasStep !== currentHasStep || (targetHasStep && currentQueryStep !== resolvedStep)

  if (queryNeedsUpdate) {
    await router.replace({query: buildQueryForStep(resolvedStep)})
  }
  if (stepNeedsUpdate) {
    currentStep.value = resolvedStep
  }
}

async function handleVerified() {
  await router.push({name: "login", query: {redirect: "/membership/signup?step=2"}})
}

async function fetchUser() {
  const userId = login.value?.userId
  if (!userId) return
  try {
    const {data} = await findUserById({path: {userId}, throwOnError: true})
    user.value = data!
  } catch (e) {
    $handleNetworkError(e)
  }
}

async function resendActivation() {
  const username = user.value?.username
  if (!username) return
  try {
    resendBusy.value = true
    await resendUserActivation({path: {username}})
  } finally {
    resendBusy.value = false
  }
}

const nextStep = async () => {
  try {
    submitting.value = true
    switch (currentStep.value) {
      case Steps.Personal: {
        const savedUser = await userRef.value?.save()
        if (!savedUser) break
        await syncStep(isLoggedIn.value ? Steps.Address : Steps.ConfirmEmail)
        break
      }
      case Steps.ConfirmEmail: {
        if (!isLoggedIn.value) break
        await fetchUser()
        await syncStep(Steps.Address)
        break
      }
      case Steps.Address: {
        const savedAddress = await addressRef.value?.save()
        if (!savedAddress) break
        await syncStep(Steps.Membership)
        break
      }
      case Steps.Membership: {
        const savedMembership = await membershipRef.value?.save()
        if (savedMembership) {
          await router.replace({query: buildQueryForStep(Steps.Done)})
          currentStep.value = Steps.Done
        }
        break
      }
    }
  } finally {
    submitting.value = false
  }
}

const previousStep = () => {
  if (currentStep.value <= Steps.Personal) return
  const target = currentStep.value - 1
  const desiredStep = target === Steps.ConfirmEmail && isLoggedIn.value ? Steps.Personal : target
  void syncStep(desiredStep)
}

async function fetchAddress() {
  const addressId = login.value?.addressId || address.value?.id
  if (!addressId) return
  try {
    const {data} = await findAddressById({path: {id: addressId}})
    address.value = data!
  } catch (e) {
    $handleNetworkError(e)
  }
}

watch(currentStep, async (step) => {
  await syncStep(step)
})

watch(
  () => route.query.step,
  async () => {
    await syncStep(parseStepQuery(route.query.step))
  }
)

watch(
  () => login.value?.userId,
  async (userId, previousUserId) => {
    if (!userId) {
      await syncStep(currentStep.value)
      return
    }

    if (userId !== previousUserId || !user.value?.id) {
      await fetchUser()
    }

    if (login.value?.addressId && !address.value?.id) {
      await fetchAddress()
    }

    if (currentStep.value === Steps.ConfirmEmail) {
      await syncStep(Steps.Address)
      return
    }

    await syncStep(currentStep.value)
  }
)

// If a user is already a member, then redirect them to a different page.
watch(user, async (val) => {
  if (!val?.roles?.includes(Role.MEMBER)) return

  store.commit("setStatusSnackbarMessage", "you are already a member")
  const rawBackTarget = (window.history.state && window.history.state.back) as string | undefined
  let normalizedBackTarget: string | null = null

  if (rawBackTarget) {
    try {
      const parsed = new URL(rawBackTarget, window.location.origin)
      normalizedBackTarget = `${parsed.pathname}${parsed.search}${parsed.hash}`
    } catch {
      normalizedBackTarget = rawBackTarget.startsWith("/") ? rawBackTarget : null
    }
  }

  const shouldUseBackTarget = Boolean(
    normalizedBackTarget &&
      normalizedBackTarget !== route.fullPath &&
      !normalizedBackTarget.startsWith("/membership/signup")
  )

  await router.replace(shouldUseBackTarget ? normalizedBackTarget! : "/")
})

async function fetchData() {
  if (!login.value?.userId) return
  await fetchUser()

  if (!login.value?.addressId && !address.value?.id) return
  await fetchAddress()
}

onMounted(async () => {
  await fetchData()
  await syncStep(parseStepQuery(route.query.step))
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
