<script lang="ts" setup>
/* An amount in euros, kept as the decimal string the api stores: two places, never negative. */
import {ref, watch} from "vue"

defineOptions({name: "MoneyInput", inheritAttrs: false})

const {
  invalid = false,
  disabled = false,
  controlId = undefined,
  describedBy = undefined,
  testid = undefined,
} = defineProps<{
  invalid?: boolean
  disabled?: boolean
  controlId?: string
  describedBy?: string
  testid?: string
}>()

const value = defineModel<string>({default: ""})

const typed = ref("")
watch(value, (held) => {
  typed.value = held
}, {immediate: true})

/* A comma is what a Dutch keyboard writes, and the minus is dropped rather than refused: a
   contribution below nothing is not a thing somebody meant to type. */
const onType = (event: Event) => {
  const raw = (event.target as HTMLInputElement).value
  const kept = raw.replace(",", ".").replaceAll(/[^\d.]/g, "")
  const [whole, ...rest] = kept.split(".") as [string, ...string[]]
  const cents = rest.join("").slice(0, 2)
  typed.value = rest.length > 0 ? `${whole}.${cents}` : whole
  value.value = typed.value
}

/* Written out on the way out, so what is stored is always xx.xx. */
const onBlur = () => {
  if (typed.value === "") {
    value.value = ""
    return
  }
  const amount = Number(typed.value)
  typed.value = amount.toFixed(2)
  value.value = typed.value
}
</script>

<template>
  <span
    class="island-money"
    :class="{'island-money--wrong': invalid}"
  >
    <span
      aria-hidden="true"
      class="island-money__sign"
    >&euro;</span>

    <input
      :id="controlId"
      :aria-describedby="describedBy"
      :aria-invalid="invalid || undefined"
      class="island-money__typed"
      :data-testid="testid"
      :disabled="disabled"
      inputmode="decimal"
      placeholder="0.00"
      type="text"
      :value="typed"
      v-bind="$attrs"
      @blur="onBlur"
      @input="onType"
    >
  </span>
</template>

<style scoped>
.island-money {
  display: flex;
  align-items: stretch;
  width: 100%;
  background-color: color-mix(in oklab, var(--color-chalk) 7%, transparent);
  border-bottom: 1px solid var(--color-ok);
}

.island-money:focus-within {
  background-color: color-mix(in oklab, var(--color-chalk) 10%, transparent);
  border-bottom-color: var(--color-brand);
}

.island-money--wrong {
  border-bottom-color: var(--color-wrong);
}

/* The same shaded cell the phone field gives its dial code, so a column of fields lines up. */
.island-money__sign {
  display: flex;
  flex: none;
  align-items: center;
  justify-content: center;
  width: var(--field-lead, 4.35rem);
  font-family: var(--font-body);
  font-size: 0.95rem;
  color: var(--color-ash);
  background-color: color-mix(in oklab, var(--color-chalk) 6%, transparent);
}

.island-money__typed {
  flex: 1 1 auto;
  min-width: 0;
  padding: 0.6rem 1rem;
  font-family: var(--font-body);
  font-size: 0.9rem;
  color: var(--color-chalk);
  background: none;
  border: 0;
  outline: none;
}

.island-money__typed::placeholder {
  color: var(--color-ash);
}
</style>
