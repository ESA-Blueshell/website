<template>
  <v-main>
    <top-banner title="Create Account" />

    <div
      v-if="!succeeded"
      class="mx-3 pb-10"
    >
      <Form
        as="div"
        class="mx-auto mt-10"
        style="max-width: 600px"
      >
        <SimpleUserForm
          ref="simpleRef"
          v-model="userForm"
        />
        <v-spacer />
        <v-row class="justify-end">
          <v-btn
            :loading="loading"
            color="primary"
            @click="createAccount"
          >
            Create account
          </v-btn>
        </v-row>
      </Form>
    </div>

    <div
      v-else-if="succeeded"
      class="mx-auto my-10"
      style="max-width: 600px"
    >
      <p class="text-center text-subtitle-1 font-weight-light">
        Your account has successfully been created! Head over to your email to confirm your account.
        If you can't find the email, make sure to check your spam folder.
      </p>
    </div>
  </v-main>
</template>

<script lang="ts" setup>
import {ref} from "vue"
import TopBanner from "@/components/banners/TopBanner.vue"
import {createGuestUser, type SimpleUser} from "@/lib"
import {Form} from "vee-validate"
import {$handleNetworkError} from "@/plugins/handleNetworkError.js"
import SimpleUserForm from "@/components/user/SimpleUserForm.vue"

// Reactive state
const loading = ref(false)
const succeeded = ref(false)
const userForm = ref<SimpleUser>({
  username: "",
  initials: "",
  firstName: "",
  lastName: "",
  password: "",
  email: "",
  discord: "",
  prefix: "",
  newsletter: true,
})

const simpleRef = ref<InstanceType<typeof SimpleUserForm>>()


// Methods
const createAccount = async () => {
  const isValid = await simpleRef.value?.validateForm()

  if (!isValid) return

  loading.value = true

  try {
    // Use the generated OpenAPI client to create user
    const response = await createGuestUser({
      body: userForm.value,
    })

    if (response.status === 201) {
      succeeded.value = true
    } else if (!await simpleRef.value?.applyErrors(response)) {
      $handleNetworkError(response)
    }
  } finally {
    loading.value = false
  }
}
</script>
