<template>
  <div>
    <v-form
      ref="form"
      v-model="valid"
    >
      <v-row v-if="userData?.id && !creating">
        <v-text-field
          v-model="userData.username"
          disabled
          label="Username"
        />
      </v-row>
      <v-row v-else-if="creating">
        <v-text-field
          v-model="userData.username"
          :rules="usernameRules"
          label="Username"
        />
      </v-row>

      <!-- Password fields for new users only -->
      <v-row v-if="creating">
        <v-col cols="6">
          <v-text-field
            v-model="password"
            :rules="passwordRules"
            label="Password"
            :append-icon="showPass ? 'mdi-eye' : 'mdi-eye-off'"
            :type="showPass ? 'text' : 'password'"
            @click:append="showPass = !showPass"
          />
        </v-col>
        <v-col cols="6">
          <v-text-field
            v-model="passwordAgain"
            :rules="passwordConfirmRules"
            label="Password (repeated)"
            :append-icon="showPass ? 'mdi-eye' : 'mdi-eye-off'"
            :type="showPass ? 'text' : 'password'"
            @click:append="showPass = !showPass"
          />
        </v-col>
      </v-row>

      <v-row>
        <v-col cols="6">
          <v-text-field
            v-model="userData.email"
            :disabled="disableEdit && !creating"
            :rules="emailRules"
            label="E-mail"
          />
        </v-col>
        <v-col cols="6">
          <v-phone-input
            ref="phoneInput"
            v-model="userData.phoneNumber"
            label="Phone Number"
            mode="international"
            :rules="phoneNumberRules"
            :default-country="'NL'"
            placeholder="Phone Number"
            :disabled="disableEdit && !creating"
            @update:country="updateCountry"
          />
        </v-col>
      </v-row>

      <v-row>
        <v-col cols="2">
          <v-text-field
            v-model="userData.initials"
            :disabled="disableEdit && !creating"
            :rules="initialsRules"
            label="Initials"
          />
        </v-col>
        <v-col cols="4">
          <v-text-field
            v-model="userData.firstName"
            :disabled="disableEdit && !creating"
            :rules="firstNameRules"
            label="First Name"
          />
        </v-col>
        <v-col cols="2">
          <v-text-field
            v-model="userData.prefix"
            :disabled="disableEdit && !creating"
            label="Surname Prefix"
          />
        </v-col>
        <v-col cols="4">
          <v-text-field
            v-model="userData.lastName"
            :disabled="disableEdit && !creating"
            :rules="lastNameRules"
            label="Surname"
          />
        </v-col>
      </v-row>

      <v-row>
        <v-col cols="2">
          <v-text-field
            v-model="userData.studentNumber"
            label="Student Number"
            :disabled="disableEdit && !creating"
          />
        </v-col>
        <v-col cols="4">
          <v-text-field
            v-model="userData.dateOfBirth"
            label="Date of Birth"
            type="date"
            :disabled="disableEdit && !creating"
            :rules="dateOfBirthRules"
          />
        </v-col>
        <v-col cols="6">
          <v-text-field
            v-model="userData.discord"
            label="Discord Username"
            :rules="discordRules"
            :disabled="disableEdit && !creating"
          />
        </v-col>
      </v-row>

      <v-row>
        <v-col cols="6">
          <v-text-field
            v-model="userData.gender"
            label="Gender"
            :disabled="disableEdit && !creating"
          />
        </v-col>
        <v-col cols="6">
          <country-select
            v-model="userData.nationality"
            label="Nationality"
            :disabled="disableEdit && !creating"
          />
        </v-col>
      </v-row>

      <!-- Checkboxes -->
      <v-row justify="space-evenly" align="center">
        <v-col cols="auto">
          <v-checkbox
            v-model="userData.newsletter"
            :hide-details="true"
            label="Subscribe to newsletter"
            :disabled="disableEdit && !creating"
          />
        </v-col>
        <v-col cols="auto">
          <v-checkbox
            v-model="userData.ehbo"
            :hide-details="true"
            label="EHBO Diploma"
            :disabled="disableEdit && !creating"
          />
        </v-col>
        <v-col cols="auto">
          <v-checkbox
            v-model="userData.bhv"
            :hide-details="true"
            label="BHV Diploma"
            :disabled="disableEdit && !creating"
          />
        </v-col>
      </v-row>

      <v-row justify="space-evenly" align="center" class="mb-3">
        <v-col cols="auto">
          <v-checkbox
            v-model="userData.photoConsent"
            :hide-details="true"
            label="Give consent for your photo to be taken at events"
            :disabled="disableEdit && !creating"
          />
        </v-col>
        <v-col cols="auto">
          <v-checkbox
            v-model="userData.incasso"
            :hide-details="true"
            label="Pays through incasso"
            :disabled="disableEdit && !creating"
          />
        </v-col>
        <v-col cols="auto" v-if="!creating">
          <v-tooltip
            location="top"
            text="Save changes"
          >
            <template #activator="{ props }">
              <v-btn
                x-small
                icon="mdi-content-save"
                :disabled="disableEdit"
                :loading="submitting"
                v-bind="props"
                @click="save"
              />
            </template>
          </v-tooltip>
        </v-col>
      </v-row>
    </v-form>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch, type Ref } from 'vue';
import { VPhoneInput } from 'v-phone-input';
import { DateTime } from 'luxon';
import store from '@/plugins/store.ts';
import { createUser, updateUser } from '@/lib';
import type { AdvancedUserDto } from '@/lib';
import client from '@/plugins/client.ts';
import type { VForm } from 'vuetify/components';
import { type CountryCode, parsePhoneNumber, type PhoneNumber } from 'libphonenumber-js/max';
import CountrySelect from '@/components/select/CountrySelect.vue';

interface Props {
  editing?: boolean;
  creating?: boolean;
  modelValue: AdvancedUserDto;
}

interface Emits {
  (e: 'update:modelValue', user: AdvancedUserDto): void;
  (e: 'user-changed', user: AdvancedUserDto): void;
}

const props = withDefaults(defineProps<Props>(), {
  editing: false,
  creating: false,
});

const emit = defineEmits<Emits>();

// Computed properties
const roles = computed(() => store.getters.getLogin?.roles);
const disableEdit = computed(() => !props.creating && !props.editing && (!roles.value || !(roles.value.includes('BOARD') || roles.value.includes('ADMIN'))));

// Reactive state
const userData: Ref<AdvancedUserDto> = ref({ ...props.modelValue });
const country: Ref<CountryCode> = ref('NL');
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
    userData.value = { ...newVal };
  },
  { deep: true, immediate: true }
);

// Watch for local changes and emit
watch(
  userData,
  (newVal) => {
    emit('update:modelValue', newVal);
  },
  { deep: true }
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
const dateOfBirthRules = [(v: string) => !!v || 'Date of birth is required'];
const discordRules = [(v: string) => !!v || 'Discord Username is required'];

const emailRules = [
  (v: string | undefined) => !!v || 'Email is required',
  (v: string | undefined) => /^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/.test(v || '') || 'Enter a valid e-mail address',
  (v: string | undefined) => !/student/i.test(v || '') || 'You may not use your student email to sign up',
];

const phoneNumberRules = [
  (v: string) => {
    if (!v) return 'Phone number is required';
    try {
      const phoneNumber: PhoneNumber = parsePhoneNumber(v, country.value);
      if (!phoneNumber.isValid()) {
        return 'Enter a valid phone number';
      }
      return phoneNumber.getType() === 'MOBILE' || 'Enter a mobile phone number';
    } catch {
      return 'Enter a valid phone number';
    }
  },
];

// Methods
const updateCountry = (newCountry: string): void => {
  country.value = newCountry as CountryCode;
};

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
        path: { userId: userData.value.id },
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
