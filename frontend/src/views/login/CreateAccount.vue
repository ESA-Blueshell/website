<template>
  <v-main>
    <top-banner title="Create Account" />

    <div
      v-if="!succeeded"
      class="mx-3 pb-10"
    >
      <v-form
        ref="form"
        class="mx-auto mt-10"
        style="max-width: 600px"
      >
        <SimpleUserEdit
          ref="userEditComponent"
          v-model="form"
          :creating="true"
        />
        <v-spacer />
        <v-col cols="auto">
          <v-btn
            :loading="loading"
            color="primary"
            @click="createAccount"
          >
            Create account
          </v-btn>
        </v-col>
      </v-form>
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

<script setup lang="ts">
import {onMounted, ref} from 'vue';
import TopBanner from "@/components/banners/TopBanner.vue";
import {createGuestUser, type SimpleUserDto} from '@/lib';

import {$handleNetworkError} from "@/plugins/handleNetworkError.js";
import store from '@/plugins/store';
import type {AxiosError} from "axios";
import SimpleUserEdit from "@/components/user/SimpleUserEdit.vue";

// Reactive state
const loading = ref(false);
const succeeded = ref(false);
const form = ref<SimpleUserDto>({
  username: '',
  initials: '',
  firstName: '',
  lastName: '',
  password: '',
  email: '',
  discord: '',
  prefix: '',
  newsletter: true,
});

// Component references
const userEditComponent = ref();

onMounted(() => {
  form.value.newsletter = true;
});

// Methods
const createAccount = async () => {
  // Validate the UserEdit component
  const isValid = await userEditComponent.value?.validateForm();

  if (!isValid) {
    return;
  }

  loading.value = true;

  try {
    // Use the generated OpenAPI client to create user
    const response = await createGuestUser({
      body: form.value,
      client
    });

    if (response.data) {
      succeeded.value = true;
    }
  } catch (error: AxiosError) {
    if (error?.response?.status === 400) {
      store.commit('setStatusSnackbarMessage', error.response.data);
    } else {
      $handleNetworkError(error);
    }
  } finally {
    loading.value = false;
  }
};
</script>
