<script generic="T, Shown = T" lang="ts" setup>
import {Field} from "vee-validate"
import FormControl from "@/components/island/FormControl.vue"
import type {Component} from "vue"
import type {DisplayFn, HandleChange, UpdateFn} from "@/types/VVField.types.ts"

defineOptions({inheritAttrs: false})

type Rules = string | Record<string, unknown> | undefined

withDefaults(
  defineProps<{
    name: string
    label?: string
    rules?: Rules
    testId?: string
    component?: Component | string
    componentProps?: Record<string, unknown>
    disabled?: boolean
    display?: DisplayFn<T, Shown>
    update?: UpdateFn<T, Shown>
  }>(),
  {
    label: "",
    rules: "",
    testId: undefined,
    component: () => FormControl,
    componentProps: () => ({}),
    disabled: false,
    // Without a display or an update the control shows and emits the value itself.
    display: (v: T) => v as unknown as Shown,
    update: (incoming: Shown, handleChange: HandleChange<T>) => {
      handleChange(incoming as unknown as T)
    },
  },
)

// A generic model has no value to default to: `default: undefined` is rejected by
// DefineModelDefault<T>, so the rule cannot be satisfied here.
// eslint-disable-next-line vue/require-default-prop
const model = defineModel<T>()
</script>

<template>
  <Field
    v-slot="{ value, errors, handleChange, handleBlur }"
    v-model="model"
    :name="name"
    :rules="disabled ? undefined : rules"
  >
    <div :data-testid="testId">
      <component
        :is="component"
        :disabled="disabled"
        :error-messages="errors"
        :label="$slots.label ? undefined : label"
        :model-value="display(value as T)"
        v-bind="{...componentProps, ...$attrs}"
        @blur="handleBlur"
        @update:model-value="(v: Shown) => update(v, handleChange as (v: T) => void)"
      >
        <template
          v-if="$slots.label"
          #label
        >
          <slot name="label" />
        </template>
      </component>
    </div>
  </Field>
</template>
