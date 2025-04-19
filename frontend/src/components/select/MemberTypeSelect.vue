<template>
  <v-select
    v-model="selected"
    :items="memberTypeOptions"
    label="Member Type"
    :rules="[requiredRule]"
    outlined
    dense
    item-text="text"
    item-value="value"
    item-title="text"
  />
</template>

<script lang="ts">
import {defineComponent, ref, watch} from 'vue';
import {MemberType} from "@/models";

export default defineComponent({
  name: 'MemberTypeSelect',
  props: {
    modelValue: {
      type: String as () => MemberType,
      default: MemberType.REGULAR,
    },
  },
  emits: ['update:modelValue'],
  setup(props, {emit}) {
    // Initialize the selected value with the prop
    const selected = ref<MemberType>(props.modelValue);

    // Preprocess MemberType.ts enum to create display text
    const memberTypeOptions = Object.values(MemberType).map((type) => ({
      text: `${type.charAt(0)}${type.slice(1).toLowerCase()}`, // Capitalize first letter
      value: type,
    }));

    // Validation rule to ensure a membership type is selected
    const requiredRule = (value: MemberType) => !!value || 'Member type is required';

    // Watch for changes in the selected value and emit updates
    watch(selected, (newVal) => {
      emit('update:modelValue', newVal);
    });

    // Watch for external changes to modelValue and update selected accordingly
    watch(
      () => props.modelValue,
      (newVal) => {
        if (newVal !== selected.value) {
          selected.value = newVal;
        }
      }
    );

    return {
      selected,
      memberTypeOptions,
      requiredRule,
    };
  },
});
</script>

<style scoped>
/* Optional: Customize the appearance */
</style>
