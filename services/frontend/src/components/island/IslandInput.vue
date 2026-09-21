<script lang="ts" setup>
import {computed, ref} from "vue"

/* A line to write on: square, ruled along its foot, lit in blue while it is being used. */
// The box around the input is chrome for the eye, so what a form sets belongs on the input.
defineOptions({name: "IslandInput", inheritAttrs: false})

const {
  type = "text",
  placeholder = "",
  invalid = false,
  disabled = false,
  controlId = undefined,
  describedBy = undefined,
  testid = undefined,
} = defineProps<{
  type?: "text" | "email" | "tel" | "url" | "number" | "password"
  placeholder?: string
  invalid?: boolean
  disabled?: boolean
  controlId?: string
  describedBy?: string
  testid?: string
}>()

const value = defineModel<string>({default: ""})

/* Only what is drawn changes, so a password manager still fills the field. */
const shown = ref(false)
const reveals = computed<boolean>(() => type === "password")
const drawnAs = computed<string>(() => (reveals.value && shown.value ? "text" : type))
</script>

<template>
  <span class="island-input__box">
    <input
      :id="controlId"
      v-model="value"
      :aria-describedby="describedBy"
      :aria-invalid="invalid || undefined"
      class="island-input"
      :class="{
        'island-input--wrong': invalid,
        'island-input--revealed': reveals,
      }"
      :data-testid="testid"
      :disabled="disabled"
      :placeholder="placeholder"
      :type="drawnAs"
      v-bind="$attrs"
    >

    <button
      v-if="reveals"
      :aria-label="shown ? 'Hide the password' : 'Show the password'"
      class="island-input__eye"
      :data-testid="testid ? `${testid}-reveal` : undefined"
      tabindex="-1"
      type="button"
      @click="shown = !shown"
    >
      <svg
        aria-hidden="true"
        fill="none"
        stroke="currentColor"
        stroke-linecap="round"
        stroke-linejoin="round"
        stroke-width="1.6"
        viewBox="0 0 24 24"
      >
        <path d="M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12Z" />
        <circle
          cx="12"
          cy="12"
          r="3"
        />
        <path
          v-if="shown"
          d="M4 20 20 4"
        />
      </svg>
    </button>
  </span>
</template>

<style scoped>
.island-input__box {
  position: relative;
  display: block;
  width: 100%;
}

.island-input {
  width: 100%;
  padding: 0.6rem 0.9rem;
  border: 0;
  border-bottom: 1px solid var(--color-hairline);
  background-color: color-mix(in oklab, var(--color-chalk) 7%, transparent);
  font-family: var(--font-body);
  font-size: 0.9rem;
  color: var(--color-chalk);
}

.island-input::placeholder {
  color: var(--color-ash);
}


.island-input:focus-visible {
  outline: none;
  border-bottom-color: var(--color-brand);
  background-color: color-mix(in oklab, var(--color-chalk) 10%, transparent);
}

.island-input--wrong {
  border-bottom-color: var(--color-danger);
}

.island-input--revealed {
  padding-right: 2.6rem;
}

/* Out of the tab order: it changes nothing that is kept, and a field a reader tabs into should
   land on the next question rather than on a way to look at this one. */
.island-input__eye {
  position: absolute;
  top: 50%;
  right: 0.6rem;
  translate: 0 -50%;
  display: grid;
  place-items: center;
  padding: 0.25rem;
  border: 0;
  background: none;
  color: var(--color-ash);
  cursor: pointer;
}

.island-input__eye:hover {
  color: var(--color-chalk);
}

.island-input__eye svg {
  width: 17px;
  height: 17px;
}

.island-input:disabled {
  color: var(--color-ash);
  cursor: not-allowed;
}
</style>
