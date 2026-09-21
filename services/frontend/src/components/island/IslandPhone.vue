<script lang="ts" setup>
/**
 * One box holding where a number is from and the number itself. What the form keeps is E.164,
 * which is what the api and every message after it want.
 */
import {computed, ref, watch} from "vue"
import {AsYouType, getCountries, getCountryCallingCode, parsePhoneNumberFromString,
  type CountryCode} from "libphonenumber-js"
import CountryFlag from "@/components/island/CountryFlag.vue"
import IslandPicker from "@/components/island/IslandPicker.vue"
import {cca2Map} from "@/composables/countries"

defineOptions({name: "IslandPhone"})

const {
  defaultCountry = "NL",
  testidPrefix,
  placeholder = "",
  invalid = false,
  disabled = false,
  controlId = undefined,
  describedBy = undefined,
} = defineProps<{
  /** Where a number is assumed to be from until the visitor says otherwise. */
  defaultCountry?: CountryCode
  testidPrefix: string
  placeholder?: string
  invalid?: boolean
  disabled?: boolean
  controlId?: string
  describedBy?: string
}>()

/** The whole number, international and punctuation-free: +31612345678. */
const number = defineModel<string>({default: ""})

/* Which country the number is being read as, which is what the form's own rule checks against. */
const emit = defineEmits<{"update:country": [code: CountryCode]}>()

const from = ref<CountryCode>(defaultCountry)
watch(from, code => emit("update:country", code), {immediate: true})
/** What is being typed, which is the national part alone: the dial code is the picker's. */
const typed = ref("")

/* A number arriving from the form is split back into the two things it is typed as. */
watch(number, (value) => {
  const held = value ? parsePhoneNumberFromString(value) : undefined
  if (!held) return
  if (held.country) from.value = held.country
  const national = held.formatNational().replace(/^0/, "")
  if (national !== typed.value) typed.value = national
}, {immediate: true})

const name = (code: CountryCode): string => cca2Map.get(code)?.name.common ?? code

/* Only the countries libphonenumber knows a dial code for: the rest cannot be called. */
const dialOptions = computed(() => getCountries()
  .map(code => ({
    key: code,
    label: name(code),
    note: `+${getCountryCallingCode(code)}`,
    flag: code,
  }))
  .sort((a, b) => a.label.localeCompare(b.label)))

const dial = computed<string>(() => `+${getCountryCallingCode(from.value)}`)

/** What the form keeps, rebuilt from the two parts on every keystroke. */
const held = computed<string>(() => {
  const digits = typed.value.replace(/\D/g, "")
  return digits === "" ? "" : `${dial.value}${digits.replace(/^0+/, "")}`
})

watch(held, value => {
  if (value !== number.value) number.value = value
})

/* A number typed or pasted whole is taken apart rather than read as a local one, or the
   country would be dropped out of it. */
const onType = (event: Event) => {
  const raw = (event.target as HTMLInputElement).value
  if (!raw.trimStart().startsWith("+")) {
    typed.value = new AsYouType(from.value).input(raw)
    return
  }

  const whole = parsePhoneNumberFromString(raw)
  if (whole?.country) {
    from.value = whole.country
    typed.value = whole.formatNational().replace(/^0/, "")
    return
  }

  // Still being typed: the dial code may already name a country even where the rest is short.
  const reading = new AsYouType()
  reading.input(raw)
  const said = reading.getCountry()
  if (said) from.value = said
  const code = getCountryCallingCode(from.value)
  typed.value = new AsYouType(from.value).input(raw.replace(new RegExp(`^\\+${code}\\s*`), ""))
}

const pickFrom = (code: string) => {
  from.value = code as CountryCode
}
</script>

<template>
  <div
    class="island-phone"
    :class="{'island-phone--wrong': invalid}"
  >
    <island-picker
      compact
      :disabled="disabled"
      :options="dialOptions"
      :placeholder="`${name(from)} ${dial}`"
      :selected-key="from"
      :testid-prefix="`${testidPrefix}-from`"
      @pick="pickFrom"
    >
      <template #chosen="{option}">
        <country-flag
          :code="option?.key ?? from"
          :size="25"
        />
      </template>
    </island-picker>

    <input
      :id="controlId"
      :aria-describedby="describedBy"
      :aria-invalid="invalid || undefined"
      class="island-phone__number"
      :data-testid="`${testidPrefix}-number`"
      :disabled="disabled"
      :placeholder="placeholder"
      type="tel"
      :value="typed"
      @input="onType"
    >
  </div>
</template>

<style scoped>
/*
 * One box, ruled along its foot like every other field: the country and the number are two
 * controls but one answer, and two boxes read as two questions.
 */
.island-phone {
  display: flex;
  align-items: stretch;
  width: 100%;
  background-color: color-mix(in oklab, var(--color-chalk) 7%, transparent);
  border-bottom: 1px solid var(--color-hairline);
}

.island-phone:focus-within {
  border-bottom-color: var(--color-brand);
  background-color: color-mix(in oklab, var(--color-chalk) 10%, transparent);
}


.island-phone--wrong {
  border-bottom-color: var(--color-danger);
}

/* The country is its own control inside the box, told apart by sitting a shade deeper rather
   than by a line: a rule inside a field reads as two fields. */
/* The flag sits on the middle of the cell and the mark to its right, rather than the pair being
   centred together: a column of these fields reads down the flags. */
.island-phone :deep(.picker__shut) {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  gap: 0;
  width: var(--field-lead, 4.35rem);
  padding-right: 0;
  padding-left: 0;
  background-color: color-mix(in oklab, var(--color-chalk) 6%, transparent);
}

.island-phone :deep(.picker__shut > :first-child) {
  grid-column: 2;
}

.island-phone :deep(.picker__shut .picker__caret) {
  grid-column: 3;
  justify-self: start;
  padding-left: 0.2rem;
}

.island-phone :deep(.picker__shut:hover),
.island-phone :deep(.picker__shut:focus-visible) {
  background-color: color-mix(in oklab, var(--color-chalk) 11%, transparent);
}

/* The picker draws its own field when it is open, so it keeps the box's ground rather than
   laying a second one over it. */
.island-phone :deep(.picker) {
  flex: none;
  width: auto;
}

.island-phone :deep(.picker__search) {
  width: 9rem;
  background: none;
  border-bottom: 0;
}

.island-phone :deep(.picker__shut) {
  height: 100%;
  padding-block: 0;
}

.island-phone__number {
  flex: 1 1 auto;
  min-width: 0;
  padding: 0.6rem 1rem;
  border: 0;
  background: none;
  font-family: var(--font-body);
  font-size: 0.9rem;
  color: var(--color-chalk);
}

.island-phone__number::placeholder {
  color: var(--color-ash);
}

.island-phone__number:focus-visible {
  outline: none;
}

.island-phone__number:disabled {
  color: var(--color-ash);
  cursor: not-allowed;
}
</style>
