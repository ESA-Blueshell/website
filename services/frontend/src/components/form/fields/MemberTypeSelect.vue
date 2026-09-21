<script lang="ts" setup>
/** Which kind of member somebody is, out of the kinds the api knows. */
import {computed} from "vue"
import IslandField from "@/components/island/IslandField.vue"
import IslandPicker from "@/components/island/IslandPicker.vue"
import {MemberType} from "@/domains/user"

const {
  modelValue = MemberType.ALUMNI,
  disabled = false,
  errorMessages = undefined,
  testid = undefined,
} = defineProps<{
  modelValue?: string
  disabled?: boolean
  /** What the api or a rule found wrong, in the shape every other field is handed it. */
  errorMessages?: string | string[]
  testid?: string
}>()

const emit = defineEmits<{(event: "update:modelValue", value: string): void}>()

const said = computed<string>(() => {
  const first = Array.isArray(errorMessages) ? errorMessages[0] : errorMessages
  return first ?? ""
})

const options = computed(() => Object.values(MemberType).map((type: MemberType) => ({
  key: type,
  label: `${type.charAt(0)}${type.slice(1).toLowerCase()}`,
  terms: [type],
})))
</script>

<template>
  <island-field
    :error="said"
    filled
    label="Member type"
    required
    :testid="testid"
    variant="inside"
  >
    <island-picker
      :disabled="disabled"
      :options="options"
      :selected-key="modelValue"
      :testid-prefix="testid ?? 'member-type'"
      @pick="emit('update:modelValue', $event)"
    />
  </island-field>
</template>
