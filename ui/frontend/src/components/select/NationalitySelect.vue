<template>
  <v-autocomplete
    v-model="selectedNationality"
    :items="nationalityOptions"
    label="Select a Nationality"
    :rules="[requiredRule]"
    outlined
    dense
    return-object
    clearable
    :counter="countries.length"
  >
    <template #item="{ item }">
      <v-list-item class="pa-2" :value="item">
        <v-list-item-title>
          {{ item.flag }} {{ item.nationality }}
        </v-list-item-title>
      </v-list-item>
    </template>

    <!-- Slot to customize the display of the selected item -->
    <template #selection="{ item }">
      <v-chip class="mr-2" size="small" variant="tonal">
        {{ item.flag }} {{ item.nationality }}
      </v-chip>
    </template>
  </v-autocomplete>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue';
import { countries, getEmojiFlag, type TCountryCode, languages } from 'countries-list';

// Define the props and emits for v-model binding
const props = defineProps<{ modelValue?: string }>();
const emit = defineEmits(['update:modelValue']);

type NationalityOption = {
  nationality: string;
  code: string;
  flag: string;
};

// Generate the options for the select component, mapping each country's primary language name
const nationalityOptions: NationalityOption[] = Object.entries(countries).map(([code, country]) => ({
  nationality: country.nationality,
  code: code,
  flag: getEmojiFlag(code as TCountryCode),
}));


// Validation rule to ensure a selection is made
const requiredRule = (value: NationalityOption | null) => !!value || 'Nationality is required';

// Set up the selected value as an object (or null)
const selectedNationality = ref<NationalityOption | null>(
  props.modelValue
    ? nationalityOptions.find(option => option.code === props.modelValue) || null
    : null
);

// Watch local selection changes and emit the country code
watch(selectedNationality, (newVal) => {
  emit('update:modelValue', newVal ? newVal.code : '');
});

// Watch for external modelValue changes and update the local selection accordingly
watch(
  () => props.modelValue,
  (newVal) => {
    if (newVal !== (selectedNationality.value ? selectedNationality.value.code : undefined)) {
      selectedNationality.value = newVal
        ? nationalityOptions.find(option => option.code === newVal) || null
        : null;
    }
  }
);
</script>

<style scoped>
/* Your custom styles here */
</style>
