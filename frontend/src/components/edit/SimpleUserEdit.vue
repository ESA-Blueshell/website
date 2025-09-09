<template>
  <div>
    <v-form
      ref="form"
      v-model="valid"
    >
      <v-row>
        <v-col cols="4">
          <v-text-field
            v-model="userData.initials"
            :rules="initialsRules"
            label="Initials"
          />
        </v-col>
        <v-col cols="8">
          <v-text-field
            v-model="userData.firstName"
            :rules="firstNameRules"
            label="First Name"
          />
        </v-col>
      </v-row>

      <v-row>
        <v-col cols="4">
          <v-text-field
            v-model="userData.prefix"
            label="SurPrefix"
          />
        </v-col>
        <v-col cols="8">
          <v-text-field
            v-model="userData.lastName"
            :rules="lastNameRules"
            label="Surname"
          />
        </v-col>
      </v-row>

      <v-row>

        <v-col
          cols="6"
        >
          <v-text-field
            v-model="userData.username"
            :rules="usernameRules"
            label="Username"
          />
        </v-col>

        <v-col cols="6">
          <v-text-field
            v-model="userData.discord"
            label="Discord"
            :rules="discordRules"
          />
        </v-col>
      </v-row>

      <v-row>
        <v-col cols="12">
          <v-text-field
            v-model="userData.email"
            :rules="emailRules"
            label="E-mail"
          />
        </v-col>
      </v-row>

      <v-row>
        <v-col cols="6">
          <v-text-field
            v-model="userData.password"
            :rules="passwordRules"
            label="Password"
            :append-inner-icon="showPass ? 'mdi-eye' : 'mdi-eye-off'"
            :type="showPass ? 'text' : 'password'"
            @click:append-inner="showPass = !showPass"
          />
        </v-col>
        <v-col cols="6">
          <v-text-field
            v-model="passwordAgain"
            :rules="passwordConfirmRules"
            label="Password (repeated)"
            :append-inner-icon="showPass ? 'mdi-eye' : 'mdi-eye-off'"
            :type="showPass ? 'text' : 'password'"
            @click:append-inner="showPass = !showPass"
          />
        </v-col>
      </v-row>

      <!-- Checkboxes -->
      <v-row
        justify="space-evenly"
        align="center"
      >
        <v-col cols="auto">
          <v-checkbox
            v-model="userData.newsletter"
            :hide-details="true"
            label="Subscribe to newsletter"
          />
        </v-col>
      </v-row>
    </v-form>
  </div>
</template>

<script setup lang="ts">
import {onMounted, ref, type Ref, watch} from 'vue';
import {DateTime} from 'luxon';
import type {AdvancedUserDto} from '@/lib';
import {createUser, updateUser} from '@/lib';
import client from '@/plugins/client.ts';
import type {VForm} from 'vuetify/components';

interface Props {
  editing?: boolean;
  modelValue: AdvancedUserDto;
}

interface Emits {
  (e: 'update:modelValue', user: AdvancedUserDto): void;

  (e: 'user-changed', user: AdvancedUserDto): void;
}

const props = withDefaults(defineProps<Props>(), {
  editing: false,
});

const emit = defineEmits<Emits>();

// Reactive state
const userData: Ref<AdvancedUserDto> = ref({...props.modelValue});
const valid: Ref<boolean> = ref(true);
const submitting: Ref<boolean> = ref(false);
const form: Ref<VForm | undefined> = ref();

// Password fields (only for user creation)
const passwordAgain: Ref<string> = ref('');
const showPass: Ref<boolean> = ref(false);

// Watch for prop changes
watch(
  () => props.modelValue,
  (newVal) => {
    if (JSON.stringify(userData.value) !== JSON.stringify(newVal)) {
      userData.value = {...newVal};
    }
  },
  {deep: true, immediate: true}
);


// Watch for local changes and emit
watch(
  userData,
  (newVal) => {
    emit('update:modelValue', newVal);
  },
  {deep: true}
);

// Validation rules
const usernameRules = [
  (v: string) => !!v || 'Username is required',
  (v: string) => /^[a-zA-Z0-9]+$/.test(v) || 'Username must only contain alphanumeric characters',
];

// Enhanced password validation rules
const passwordRules = [
  (v: string) => !!v || 'Password is required',
  (v: string) => v.length >= 8 || 'Password must be at least 8 characters',
  (v: string) => /(?=.*[a-z])/.test(v) || 'Password must contain at least one lowercase letter',
  (v: string) => /(?=.*[A-Z])/.test(v) || 'Password must contain at least one uppercase letter',
  (v: string) => /(?=.*\d)/.test(v) || 'Password must contain at least one number',
  (v: string) => /(?=.*[@$!%*?&])/.test(v) || 'Password must contain at least one special character (@$!%*?&)',
];

const passwordConfirmRules = [
  (v: string) => !!v || 'Password confirmation is required',
  (v: string) => v === userData.value.password || 'Passwords do not match',
];

const initialsRules = [(v: string) => !!v || 'Initials are required'];
const firstNameRules = [(v: string) => !!v || 'First name is required'];
const lastNameRules = [(v: string) => !!v || 'Surname is required'];
const discordRules = [(v: string) => !!v || 'Discord Username is required'];

const emailRules = [
  (v: string | undefined) => !!v || 'Email is required',
  (v: string | undefined) => /^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/.test(v || '') || 'Enter a valid e-mail address',
  (v: string | undefined) => !/student/i.test(v || '') || 'You may not use your student email to sign up',
];

const validateForm = async (): Promise<boolean> => {
  if (!form.value) return false;
  const result = await form.value.validate();
  return result.valid;
};

const save = async (): Promise<void> => {
  const isValid = await validateForm();
  if (!isValid) return;

  submitting.value = true;

  try {
    let response;
    if (userData.value?.id) {
      // Update existing user
      response = await updateUser({
        path: {userId: userData.value.id},
        body: userData.value,
        client
      });
    } else {
      // Create new user
      response = await createUser({
        body: userData.value,
        client
      });
    }

    if (response.data) {
      userData.value = response.data;
      emit('user-changed', userData.value);
      emit('update:modelValue', userData.value);
    }
  } catch (error: unknown) {
    console.error('Failed to save user:', error);
    throw error;
  } finally {
    submitting.value = false;
  }
};

// Lifecycle hooks
onMounted(() => {
  if (userData.value.dateOfBirth) {
    userData.value.dateOfBirth = DateTime.fromISO(userData.value.dateOfBirth).toISODate() as string;
  }
});

// Expose methods
defineExpose({
  validateForm,
  save
});
</script>

<style scoped>
.v-col:first-child {
  padding-left: 0;
}

.v-col:last-child {
  padding-right: 0;
}

.v-col {
  padding-bottom: 0;
  padding-top: 0;
}
</style>
