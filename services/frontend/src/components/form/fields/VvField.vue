<script generic="T" lang="ts" setup>
import {Field} from "vee-validate"
import {VTextField} from "vuetify/components"
import type {DefineComponent} from "vue"
import type {DisplayFn, HandleChange, UpdateFn} from "@/types/VVField.types.ts"

defineOptions({inheritAttrs: false})

type Rules = string | Record<string, unknown> | undefined

withDefaults(
  defineProps<{
    name: string
    label?: string
    rules?: Rules
    testId?: string
    component?: DefineComponent | string
    componentProps?: Record<string, unknown>
    disabled?: boolean
    display?: DisplayFn<T>
    update?: UpdateFn<T>
  }>(),
  {
    label: "",
    rules: "",
    testId: undefined,
    component: () => VTextField as unknown as DefineComponent,
    componentProps: () => ({}),
    disabled: false,
    display: (v: T) => v,
    update: (incoming: T, handleChange: HandleChange<T>) => {
      handleChange(incoming)
    },
  },
)

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
        @update:model-value="(v: T) => update(v, handleChange as (v: T) => void)"
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
