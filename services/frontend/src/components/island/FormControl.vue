<script lang="ts">
/* What `VvField` hands a control, answered on the island side: a form names a kind. */
export type ControlKind = "text" | "email" | "tel" | "url" | "number" | "password" | "date"
  | "textarea" | "markdown" | "phone" | "country" | "nationality"
</script>

<script lang="ts" setup>
import {computed, useAttrs} from "vue"
import type {CountryCode} from "libphonenumber-js"
import {firstSaid} from "@/components/form/fields/saidWrong"
import CountryPicker from "@/components/island/CountryPicker.vue"
import FormField from "@/components/island/FormField.vue"
import TextInput from "@/components/island/TextInput.vue"
import PhoneInput from "@/components/island/PhoneInput.vue"
import MarkdownEditor from "@/components/island/MarkdownEditor.vue"
import TextArea from "@/components/island/TextArea.vue"

defineOptions({name: "FormControl", inheritAttrs: false})

const {
  kind = "text",
  label = "",
  errorMessages = [],
  hint = "",
  disabled = false,
  placeholder = "",
  defaultCountry = "NL",
  testid = undefined,
} = defineProps<{
  kind?: ControlKind
  label?: string
  /** What vee-validate found wrong, in the shape Vuetify's controls are handed it. */
  errorMessages?: string | string[]
  hint?: string
  disabled?: boolean
  placeholder?: string
  defaultCountry?: CountryCode
  testid?: string
}>()

const model = defineModel<string | null>({default: ""})

const emit = defineEmits<{blur: []; "update:country": [code: CountryCode]}>()

/* On the field rather than the input: the Vuetify control put it on its root, and the specs
   read the input under that name. */
const attrs = useAttrs()
const named = computed<string | undefined>(() =>
  (attrs["data-testid"] as string | undefined) ?? testid)
const rest = computed(() => {
  const {"data-testid": _named, ...others} = attrs
  return others
})

const error = computed<string>(() => firstSaid(errorMessages))

/* A label ending in the star the old forms typed into it says the same thing the field's own
   mark does, so the star is read off it rather than printed twice. */
const required = computed<boolean>(() => label.trimEnd().endsWith("*"))
const said = computed<string>(() => label.trimEnd().replace(/\*$/, "").trimEnd())

const filled = computed<boolean>(() => (model.value ?? "") !== "")


const text = computed<string>({
  get: () => model.value ?? "",
  set: value => {
    model.value = value
  },
})

const picked = computed<string | null>({
  get: () => (model.value === "" ? null : model.value ?? null),
  set: value => {
    model.value = value
  },
})

/* A flag cell is --field-lead wide and the text a further 1rem in, so the labels line up. */
const carriesFlag = computed<boolean>(() =>
  kind === "phone" || kind === "country" || kind === "nationality")
const inset = computed(() =>
  (carriesFlag.value ? {"--field-label-left": "calc(var(--field-lead, 4.35rem) + 1rem)"} : undefined))
</script>

<template>
  <form-field
    :error="error"
    :filled="filled"
    :hint="hint"
    :label="said"
    :required="required"
    :style="inset"
    :testid="named"
    variant="inside"
  >
    <template #default="{controlId, describedBy, invalid, labelId}">
      <phone-input
        v-if="kind === 'phone'"
        v-model="text"
        :control-id="controlId"
        :default-country="defaultCountry"
        :described-by="describedBy"
        :disabled="disabled"
        :invalid="invalid"
        :testid-prefix="named ? `${named}-phone` : 'phone'"
        @focusout="emit('blur')"
        @update:country="emit('update:country', $event)"
      />

      <country-picker
        v-else-if="kind === 'country' || kind === 'nationality'"
        v-model="picked"
        :disabled="disabled"
        :reading="kind === 'nationality' ? 'nationality' : 'country'"
        :testid-prefix="named ? `${named}-pick` : 'pick'"
      />

      <markdown-editor
        v-else-if="kind === 'markdown'"
        v-model="text"
        :disabled="disabled"
        :labelled-by="labelId"
        :placeholder="placeholder"
        :testid="named ? `${named}-editor` : undefined"
      />

      <text-area
        v-else-if="kind === 'textarea'"
        v-model="text"
        :control-id="controlId"
        :described-by="describedBy"
        :disabled="disabled"
        :invalid="invalid"
        :placeholder="placeholder"
        @blur="emit('blur')"
      />

      <text-input
        v-else
        v-model="text"
        :control-id="controlId"
        :described-by="describedBy"
        :disabled="disabled"
        :invalid="invalid"
        :placeholder="placeholder"
        :testid="undefined"
        :type="kind"
        v-bind="rest"
        @blur="emit('blur')"
      />
    </template>
  </form-field>
</template>
