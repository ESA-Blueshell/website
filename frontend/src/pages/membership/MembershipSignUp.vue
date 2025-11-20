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
        <!-- Step 1: Personal information -->
        <template #[`item.1`]>
          <v-card class="pa-4">
            <advanced-user-form
              ref="userRef"
              v-model="user"
              :show-password="!user?.id"
            />
            <v-row align="center">
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
                  prepend-icon="mdi-email-arrow-right-outline"
                  variant="outlined"
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
            <v-row align="center">
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
  Role,
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

const user = ref<AdvancedUser>()
const address = ref<Address>()
const membership = ref<Membership>()

const userRef = ref<InstanceType<typeof AdvancedUserForm>>()
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

async function handleVerified() {
  if (isLoggedIn.value) await fetchData()
  else await router.push({name: "login", query: {redirect: "/membership/signUp?step=2"}})
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
        await userRef.value?.save()
        currentStep.value = isLoggedIn.value ? Steps.Address : Steps.ConfirmEmail
        break
      }
      case Steps.ConfirmEmail:
        if (isLoggedIn.value) currentStep.value = Steps.Address
        break
      case Steps.Address: {
        await addressRef.value?.save()
        currentStep.value = Steps.Membership
        break
      }
      case Steps.Membership: {
        const savedMembership = await membershipRef.value?.save()
        if (savedMembership) currentStep.value = Steps.Done
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
  currentStep.value = target === Steps.ConfirmEmail && isLoggedIn.value ? Steps.Personal : target
}

async function fetchAddress() {
  const addressId = user.value?.addressId || address.value?.id
  if (!addressId) return
  try {
    const {data} = await findAddressById({path: {id: addressId}})
    address.value = data!
  } catch (e) {
    $handleNetworkError(e)
  }
}

// Keep URL in sync, redirect to correct steps based on state
watch(currentStep, async (val) => {
  let step = Math.max(0, Math.min(val, Steps.Membership))

  const userValid = Boolean(user.value?.id && await userRef.value?.validate())
  const addressValid = Boolean(address.value?.id && await addressRef.value?.validate())

  switch (step) {
    case Steps.Membership:
      // It is only allowed to become a member if a user is signed in, is valid, and has a valid address
      // if that is not the case go back by one step
      if (!isLoggedIn.value || !userValid || !addressValid) {
        step = Steps.Address
      }
      break

    case Steps.Address:
      // It is only allowed to modify an address if a user is signed in and is valid
      // if that is not the case go back by one step
      if (!isLoggedIn.value || !userValid) {
        step = Steps.ConfirmEmail
      }
      break

    case Steps.ConfirmEmail:
      // One may only be on the confirm email page if they have done the initial account creation. but have not logged in

      if (isLoggedIn.value && userValid) {
        // If a user is logged in and has a valid user, then go to the address page
        step = Steps.Address
      } else if (!userValid) {
        // If the user is not valid, go to personal to make it valid
        step = Steps.Personal
      }
      // Otherwise, they are not logged in, but user is valid, they may stay on the page
      break
  }

  await router.replace({query: {step}})
  currentStep.value = step
})

// If a user is already a member, then redirect them to a different page.
watch(user, async (val) => {
  if (!val?.roles?.includes(Role.MEMBER)) return

  store.commit("setStatusSnackbarMessage", "you are already a member")
  const backTarget = (window.history.state && window.history.state.back) as string | undefined
  await router.replace(backTarget && backTarget !== route.fullPath ? backTarget : "/")
})

async function fetchData() {
  if (!login.value?.userId) return
  await fetchUser()

  if (!user.value?.addressId && !address.value?.id) return
  await fetchAddress()
}

onMounted(async () => {
  await fetchData()
  currentStep.value = Number(route.query.step ?? 1)
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
