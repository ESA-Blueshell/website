<script lang="ts">
/**
 * What `VvField` hands every control — a label, a value, what is wrong with it, whether it is
 * off — answered on the island side, so a form swaps a control by naming a kind.
 */
export type ControlKind = "text" | "email" | "tel" | "url" | "number" | "password" | "date"
  | "textarea" | "markdown" | "phone" | "country" | "nationality"
</script>

<script lang="ts" setup>
import {computed, useAttrs} from "vue"
import type {CountryCode} from "libphonenumber-js"
import {firstSaid} from "@/components/form/fields/saidWrong"
import IslandCountry from "@/components/island/IslandCountry.vue"
import IslandField from "@/components/island/IslandField.vue"
import IslandInput from "@/components/island/IslandInput.vue"
import IslandPhone from "@/components/island/IslandPhone.vue"
import IslandMarkdown from "@/components/island/IslandMarkdown.vue"
import IslandTextarea from "@/components/island/IslandTextarea.vue"

defineOptions({name: "IslandControl", inheritAttrs: false})

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
  <island-field
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
      <island-phone
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

      <island-country
        v-else-if="kind === 'country' || kind === 'nationality'"
        v-model="picked"
        :disabled="disabled"
        :reading="kind === 'nationality' ? 'nationality' : 'country'"
        :testid-prefix="named ? `${named}-pick` : 'pick'"
      />

      <island-markdown
        v-else-if="kind === 'markdown'"
        v-model="text"
        :disabled="disabled"
        :labelled-by="labelId"
        :placeholder="placeholder"
        :testid="named ? `${named}-editor` : undefined"
      />

      <island-textarea
        v-else-if="kind === 'textarea'"
        v-model="text"
        :control-id="controlId"
        :described-by="describedBy"
        :disabled="disabled"
        :invalid="invalid"
        :placeholder="placeholder"
        @blur="emit('blur')"
      />

      <island-input
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
  </island-field>
</template>
