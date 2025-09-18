<template>
  <div>
    <v-form
      ref="form"
      v-model="valid"
    >
      <!-- Reuse SimpleUserEdit -->
      <SimpleUserEdit
        ref="simpleRef"
        :model-value="simpleModel"
        :show-passwords="creating"
        @update:model-value="(val) => simpleModel = val"
      />

      <v-row class="mt-10">
        <v-col cols="12">
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
        <v-col cols="6">
          <v-text-field
            v-model="userData.studentNumber"
            label="Student Number"
            :disabled="disableEdit && !creating"
          />
        </v-col>
        <v-col cols="6">
          <v-text-field
            v-model="userData.dateOfBirth"
            label="Date of Birth"
            type="date"
            :disabled="disableEdit && !creating"
            :rules="dateOfBirthRules"
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
      <v-row
        justify="space-evenly"
        align="center"
      >
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

      <v-row
        justify="space-evenly"
        align="center"
        class="mb-3"
      >
        <v-col cols="auto">
          <v-checkbox
            v-model="userData.photoConsent"
            :hide-details="true"
            label="Give consent for your photo to be taken at events"
            :disabled="disableEdit && !creating"
          />
        </v-col>
        <v-col
          v-if="!creating"
          cols="auto"
        >
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
import {computed, ref, type Ref, watch} from 'vue';
import {VPhoneInput} from 'v-phone-input';
import store from '@/plugins/store.ts';
import {type AdvancedUserDto, createMember, createUser, type SimpleUserDto, updateUser} from '@/lib';
import type {VForm} from 'vuetify/components';
import {type CountryCode, parsePhoneNumber, type PhoneNumber} from 'libphonenumber-js/max';
import CountrySelect from '@/components/select/CountrySelect.vue';
import SimpleUserEdit from '@/components/user/SimpleUserEdit.vue';

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
const userData: Ref<AdvancedUserDto> = ref({...props.modelValue});
const country: Ref<CountryCode> = ref('NL');
const valid: Ref<boolean> = ref(true);
const submitting: Ref<boolean> = ref(false);
const form: Ref<VForm | undefined> = ref();
const simpleRef = ref<InstanceType<typeof SimpleUserEdit> | null>(null);

// Bridge SimpleUserEdit v-model into AdvancedUserEdit v-model
let simpleModel = computed<SimpleUserDto>({
  get: () => ({
    initials: userData.value.initials,
    firstName: userData.value.firstName,
    prefix: userData.value.prefix,
    lastName: userData.value.lastName,
    username: userData.value.username,
    discord: userData.value.discord,
    email: userData.value.email,
    password: userData.value.password,
    newsletter: userData.value.newsletter,
  } as SimpleUserDto),
  set: (val: SimpleUserDto) => {
    userData.value = {
      ...userData.value,
      ...val,
    };
    emit('update:modelValue', userData.value);
  }
});

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

// Validation rules (only ones needed for fields not covered by SimpleUserEdit)
const dateOfBirthRules = [(v: string) => !!v || 'Date of birth is required'];

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
  // Validate child (SimpleUserEdit) and this form
  const childValid = (await simpleRef.value?.validateForm?.()) ?? true;
  if (!form.value) return false;
  const selfResult = await form.value.validate();
  return childValid && selfResult.valid;
};

const save = async (): Promise<void> => {
  const isValid = await validateForm();
  if (!isValid) return;

  submitting.value = true;

  try {
    let response;
    if (userData.value?.id) {
      response = await updateUser({
        path: {userId: userData.value.id},
        body: userData.value
      });
    } else {
      if (roles.value && roles.value.includes('BOARD')) {
        response = await createMember({
          body: userData.value
        });
      } else {
        response = await createUser({
          body: userData.value
        });
      }
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

// Expose methods
defineExpose({
  validateForm,
  save
});
</script>

<style lang="scss">
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
