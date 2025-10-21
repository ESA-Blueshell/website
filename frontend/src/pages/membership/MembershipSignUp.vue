<template>
  <v-main>
    <top-banner title="Membership Form" />

    <div
      v-if="currentStep <= 3"
      class="mx-3 pb-10"
    >
      <v-stepper
        v-model="currentStep"
        :items="steps"
        class="mx-auto mt-10"
        hide-actions
        style="max-width: 800px"
      >
        <template #item.1>
          <v-card class="pa-4">
            <advanced-user-form
              ref="userRef"
              v-model="user"
              :show-password="!user?.id"
            />

            <v-row class="mt-4">
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

          <v-expand-transition>
            <v-alert
              v-if="waitingForVerification"
              type="info"
              variant="tonal"
              class="mt-4"
            >
              <div class="mb-2">
                We’ve emailed <strong>{{ infoEmail }}</strong> a link to activate your account.
              </div>
              <div class="text-medium-emphasis">
                Open the email and click the link. Once verified, we’ll automatically take you to the next step.
              </div>

              <div
                class="d-flex align-center mt-3"
                style="gap: 8px;"
              >
                <v-btn
                  :loading="resendBusy"
                  size="small"
                  variant="outlined"
                  @click="resendActivation"
                >
                  Resend email
                </v-btn>
                <v-btn
                  size="small"
                  color="primary"
                  @click="refreshUser"
                >
                  I’ve verified
                </v-btn>
              </div>
            </v-alert>
          </v-expand-transition>
        </template>

        <template #item.2>
          <v-card class="pa-4">
            <address-form
              ref="addressRef"
              v-model="address"
              :user-id="user.id"
            />

            <v-row class="mt-4">
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

        <!-- Step 3: Membership Information -->
        <template #item.3>
          <v-card class="pa-4">
            <membership-form
              ref="membershipRef"
              v-model="membership"
            />

            <v-row class="mt-4">
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
                  :loading="saving"
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
    </div>

    <div
      v-else
      class="mx-auto my-10"
      style="max-width: 600px"
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
          Your membership form has been successfully submitted. Welcome to Blueshell E-Sports!
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
  </v-main>
</template>

<script lang="ts" setup>
import {computed, onMounted, ref, type Ref} from "vue"
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

const currentStep: Ref<number> = ref(1)

const user = ref<AdvancedUser>()
const address = ref<Address>()
const membership = ref<Membership>()
const submitting: Ref<boolean> = ref(false)

const userRef = ref<InstanceType<typeof AdvancedUserForm>>()
const addressRef = ref<InstanceType<typeof AddressForm>>()
const membershipRef = ref<InstanceType<typeof MembershipForm>>()

const steps = [
  {title: "Personal Information", value: 1},
  {title: "Address", value: 2},
  {title: "Confirm Membership", value: 3},
]

const waitingForVerification = ref(false)
const resendBusy = ref(false)
const infoEmail = computed(() => user.value?.email ?? "")

async function refreshUser(): Promise<void> {
  if (!user.value?.id) return

  try {
    const response = await findUserById({path: {userId: user.value.id}, throwOnError: true})
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

const nextStep = async (): Promise<void> => {
  try {
    submitting.value = true
    if (currentStep.value === 1) {
      const savedUser = await userRef.value?.save()
      if (savedUser) {
        user.value = savedUser
        await fetchAddress()

        await refreshUser()
        if (!user.value?.enabled) {
          waitingForVerification.value = true
          const started = Date.now()
          const poll = setInterval(async () => {
            await refreshUser()
            if (user.value?.enabled) {
              clearInterval(poll)
              waitingForVerification.value = false
              currentStep.value += 1
            } else if (Date.now() - started > 120000) { // stop after 2 min
              clearInterval(poll)
            }
          }, 5000)
          return
        }

        currentStep.value += 1
      }
    } else if (currentStep.value === 2) {
      const savedAddress = await addressRef.value?.save()
      if (savedAddress) {
        currentStep.value += 1
        user.value!.addressId = savedAddress.id!
      }
    } else if (currentStep.value === 3) {
      const savedMembership = await membershipRef.value?.save()
      if (savedMembership) {
        currentStep.value += 1
      }
    }
  } finally {
    submitting.value = false
  }
}

const previousStep = (): void => {
  if (currentStep.value > 1) {
    currentStep.value -= 1
  }
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
    const response = await findAddressById({
      path: {
        id: user.value.addressId!,
      },
    })

    address.value = response.data!
  } catch (e) {
    $handleNetworkError(e)
  }
}

onMounted(async () => {
  const login = store.getters.getLogin
  if (!login?.userId) return

  try {
    const response = await findUserById({
      path: {
        userId: login.userId,
      },
    })

    user.value = response.data!
  } catch (e) {
    $handleNetworkError(e)
  }
})
</script>

<style lang="scss" scoped>
.v-stepper {
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.1);
}

.v-card {
  border-radius: 12px;
}

.v-col:first-child {
  padding-left: 0;
}

.v-col:last-child {
  padding-right: 0;
}
</style>
