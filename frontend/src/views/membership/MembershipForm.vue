<template>
  <v-main>
    <top-banner title="Membership Form" />

    <div
      v-if="!succeeded"
      class="mx-3 pb-10"
    >
      <v-stepper
        v-model="currentStep"
        :items="steps"
        class="mx-auto mt-10"
        hide-actions
        style="max-width: 800px"
      >
        <!-- Step 1: User Information -->
        <template #item.1>
          <v-card class="pa-4">
            <advanced-user-form
              ref="userEditRef"
              v-model="userData"
              :creating="!loggedIn"
              :editing="loggedIn"
            />

            <v-row class="mt-4">
              <v-spacer />
              <v-col cols="auto">
                <v-btn
                  :loading="saving"
                  color="primary"
                  @click="nextStep"
                >
                  Next
                </v-btn>
              </v-col>
            </v-row>
          </v-card>
        </template>

        <!-- Step 2: Address Information -->
        <template #item.2>
          <v-card class="pa-4">
            <address-edit
              ref="addressEditRef"
              v-model="addressData"
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
                  Next
                </v-btn>
              </v-col>
            </v-row>
          </v-card>
        </template>

        <!-- Step 3: Membership Information -->
        <template #item.3>
          <v-card class="pa-4">
            <v-card-title>Membership Agreement</v-card-title>
            <membership-edit
              ref="membershipEditRef"
              v-model="membershipData"
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
                  @click="completeMembership"
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
      v-else-if="succeeded"
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
import {onMounted, ref, type Ref} from "vue"
import {DateTime} from "luxon"
import TopBanner from "@/components/banners/TopBanner.vue"
import AdvancedUserForm from "@/components/user/AdvancedUserForm.vue"
import AddressEdit from "@/components/edit/AddressEdit.vue"
import MembershipEdit from "@/components/edit/MembershipEdit.vue"
import type {Address, AdvancedUser, Membership} from "@/lib"
import {createUser, findUserById, updateUser} from "@/lib"

import store from "@/plugins/store"
import {$handleNetworkError} from "@/plugins/handleNetworkError"
import {$goto} from "@/plugins/goto"

// Reactive state
const currentStep: Ref<number> = ref(1)
const succeeded: Ref<boolean> = ref(false)
const saving: Ref<boolean> = ref(false)
const loggedIn: Ref<boolean> = ref(false)

// Form data
const userData: Ref<AdvancedUser> = ref({
  initials: "",
  firstName: "",
  lastName: "",
  prefix: "",
  email: "",
  username: "",
  phoneNumber: "",
  dateOfBirth: "",
  nationality: "",
  discord: "",
  newsletter: false,
  photoConsent: false,
  ehbo: false,
  bhv: false,
  incasso: false,
  studentNumber: "",
  gender: "",
} as AdvancedUser)

const addressData: Ref<Address> = ref({
  street: "",
  houseNumber: "",
  zipCode: "",
  city: "",
  country: "",
} as Address)

const membershipData: Ref<Membership> = ref({
  userId: 0,
  memberType: "REGULAR",
  city: "",
  date: DateTime.now().toISODate(),
} as Membership)

// Template refs
const userEditRef: Ref<any> = ref(null)
const addressEditRef: Ref<any> = ref(null)
const membershipEditRef: Ref<any> = ref(null)

// Steps configuration
const steps = [
  {title: "Personal Information", value: 1},
  {title: "Address", value: 2},
  {title: "Membership", value: 3},
]

// Methods
const nextStep = async (): Promise<void> => {
  saving.value = true

  try {
    if (currentStep.value === 1) {
      // Validate and save user data
      if (!await validateAndSaveUserData()) {
        return
      }
    } else if (currentStep.value === 2) {
      // Validate and save address data
      if (!await validateAndSaveAddressData()) {
        return
      }
    }

    currentStep.value += 1
  } catch (error: unknown) {
    $handleNetworkError(error)
  } finally {
    saving.value = false
  }
}

const previousStep = (): void => {
  if (currentStep.value > 1) {
    currentStep.value -= 1
  }
}

const validateAndSaveUserData = async (): Promise<boolean> => {
  // Validate the child component
  if (!userEditRef.value) {
    return false
  }

  const userEditValid = await userEditRef.value.validateForm()
  if (!userEditValid) {
    return false
  }

  try {
    let response: { data?: AdvancedUser }

    if (loggedIn.value && userData.value.id) {
      // Update existing user
      response = await updateUser({
        path: {userId: userData.value.id},
        body: userData.value,
        client,
      })
    } else {
      // Create new user
      response = await createUser({
        body: userData.value,
        client,
      })
    }

    if (response.data) {
      userData.value = response.data
      membershipData.value.userId = response.data.id!
      return true
    }

    return false
  } catch (error: any) {
    if (error.response?.status === 400) {
      store.commit("setStatusSnackbarMessage", error.response.data)
    } else {
      $handleNetworkError(error)
    }
    return false
  }
}

const validateAndSaveAddressData = async (): Promise<boolean> => {
  if (!addressEditRef.value) {
    return false
  }

  // Validate address using child component validation
  if (!addressEditRef.value.validateAddress()) {
    return false
  }

  try {
    // Save address using child component method
    await addressEditRef.value.saveAddress()

    // Link address to user if we have both IDs
    if (userData.value.id && addressData.value.id) {
      // Update user with address ID - this would typically be done on the backend
      // but we'll set it locally for now
      (userData.value as any).addressId = addressData.value.id
    }

    return true
  } catch (error: unknown) {
    $handleNetworkError(error)
    return false
  }
}

const completeMembership = async (): Promise<void> => {
  saving.value = true

  try {
    if (!membershipEditRef.value) {
      return
    }

    // Validate and save membership using child component method
    const membershipSaved = await membershipEditRef.value.saveMembership()

    if (!membershipSaved) {
      return
    }

    // Mark as succeeded
    succeeded.value = true

    // Update user roles if logged in
    if (loggedIn.value && userData.value.id) {
      // Fetch updated user data to get new roles
      const response = await findUserById({
        path: {userId: userData.value.id},
        client,
      })

      if (response.data) {
        store.commit("setRoles", response.data.roles)
      }
    }

  } catch (error: any) {
    if (error.response?.status === 400) {
      store.commit("setStatusSnackbarMessage", error.response.data)
    } else {
      $handleNetworkError(error)
    }
  } finally {
    saving.value = false
  }
}

// Lifecycle hooks
onMounted(async () => {
  const login = store.getters.getLogin
  loggedIn.value = !!login

  if (login && login.userId) {
    try {
      // Fetch existing user data
      const response = await findUserById({
        path: {userId: login.userId},
        client,
      })

      if (response.data) {
        userData.value = {...response.data}
        membershipData.value.userId = response.data.id!

        // If user has an address, fetch it
        if ((response.data as any).address) {
          addressData.value = {...(response.data as any).address}
        }
      }
    } catch (error: unknown) {
      console.error("Failed to fetch user data:", error)
      $handleNetworkError(error)
    }
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
