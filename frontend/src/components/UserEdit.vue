<template>
  <div>
    <v-form
      ref="form"
      v-model="valid"
    >
      <v-row style="padding-top: 10px">
        <v-text-field
          v-if="userData?.id"
          v-model="userData.username"
          disabled
          label="Username"
        />
      </v-row>
      <v-row>
        <v-col cols="2">
          <v-text-field
            v-model="userData.initials"
            :disabled="disableEdit"
            :rules="initialsRules"
            label="Initials"
          />
        </v-col>
        <v-col cols="4">
          <v-text-field
            v-model="userData.firstName"
            :disabled="disableEdit"
            :rules="firstNameRules"
            label="First Name"
          />
        </v-col>
        <v-col cols="2">
          <v-text-field
            v-model="userData.prefix"
            :disabled="disableEdit"
            label="Surname Prefix"
          />
        </v-col>
        <v-col cols="4">
          <v-text-field
            v-model="userData.lastName"
            :disabled="disableEdit"
            :rules="lastNameRules"
            label="Surname"
          />
        </v-col>
      </v-row>
      <v-row>
        <v-col cols="6">
          <v-text-field
            v-model="userData.email"
            :disabled="disableEdit"
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
            :disabled="disableEdit"
            @update:country="updateCountry"
          />
        </v-col>
      </v-row>
      <v-row>
        <v-col cols="2">
          <v-text-field
            v-model="userData.postalCode"
            :disabled="disableEdit"
            :rules="postalCodeRules"
            label="Postal Code"
          />
        </v-col>
        <v-col cols="4">
          <v-text-field
            v-model="userData.city"
            :disabled="disableEdit"
            :rules="cityRules"
            label="City"
          />
        </v-col>
        <v-col cols="6">
          <v-text-field
            v-model="userData.address"
            :disabled="disableEdit"
            label="Address"
          />
        </v-col>
      </v-row>
      <v-row>
        <v-col cols="2">
          <v-text-field
            v-model="userData.studentNumber"
            label="Student Number"
            :disabled="disableEdit"
          />
        </v-col>
        <v-col cols="4">
          <v-text-field
            v-model="userData.dateOfBirth"
            label="Date of Birth"
            type="date"
            :disabled="disableEdit"
            :rules="dateOfBirthRules"
          />
        </v-col>
        <v-col cols="6">
          <v-text-field
            v-model="userData.discord"
            label="Discord Username"
            :rules="discordRules"
            :disabled="disableEdit"
          />
        </v-col>
      </v-row>
      <v-row>
        <v-col cols="6">
          <v-text-field
            v-model="userData.gender"
            label="Gender"
          />
        </v-col>
        <v-col cols="6">
          <v-text-field
            v-model="userData.study"
            cols="4"
            label="Study"
          />
        </v-col>
      </v-row>
      <v-row>
        <nationality-select
          v-model="userData.nationality"
          cols="4"
        />
        <country-select v-model="userData.country" />
      </v-row>
      <v-row>
        <v-col cols="6">
          <MemberTypeSelect v-model="userData.memberType" />
        </v-col>
        <v-col cols="6" />
      </v-row>
      <!-- Last Row: Checkboxes and Save Button -->
      <v-row
        justify="space-evenly"
        align="center"
      >
        <v-col cols="auto">
          <v-checkbox
            v-model="userData.newsletter"
            :hide-details="true"
            label="Subscribe to newsletter"
            :disabled="disableEdit"
          />
        </v-col>
        <v-col cols="auto">
          <v-checkbox
            v-model="userData.ehbo"
            :hide-details="true"
            label="EHBO Diploma"
          />
        </v-col>
        <v-col cols="auto">
          <v-checkbox
            v-model="userData.bhv"
            :hide-details="true"
            label="BHV Diploma"
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
          />
        </v-col>
        <v-col cols="auto">
          <v-checkbox
            v-model="userData.incasso"
            :hide-details="true"
            label="Pays through incasso"
          />
        </v-col>
        <v-col cols="auto">
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

<script lang="ts">
import {ref, onMounted} from 'vue';
import {VPhoneInput} from 'v-phone-input';
import {DateTime} from 'luxon';
import store from '@/plugins/store';
import {type AdvancedUserModel} from "@/models";
import UserService from "@/services/UserService";
import type {VForm} from "vuetify/components";
import {type CountryCode, parsePhoneNumber, type PhoneNumber} from 'libphonenumber-js/max';
import MemberTypeSelect from "@/components/select/MemberTypeSelect.vue";
import NationalitySelect from "@/components/select/NationalitySelect.vue";
import CountrySelect from "@/components/select/CountrySelect.vue";

export default {
  name: 'UserEdit',
  components: {CountrySelect, NationalitySelect, MemberTypeSelect, VPhoneInput},
  props: {
    editing: {
      type: Boolean,
      required: false,
      default: false,
    },
    creating: {
      type: Boolean,
      required: false,
      default: false,
    },
    modelValue: {
      type: Object as () => AdvancedUserModel,
      default: null,
    },
    user: {
      type: Object as () => AdvancedUserModel,
      required: false,
      default: () => ({
        type: 'advanced',
        prefix: '',
        initials: '',
        lastName: '',
        email: '',
        address: '',
        city: '',
        postalCode: '',
        phoneNumber: '',
        studentNumber: '',
        dateOfBirth: '',
        bhv: false,
        ehbo: false,
        discord: '',
        newsletter: false,
        photoConsent: false,
      }),
    },
  },
  emits: ['user-changed'],
  setup(props, {emit}) {
    const roles = store.getters.getLogin?.roles;
    const disableEdit = !roles || !(roles.includes('BOARD') || roles.includes('ADMIN'));
    const userService = new UserService();
    const userData = ref(props.user as AdvancedUserModel);
    const country = ref<CountryCode>('NL'); // Default country code

    const valid = ref(true);
    const submitting = ref(false);
    const form = ref<VForm>();

    const initialsRules = [(v: string) => !!v || 'Initials are required'];
    const firstNameRules = [(v: string) => !!v || 'First name is required'];
    const lastNameRules = [(v: string) => !!v || 'Surname is required'];
    const dateOfBirthRules = [(v: string) => !!v || 'Date of birth is required'];
    const discordRules = [(v: string) => !!v || 'Discord Username is required'];
    const postalCodeRules = [(v: string) => !!v || 'Postal code is required'];
    const cityRules = [(v: string) => !!v || 'Place of residence is required'];
    const emailRules = [
      (v: string | undefined) => !!v || 'Email is required',
      (v: string | undefined) => /^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/.test(v || '') || 'Enter a valid e-mail address',
      (v: string | undefined) => !/student/i.test(v || '') || 'You may not use your student email to sign up',
    ];
    const phoneNumberRules = [
      (v: string) => {
        if (!v) return true;
        try {
          const phoneNumber: PhoneNumber = parsePhoneNumber(v, country.value);
          if (!phoneNumber.isValid()) {
            return true
          }
          // The v-phone input has a very nice error message built in for all countries. But it can't recognize
          // mobile phone numbers. Therefore, we only fail on this rule when the numbe ris a valid non-mobile number
          return phoneNumber.getType() === 'MOBILE' || 'Enter a mobile phone number';
        } catch {
          return true
        }
      },
    ];

    const updateCountry = (newCountry: string) => {
      country.value = newCountry as CountryCode;
    };

    onMounted(() => {
      if (userData.value.dateOfBirth) {
        userData.value.dateOfBirth = DateTime.fromISO(userData.value.dateOfBirth).toISODate() as string;
      }
    });

    const save = async () => {
      const result = await form.value?.validate();
      if (!result || !result.valid) return;

      submitting.value = true;

      try {
        if (userData.value?.id) {
          userData.value = await userService.updateUser(userData.value.id, userData.value);
        } else {
          userData.value = await userService.createUser(userData.value);
        }
        submitting.value = false;
        emit('user-changed', userData.value);
        // eslint-disable-next-line @typescript-eslint/no-unused-vars
      } catch (e: unknown) {
        submitting.value = false;
      }
    };

    return {
      disableEdit,
      userData,
      valid,
      submitting,
      form,
      initialsRules,
      firstNameRules,
      lastNameRules,
      dateOfBirthRules,
      discordRules,
      postalCodeRules,
      cityRules,
      emailRules,
      phoneNumberRules,
      save,
      country,
      updateCountry,
    };
  },
};
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
