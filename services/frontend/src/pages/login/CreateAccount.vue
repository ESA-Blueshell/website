<template>
  <v-main>
    <TopBanner title="Create Account" />

    <div
      v-if="!session"
      class="mx-auto pb-10 mt-10"
      data-testid="create-account-form-state"
      style="max-width: 600px"
    >
      <user-form
        ref="userRef"
        v-model="user"
        data-testid="create-account-user-form"
        show-password
        show-submit
        :signup-token="signupSession?.signupToken"
        :submit-text="submitText"
        @submitted="onFormSubmitted"
      />
    </div>

    <div
      v-else
      class="mx-auto my-10"
      data-testid="create-account-success-state"
      style="max-width: 600px"
    >
      <email-confirmation-panel
        :email="user?.email ?? ''"
        :username="user?.username ?? ''"
        :continuation-token="session.signupToken"
        confirmation-consequence="Your account works as soon as you do."
        @back="editing = true"
        @email-corrected="onEmailCorrected"
      />
    </div>
  </v-main>
</template>

<script lang="ts" setup>
import {computed, ref} from "vue"
import TopBanner from "@/components/common/banners/TopBanner.vue"
import UserForm from "@/components/form/UserForm.vue"
import EmailConfirmationPanel from "@/components/form/EmailConfirmationPanel.vue"
import type {SignupSessionResponse} from "@/services/api"
import type {EditableUser} from "@/utils/editableUser"

const user = ref<EditableUser>()
const userRef = ref<InstanceType<typeof UserForm>>()
const signupSession = ref<SignupSessionResponse>()
const editing = ref(false)

// The confirmation panel shows once an account exists and the applicant is not
// back in the form correcting something.
const session = computed(() => (editing.value ? undefined : signupSession.value))
const submitText = computed(() => (signupSession.value ? "Save Changes" : "Create Account"))

const onFormSubmitted = (ok: boolean) => {
  if (!ok) return
  signupSession.value = userRef.value?.signupSession ?? signupSession.value
  editing.value = false
}

const onEmailCorrected = (email: string) => {
  if (user.value) user.value.email = email
}
</script>
