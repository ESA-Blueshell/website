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
import {onMounted, ref, type Ref} from "vue"
import TopBanner from "@/components/common/banners/TopBanner.vue"
import AdvancedUserForm from "@/components/form/AdvancedUserForm.vue"
import AddressForm from "@/components/form/AddressForm.vue"
import MembershipForm from "@/components/form/MembershipForm.vue"
import {type Address, type AdvancedUser, findAddressById, findUserById, type Membership} from "@/services/api"

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

const nextStep = async (): Promise<void> => {
  try {
    submitting.value = true
    if (currentStep.value === 1) {
      const savedUser = await userRef.value?.save()
      if (savedUser) {
        await fetchAddress()
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
      userId: user.value!.id!,
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
