<template>
  <account-frame
    :crumb="{label: 'Login', to: '/login'}"
    heading="Create account"
    island-content
    :tabs="false"
  >
    <task-layout
      aside-title="Becoming a member?"
      wide
    >
      <div
        v-if="!session"
        class="island-panel"
        data-testid="create-account-form-state"
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
        data-testid="create-account-success-state"
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

      <template #aside>
        <p>
          You do not need an account first. The <router-link
            to="/membership/signup"
          >
            membership form
          </router-link> creates one for you.
        </p>
        <p>
          Already have an account? <router-link
            to="/login"
          >
            Log in
          </router-link>
        </p>
      </template>
    </task-layout>
  </account-frame>
</template>

<script lang="ts" setup>
import {computed, ref} from "vue"
import AccountFrame from "@/components/common/AccountFrame.vue"
import TaskLayout from "@/components/island/TaskLayout.vue"
import UserForm from "@/components/form/UserForm.vue"
import EmailConfirmationPanel from "@/components/form/EmailConfirmationPanel.vue"
import type {SignupSessionResponse} from "@/domains/user"
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
