<script lang="ts">
/* What `VvField` hands a control, answered on the island side: a form names a kind. */
export type ControlKind = "text" | "email" | "tel" | "url" | "number" | "password" | "date"
  | "time" | "datetime" | "count" | "money" | "textarea" | "markdown" | "phone" | "country"
  | "nationality"
</script>

<script lang="ts" setup>
import {computed, useAttrs} from "vue"
import type {CountryCode} from "libphonenumber-js"
import {firstSaid} from "@/components/form/fields/saidWrong"
import CountInput from "@/components/island/CountInput.vue"
import CountryPicker from "@/components/island/CountryPicker.vue"
import DateInput from "@/components/island/DateInput.vue"
import DateTimeInput from "@/components/island/DateTimeInput.vue"
import MoneyInput from "@/components/island/MoneyInput.vue"
import TimeInput from "@/components/island/TimeInput.vue"
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

/* The island draws its own calendar, so the browser is not asked for one: the type it was
   handed decides the control rather than being passed on to an input. */
const typedAs = computed(() => String(attrs.type ?? ""))
const asDate = computed<boolean>(() => kind === "date" || typedAs.value === "date")
const asTime = computed<boolean>(() => kind === "time" || typedAs.value === "time")
const asMoment = computed<boolean>(() => kind === "datetime" || typedAs.value === "datetime-local")
const earliest = computed<string | undefined>(() => attrs.min as string | undefined)
const latest = computed<string | undefined>(() => attrs.max as string | undefined)
const restOfDate = computed(() => {
  const {type: _type, min: _min, max: _max, ...others} = rest.value
  return others
})

const error = computed<string>(() => firstSaid(errorMessages))

/* A label ending in the star the old forms typed into it says the same thing the field's own
   mark does, so the star is read off it rather than printed twice. */
const required = computed<boolean>(() => label.trimEnd().endsWith("*"))
const said = computed<string>(() => label.trimEnd().replace(/\*$/, "").trimEnd())

/* A date or time input draws its own dd / mm / yyyy whether or not it holds one, so the label
   rises at once rather than sitting on top of it. */
const SELF_DRAWN = new Set(["date", "datetime-local", "month", "time", "week"])
const DRAWS_ITS_OWN = new Set<ControlKind>(["date", "time", "datetime", "count", "money"])
const drawsItsOwn = computed<boolean>(() => DRAWS_ITS_OWN.has(kind) || SELF_DRAWN.has(typedAs.value))
const filled = computed<boolean>(() => drawsItsOwn.value || (model.value ?? "") !== "")


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
  kind === "phone" || kind === "country" || kind === "nationality" || kind === "money")
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
        :control-id="controlId"
        :labelled-by="labelId"
        :disabled="disabled"
        :reading="kind === 'nationality' ? 'nationality' : 'country'"
        :testid-prefix="named ? `${named}-pick` : 'pick'"
      />

      <date-time-input
        v-else-if="asMoment"
        v-model="text"
        :control-id="controlId"
        :described-by="describedBy"
        :disabled="disabled"
        :invalid="invalid"
        :min="earliest"
        :testid="named ? `${named}-when` : undefined"
        v-bind="restOfDate"
      />

      <count-input
        v-else-if="kind === 'count'"
        v-model="text"
        :control-id="controlId"
        :described-by="describedBy"
        :disabled="disabled"
        :invalid="invalid"
        :testid="named ? `${named}-count` : undefined"
        v-bind="rest"
        @blur="emit('blur')"
      />

      <date-input
        v-else-if="asDate"
        v-model="text"
        :control-id="controlId"
        :described-by="describedBy"
        :disabled="disabled"
        :invalid="invalid"
        :max="latest"
        :min="earliest"
        :testid="named ? `${named}-date` : undefined"
        v-bind="restOfDate"
        @blur="emit('blur')"
      />

      <time-input
        v-else-if="asTime"
        v-model="text"
        :control-id="controlId"
        :described-by="describedBy"
        :disabled="disabled"
        :invalid="invalid"
        :testid="named ? `${named}-time` : undefined"
        v-bind="restOfDate"
        @blur="emit('blur')"
      />

      <money-input
        v-else-if="kind === 'money'"
        v-model="text"
        :control-id="controlId"
        :described-by="describedBy"
        :disabled="disabled"
        :invalid="invalid"
        :testid="named ? `${named}-money` : undefined"
        v-bind="rest"
        @blur="emit('blur')"
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
        v-bind="rest"
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
