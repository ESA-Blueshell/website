
<template>
  <v-autocomplete
    v-model="selectedCountry"
    :items="countryOptions"
    item-title="displayName"
    item-value="code"
    :label="label || 'Country'"
    clearable
    :custom-filter="customFilter"
  >
  </v-autocomplete>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue';
import { countries, getEmojiFlag } from 'countries-list';

// Define props and emit for v-model binding
const props = defineProps<{ modelValue?: string, label?: string }>();
const emit = defineEmits<{
  'update:modelValue': [value: string | null]
}>();

// The selected country code is stored as a string
const selectedCountry = ref<string | null>(props.modelValue || null);

// Convert the countries object into an array of country options
const countryOptions = computed(() => {
  return Object.entries(countries).map(([code, country]) => ({
    code, // This will be the actual value (2-letter country code)
    name: country.name,
    flag: getEmojiFlag(code),
    displayName: `${getEmojiFlag(code)} ${country.name}` // For search purposes
  }));
});

// Custom filter function to enable searching by country name
const customFilter = (itemText: string, queryText: string, item: any) => {
  const query = queryText.toLowerCase();
  const name = item.raw.name.toLowerCase();
  const code = item.raw.code.toLowerCase();

  return name.includes(query) || code.includes(query);
};

// Emit changes to the parent component when the selection changes
watch(selectedCountry, (newVal) => {
  emit('update:modelValue', newVal);
});

// Update local selection if the prop value changes externally
watch(
  () => props.modelValue,
  (newVal) => {
    if (newVal !== selectedCountry.value) {
      selectedCountry.value = newVal || null;
    }
  }
);
</script>
