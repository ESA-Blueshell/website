<template>
  <v-autocomplete
    v-model="selectedCountry"
    :items="countryOptions"
    label="Select a Country"
    dense
    outlined
    clearable
    :counter="countries.length"
  >
    <!-- Customize each option in the dropdown -->
    <template #item="{ item }">
      <v-list-item
        :value="item"
      >
        <v-list-item-title>
          {{ item.value.flag }} {{ item.value.name }}
        </v-list-item-title>
      </v-list-item>
    </template>
  </v-autocomplete>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue';
import { countries, getEmojiFlag } from 'countries-list';

// Define props and emit for v-model binding
const props = defineProps<{ modelValue?: string }>();
const emit = defineEmits(['update:modelValue']);

// The selected country code is stored as a string
const selectedCountry = ref<string | null>(props.modelValue || null);

// Convert the countries object into an array of country options
const countryOptions = computed(() => {
  return Object.entries(countries).map(([code, country]) => ({
    code,
    name: country.name,
    flag: getEmojiFlag(code)
  }));
});

// Emit changes to the parent component when the selection changes
watch(selectedCountry, (newVal) => {
  emit('update:modelValue', newVal);
});

// Update local selection if the prop value changes externally
watch(
  () => props.modelValue,
  (newVal) => {
    if (newVal !== selectedCountry.value) {
      selectedCountry.value = newVal;
    }
  }
);
</script>
