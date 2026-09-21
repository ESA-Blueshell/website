<script lang="ts" setup>
/** Which kind of member somebody is, out of the kinds the api knows. */
import {computed} from "vue"
import {useFieldName} from "@/components/form/fields/fieldName"
import {firstSaid} from "@/components/form/fields/saidWrong"
import FormField from "@/components/island/FormField.vue"
import SearchPicker from "@/components/island/SearchPicker.vue"
import {MemberType} from "@/domains/user"

const {
  modelValue = MemberType.ALUMNI,
  disabled = false,
  errorMessages = undefined,
  testid = undefined,
} = defineProps<{
  modelValue?: string
  disabled?: boolean
  errorMessages?: string | string[]
  testid?: string
}>()

const named = useFieldName(testid)

const emit = defineEmits<{(event: "update:modelValue", value: string): void}>()

const said = computed<string>(() => firstSaid(errorMessages))

const options = computed(() => Object.values(MemberType).map((type: MemberType) => ({
  key: type,
  label: `${type.charAt(0)}${type.slice(1).toLowerCase()}`,
  terms: [type],
})))
</script>

<template>
  <form-field
    :error="said"
    filled
    label="Member type"
    required
    :testid="named"
    variant="inside"
  >
    <template #default="{controlId, labelId}">
      <search-picker
        :control-id="controlId"
        :labelled-by="labelId"
        :disabled="disabled"
        :options="options"
        :selected-key="modelValue"
        :testid-prefix="named ?? 'member-type'"
        @pick="emit('update:modelValue', $event)"
      />
    </template>
  </form-field>
</template>
