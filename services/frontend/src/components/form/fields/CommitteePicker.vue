<script setup lang="ts">
/** One of the committees the form was handed, found by typing its name. */
import {computed} from "vue"
import {useFieldName} from "@/components/form/fields/fieldName"
import {firstSaid} from "@/components/form/fields/saidWrong"
import FormField from "@/components/island/FormField.vue"
import SearchPicker from "@/components/island/SearchPicker.vue"

const {
  modelValue = undefined,
  committees,
  label = "Committee",
  required = false,
  disabled = false,
  errorMessages = undefined,
  testid = undefined,
} = defineProps<{
  modelValue?: number | null | undefined
  /** The committees the reader may choose from, which the form reads for them. */
  committees: Array<{id: number, name: string}>
  label?: string
  required?: boolean
  disabled?: boolean
  errorMessages?: string | string[]
  testid?: string
}>()

const emit = defineEmits<{"update:modelValue": [value: number | undefined]}>()

const said = computed<string>(() => firstSaid(errorMessages))
const named = useFieldName(testid)

const options = computed(() => committees.map(one => ({key: String(one.id), label: one.name})))
</script>

<template>
  <form-field
    :error="said"
    :filled="modelValue != null"
    :label="label"
    :required="required"
    :testid="named"
    variant="inside"
  >
    <template #default="{controlId, labelId}">
      <search-picker
        :control-id="controlId"
        :disabled="disabled || committees.length === 0"
        empty-note="You are not in a committee that can hold events."
        :labelled-by="labelId"
        :options="options"
        :selected-key="modelValue == null ? null : String(modelValue)"
        :testid-prefix="named ?? 'committee-picker'"
        @pick="emit('update:modelValue', Number($event))"
      />
    </template>
  </form-field>
</template>
