
<template>
  <v-sheet
    class="pa-4"
    style="border-radius: 10px"
  >
    <strong>Address Information</strong><br>

    <v-row>
      <v-col cols="8">
        <v-text-field
          ref="street"
          v-model="localAddress.street"
          label="Street"
          :rules="streetRules"
        />
      </v-col>
      <v-col cols="4">
        <v-text-field
          ref="houseNumber"
          v-model="localAddress.houseNumber"
          label="House Number"
          :rules="houseNumberRules"
        />
      </v-col>
    </v-row>

    <v-row>
      <v-col cols="6">
        <v-text-field
          ref="zipCode"
          v-model="localAddress.zipCode"
          label="Zipcode"
          :rules="zipCodeRules"
        />
      </v-col>
      <v-col cols="6">
        <v-text-field
          ref="city"
          v-model="localAddress.city"
          label="City"
          :rules="cityRules"
        />
      </v-col>
    </v-row>

    <v-row>
      <v-col cols="12">
        <country-select v-model="localAddress.country" />
      </v-col>
    </v-row>
  </v-sheet>
</template>

<script setup lang="ts">
import { ref, watch, type Ref } from 'vue';
import type { AddressDto } from '@/lib';
import { createAddress, updateAddress } from '@/lib';
import CountrySelect from "@/components/select/CountrySelect.vue";

interface Props {
  modelValue: AddressDto;
}

interface Emits {
  (e: 'update:modelValue', value: AddressDto): void;
}

const props = defineProps<Props>();
const emit = defineEmits<Emits>();

// Template refs
const street: Ref<HTMLElement | null> = ref(null);
const houseNumber: Ref<HTMLElement | null> = ref(null);
const zipCode: Ref<HTMLElement | null> = ref(null);
const city: Ref<HTMLElement | null> = ref(null);
const country: Ref<HTMLElement | null> = ref(null);

// Reactive data
const localAddress: Ref<AddressDto> = ref({ ...props.modelValue });

// Validation rules
const streetRules: Ref<Array<(v: any) => boolean | string>> = ref([
  (v: any) => !!v || 'Street is required',
  (v: string) => (v && v.length >= 2) || 'Street must be at least 2 characters'
]);

const houseNumberRules: Ref<Array<(v: any) => boolean | string>> = ref([
  (v: any) => !!v || 'House number is required'
]);

const zipCodeRules: Ref<Array<(v: any) => boolean | string>> = ref([
  (v: any) => !!v || 'Zipcode is required'
]);

const cityRules: Ref<Array<(v: any) => boolean | string>> = ref([
  (v: any) => !!v || 'City is required',
  (v: string) => (v && v.length >= 2) || 'City must be at least 2 characters'
]);

const countryRules: Ref<Array<(v: any) => boolean | string>> = ref([
  (v: any) => !!v || 'Country is required',
  (v: string) => (v && v.length >= 2) || 'Country must be at least 2 characters'
]);

// Watch for prop changes
watch(
  () => props.modelValue,
  (newVal: AddressDto) => {
    localAddress.value = { ...newVal };
  },
  { deep: true, immediate: true }
);

// Watch for local changes and emit
watch(
  localAddress,
  (newVal: AddressDto) => {
    emit('update:modelValue', newVal);
  },
  { deep: true }
);

// Methods
const saveAddress = async (): Promise<void> => {
  try {
    let response: { data?: AddressDto };

    if (localAddress.value.id) {
      // Update existing address
      response = await updateAddress({
        path: { addressId: localAddress.value.id },
        body: localAddress.value,
        client
      });
    } else {
      // Create new address
      response = await createAddress({
        body: localAddress.value,
        client
      });
    }

    if (response.data) {
      localAddress.value = response.data;
      emit('update:modelValue', response.data);
    }
  } catch (error: unknown) {
    console.error('Failed to save address:', error);
    throw error;
  }
};

const validateAddress = (): boolean => {
  const requiredFields = [
    { value: localAddress.value.street, name: 'street' },
    { value: localAddress.value.houseNumber, name: 'house number' },
    { value: localAddress.value.zipCode, name: 'zip code' },
    { value: localAddress.value.city, name: 'city' },
    { value: localAddress.value.country, name: 'country' }
  ];

  for (const field of requiredFields) {
    if (!field.value || field.value.trim().length === 0) {
      console.error(`Validation failed: ${field.name} is required`);
      return false;
    }
  }

  return true;
};

const clearAddress = (): void => {
  localAddress.value = {
    street: '',
    houseNumber: '',
    zipCode: '',
    city: '',
    country: '',
  };
};

// Expose methods that might be called from parent components
defineExpose({
  saveAddress,
  validateAddress,
  clearAddress
});
</script>

<style lang="scss" scoped>
.v-sheet {
  background: white;
}
</style>
