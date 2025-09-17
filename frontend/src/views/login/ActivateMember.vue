<template>
  <v-form
    ref="formRef"
    v-model="valid"
    class="mx-auto"
    style="max-width: 500px"
  >
    <v-text-field
      v-model="form.username"
      :rules="usernameRules"
      label="Username"
    />
    <v-text-field
      v-model="form.password"
      :rules="passwordRules"
      label="Password"
      :append-inner-icon="showPass ? 'mdi-eye' : 'mdi-eye-off'"
      :type="showPass ? 'text' : 'password'"
      @click:append-inner="showPass = !showPass"
    />
    <v-text-field
      v-model="passwordAgain"
      :rules="passwordConfirmRules"
      label="Repeat Password"
      :append-inner-icon="showPass ? 'mdi-eye' : 'mdi-eye-off'"
      :type="showPass ? 'text' : 'password'"
      @click:append-inner="showPass = !showPass"
    />
    <v-row
      class="mt-2"
      align="center"
      justify="end"
    >
      <v-btn
        :loading="loading"
        :disabled="!valid"
        color="primary"
        @click="submit"
      >
        Activate Member
      </v-btn>
    </v-row>

    <div
      v-if="succeeded"
      class="mt-6"
    >
      <p class="text-subtitle-1">
        Membership activated! You can now log in.
      </p>
    </div>
  </v-form>
</template>

<script setup lang="ts">
import {onMounted, ref} from 'vue';
import type {VForm} from 'vuetify/components';
import {useRoute, useRouter} from 'vue-router';
import {memberActivate} from '@/lib';
import type {MemberActivationRequest} from '@/lib';

const route = useRoute();
const router = useRouter();

const formRef = ref<VForm | null>(null);
const valid = ref<boolean>(false);
const loading = ref<boolean>(false);
const succeeded = ref<boolean>(false);
const showPass = ref<boolean>(false);
const token = ref<string>('');

const form = ref<MemberActivationRequest>({
  token: '',
  username: '',
  password: ''
});

// Same validations as UserEdit
const usernameRules = [
  (v: string) => !!v || 'Username is required',
  (v: string) => /^[a-zA-Z0-9]+$/.test(v) || 'Username must only contain alphanumeric characters',
];
const passwordRules = [
  (v: string) => !!v || 'Password is required',
  (v: string) => v.length >= 8 || 'Password must be at least 8 characters',
  (v: string) => /(?=.*[a-z])/.test(v) || 'Password must contain at least one lowercase letter',
  (v: string) => /(?=.*[A-Z])/.test(v) || 'Password must contain at least one uppercase letter',
  (v: string) => /(?=.*\d)/.test(v) || 'Password must contain at least one number',
  (v: string) => /(?=.*[@$!%*?&])/.test(v) || 'Password must contain at least one special character (@$!%*?&)',
];

const passwordAgain = ref<string>('');
const passwordConfirmRules = [
  (v: string) => !!v || 'Password confirmation is required',
  (v: string) => v === form.value.password || 'Passwords do not match',
];

onMounted(() => {
  token.value = (route.query.token as string) || '';
  form.value.username = (route.query.username as string) || '';
  if (!token.value) {
    router.push('/');
    return;
  }
  form.value.token = token.value;
});

const submit = async () => {
  loading.value = true;
  try {
    const result = await formRef.value?.validate();
    if (!result || (typeof result === 'object' && 'valid' in result && !result.valid)) return;

    await memberActivate({body: form.value});
    succeeded.value = true;
  } finally {
    loading.value = false;
  }
};
</script>
