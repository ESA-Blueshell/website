<script lang="ts" setup>
import {computed} from "vue"
import FormField from "@/components/island/FormField.vue"
import TextInput from "@/components/island/TextInput.vue"
import {isHexColour} from "./colour"

/**
 * A colour, picked from the swatch or written as a hex, drawn as the island's other fields are.
 * Handed to `VvField` as its component, so it takes what a form control takes.
 */
defineOptions({name: "ColourControl", inheritAttrs: false})

const {
  label = "",
  errorMessages = [],
  hint = "",
  disabled = false,
  placeholder = "#1f6feb",
  testid = undefined,
} = defineProps<{
  label?: string
  errorMessages?: string | string[]
  hint?: string
  disabled?: boolean
  placeholder?: string
  testid?: string
}>()

const model = defineModel<string | null>({default: ""})
const emit = defineEmits<{blur: []}>()

const text = computed<string>({
  get: () => model.value ?? "",
  set: value => { model.value = value.trim() === "" ? "" : value.trim().replace(/^([0-9a-f]{6})$/i, "#$1") },
})
const valid = computed<boolean>(() => text.value === "" || isHexColour(text.value))
const swatch = computed<string>(() => (isHexColour(text.value) ? text.value : placeholder))
const error = computed<string>(() => {
  const given = Array.isArray(errorMessages) ? errorMessages[0] : errorMessages
  return given || (valid.value ? "" : "Write a colour as # and six hex digits.")
})
</script>

<template>
  <form-field
    :error="error"
    :filled="text !== ''"
    :hint="hint"
    :label="label"
    :style="{'--field-label-left': 'calc(2.6rem + 0.9rem)'}"
    :testid="testid"
    variant="inside"
  >
    <template #default="{controlId, describedBy, invalid}">
      <span class="colour-control">
        <label
          class="colour-control__swatch"
          :style="{'--swatch': swatch}"
        >
          <input
            :aria-label="`Pick ${label.toLowerCase() || 'a colour'}`"
            class="colour-control__native"
            :data-testid="testid ? `${testid}-swatch` : undefined"
            :disabled="disabled"
            type="color"
            :value="swatch"
            @input="text = ($event.target as HTMLInputElement).value"
          >
        </label>
        <text-input
          v-model="text"
          class="colour-control__hex"
          :control-id="controlId"
          :data-testid="testid ? `${testid}-hex` : undefined"
          :described-by="describedBy"
          :disabled="disabled"
          :invalid="invalid || !valid"
          maxlength="7"
          :placeholder="placeholder"
          spellcheck="false"
          @blur="emit('blur')"
        />
      </span>
    </template>
  </form-field>
</template>

<style scoped>
.colour-control {
  display: flex;
  align-items: stretch;
  gap: 0;
}

/* The swatch is the colour itself, in the field's own square, so it reads as the value. */
.colour-control__swatch {
  position: relative;
  flex: 0 0 auto;
  width: 2.6rem;
  background-color: var(--swatch);
  border-bottom: 1px solid var(--color-ok);
  cursor: pointer;
}

.colour-control__swatch:focus-within {
  outline: 2px solid var(--color-brand);
  outline-offset: -2px;
}

.colour-control__native {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  opacity: 0;
  cursor: pointer;
}

.colour-control__hex {
  flex: 1 1 auto;
  min-width: 0;
  font-family: var(--font-mono, ui-monospace, monospace);
}
</style>
