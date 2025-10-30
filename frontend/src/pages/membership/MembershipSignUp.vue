<template>
  <v-main>
    <top-banner title="Membership Form" />

    <div
      class="mx-auto my-6"
      style="max-width: 800px"
    >
      <v-stepper
        v-model="currentStep"
        :items="stepItems"
        hide-actions
      >
        <!-- Step 1: Personal information / create-or-update user -->
        <template #[`item.1`]>
          <v-card class="pa-4">
            <advanced-user-form
              ref="userRef"
              v-model="user"
              :show-password="!user?.id"
            />

            <v-row
              align="center"
            >
              <v-spacer />
              <v-col cols="auto">
                <v-btn
                  :loading="submitting"
                  color="primary"
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
                    size="28"
                    color="primary"
                    class="mr-2"
                  >
                    mdi-email-fast-outline
                  </v-icon>
                  <span class="text-h6 font-weight-medium">Check your inbox</span>
                </div>

                <v-alert
                  variant="tonal"
                  color="primary"
                  border="start"
                  class="mb-4"
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
                  color="primary"
                  variant="text"
                  @click="previousStep"
                >
                  Previous
                </v-btn>
              </v-col>

              <v-spacer />

              <v-col
                cols="auto"
                class="d-flex"
              >
                <v-btn
                  :loading="resendBusy"
                  variant="outlined"
                  class="mr-2"
                  prepend-icon="mdi-email-arrow-right-outline"
                  @click="resendActivation"
                >
                  Resend email
                </v-btn>

                <v-btn
                  color="primary"
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

            <v-row
              align="center"
            >
              <v-col cols="auto">
                <v-btn
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
                  @click="nextStep"
                >
                  Next
                </v-btn>
              </v-col>
            </v-row>
          </v-card>
        </template>

        <!-- Step 4: Membership Information / submit -->
        <template #[`item.4`]>
          <v-card class="pa-4">
            <membership-form
              ref="membershipRef"
              v-model="membership"
            />

            <v-row
              align="center"
            >
              <v-col cols="auto">
                <v-btn
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
                  @click="nextStep"
                >
                  Complete Membership
                </v-btn>
              </v-col>
            </v-row>
          </v-card>
        </template>
      </v-stepper>

      <div
        v-if="currentStep == 5"
      >
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
import {computed, onBeforeUnmount, onMounted, ref, type Ref, watch} from "vue"
import {useRoute} from "vue-router"
import TopBanner from "@/components/common/banners/TopBanner.vue"
import AdvancedUserForm from "@/components/form/AdvancedUserForm.vue"
import AddressForm from "@/components/form/AddressForm.vue"
import MembershipForm from "@/components/form/MembershipForm.vue"
import {
  type Address,
  type AdvancedUser,
  findAddressById,
  findUserById,
  type Membership,
  resendUserActivation,
} from "@/services/api"

import store from "@/plugins/store"
import {$handleNetworkError} from "@/plugins/handleNetworkError"
import {$goto} from "@/plugins/goto"
import router from "@/plugins/router.ts"

const route = useRoute()

const currentStep: Ref<number> = ref(1)
const submitting: Ref<boolean> = ref(false)

const isLoggedIn = computed<boolean>(() => store.getters.isLoggedIn)
const isMember = computed(() => store.getters.isMember)
const login = computed(() => store.getters.getLogin)

const user = ref<AdvancedUser>()
const address = ref<Address>()
const membership = ref<Membership>()

const userRef = ref<InstanceType<typeof AdvancedUserForm>>()
const addressRef = ref<InstanceType<typeof AddressForm>>()
const membershipRef = ref<InstanceType<typeof MembershipForm>>()

const stepItems = computed(() => [
  {title: "Personal Information", value: 1},
  {title: "Confirm Email", value: 2},
  {title: "Address", value: 3},
  {title: "Confirm Membership", value: 4},
])

const infoEmail = computed(() => user.value?.email ?? "")

function bounceIfMember() {
  if (!isMember.value) return

  stopLoginPoll?.()
  store.commit("setStatusSnackbarMessage", "you are already a member")
  const backTarget = (window.history.state && (window.history.state).back) as string | undefined

  if (backTarget && backTarget !== route.fullPath) {
    router.replace(backTarget) // replaces the current page with the previous page
  } else {
    router.replace("/") // fallback
  }
}

async function handleVerified() {
  if (isLoggedIn.value) {
    await onLoggedInAfterActivation()
  } else {
    await router.push({name: "login", query: {redirect: "/membership/signUp?step=2"}})
  }
}

async function refreshUser(): Promise<void> {
  if (!login.value?.userId) return
  try {
    const response = await findUserById({path: {userId: login.value.userId}, throwOnError: true})
    user.value = response.data!
  } catch (e: unknown) {
    $handleNetworkError(e)
  }
}

async function resendActivation(): Promise<void> {
  if (!user.value?.username) return
  try {
    resendBusy.value = true
    await resendUserActivation({path: {username: user.value.username}})
  } finally {
    resendBusy.value = false
  }
}

const resendBusy = ref(false)

const nextStep = async (): Promise<void> => {
  try {
    submitting.value = true

    if (currentStep.value === 1) {
      const savedUser = await userRef.value?.save()
      if (!savedUser) return

      user.value = savedUser

      // If not logged in yet, start polling; otherwise skip directly to Address (step 3)
      if (!isLoggedIn.value) startLoginPoll()
      currentStep.value = isLoggedIn.value ? 3 : 2
      return
    }

    if (currentStep.value === 2) {
      // If we somehow land here while logged in, just skip ahead
      if (isLoggedIn.value) {
        currentStep.value = 3
      }
      return
    }

    if (currentStep.value === 3) {
      const savedAddress = await addressRef.value?.save()
      if (savedAddress) {
        if (user.value) user.value.addressId = savedAddress.id!
        currentStep.value = 4
      }
      return
    }

    if (currentStep.value === 4) {
      const savedMembership = await membershipRef.value?.save()
      if (savedMembership) {
        currentStep.value = 5 // show completion screen
      }
      return
    }
  } finally {
    submitting.value = false
  }
}

const previousStep = (): void => {
  if (currentStep.value <= 1) return
  const target = currentStep.value - 1
  // If user is logged in, skip over email confirmation when going backwards
  currentStep.value = target === 2 && isLoggedIn.value ? 1 : target
}

const fetchAddress = async (): Promise<void> => {
  if (!user.value?.addressId) {
    address.value = {
      country: "NL",
      city: "",
      street: "",
      houseNumber: "",
      zipCode: "",
    }
    return
  }

  try {
    const response = await findAddressById({path: {id: user.value.addressId!}})
    address.value = response.data!
  } catch (e) {
    $handleNetworkError(e)
  }
}

let loginPollId: number | undefined
const startLoginPoll = () => {
  stopLoginPoll()
  loginPollId = window.setInterval(async () => {
    if (isLoggedIn.value) {
      stopLoginPoll()
      await onLoggedInAfterActivation()
    }
  }, 1500)
}
const stopLoginPoll = () => {
  if (loginPollId) {
    clearInterval(loginPollId)
    loginPollId = undefined
  }
}

const onLoggedInAfterActivation = async () => {
  await refreshUser()
  await fetchAddress()
  if (currentStep.value === 2 || currentStep.value === 1) currentStep.value = 3
}

// Keep the URL in sync and ensure step 2 is skipped when logged in
watch(currentStep, (val) => {
  if (val === 2 && isLoggedIn.value) {
    currentStep.value = 3
    return
  }
  // Clamp to 4 for URL query
  const qStep = Math.min(val, 4)
  router.replace({query: {step: String(qStep)}})
})

onMounted(async () => {
  bounceIfMember()

  const qsStep = Number(route.query.step)
  if ([1, 2, 3, 4].includes(qsStep)) {
    currentStep.value = isLoggedIn.value && qsStep === 2 ? 3 : qsStep
  }

  const loginInfo = store.getters.getLogin
  if (loginInfo?.userId) {
    try {
      const response = await findUserById({path: {userId: loginInfo.userId}})
      user.value = response.data!
      await fetchAddress()
    } catch (e) {
      $handleNetworkError(e)
    }
  }
})

onBeforeUnmount(() => {
  stopLoginPoll()
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
