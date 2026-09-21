<script lang="ts" setup>
/**
 * The chrome every island field shares: its name, what it says when it is wrong, and the
 * room that keeps the form from jumping when it says it.
 */
import {computed, useId} from "vue"
import {useMotionAllowed} from "@/components/island/useMotionAllowed"

defineOptions({name: "IslandField"})

const {
  label,
  hint = "",
  error = "",
  required = false,
  variant = "stacked",
  filled = false,
  testid = undefined,
} = defineProps<{
  label: string
  /**
   * Where the label sits: above the control, or inside it.
   *
   * Inside, it rests on the line a visitor is about to write on and rises out of the way once
   * there is something to read: the name of the field and its answer are then one thing rather
   * than two lines. It costs the caller one fact, whether the control holds anything, which
   * only the control knows.
   */
  variant?: "stacked" | "inside"
  /** The control holds something, so an inside label stays risen. */
  filled?: boolean
  /** Said under the control while it is right, which is where the error appears when it is not. */
  hint?: string
  error?: string
  required?: boolean
  testid?: string
}>()

/* The label's rise explains where the answer went, so it shortens rather than stopping. */
const motion = useMotionAllowed()
const rise = computed<string>(() => `${motion.duration(0.18)}s`)

const uid = useId()
const controlId = `${uid}-control`
const labelId = `${uid}-label`
const saidId = `${uid}-said`
</script>

<template>
  <div
    class="island-field"
    :class="[`island-field--${variant}`, {'island-field--filled': filled}]"
    :style="{'--rise': rise}"
    :data-testid="testid"
  >
    <label
      v-if="variant === 'stacked'"
      :id="labelId"
      class="island-field__label"
      :for="controlId"
    >
      {{ label }}<span
        v-if="required"
        aria-hidden="true"
        class="island-field__must"
      >*</span>
    </label>

    <div class="island-field__box">
      <label
        v-if="variant === 'inside'"
        :id="labelId"
        class="island-field__label"
        :for="controlId"
      >
        {{ label }}<span
          v-if="required"
          aria-hidden="true"
          class="island-field__must"
        >*</span>
      </label>

      <slot
        :control-id="controlId"
        :described-by="hint || error ? saidId : undefined"
        :invalid="error !== ''"
        :label-id="labelId"
      />
    </div>

    <!-- Kept in the flow whether or not it says anything, so a form does not jump the moment a
         field is judged. -->
    <p
      :id="saidId"
      class="island-field__said"
      :class="{'island-field__said--wrong': error !== ''}"
    >
      {{ error || hint }}
    </p>
  </div>
</template>

<style scoped>
.island-field {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
  width: 100%;
}

/* At rest the label sits where the answer will go, and rises into the box once there is one. */
.island-field--inside {
  position: relative;
  gap: 0;
}

.island-field__box {
  position: relative;
  display: flex;
  flex-direction: column;
}

.island-field--inside .island-field__label {
  position: absolute;
  /* Centred on the control rather than set at a fixed height: the box is as tall as whatever
     is in it, and a phone field is not a text field. */
  top: 50%;
  translate: 0 -50%;
  /* A control with something at its left edge, like the phone field's flag, sets this so the
     label rests beside it rather than under it. */
  left: var(--field-label-left, 0.9rem);
  font-size: 0.9rem;
  font-weight: 400;
  letter-spacing: normal;
  text-transform: none;
  color: var(--color-ash);
  pointer-events: none;
  transition: top var(--rise, 0.18s) var(--ease-out-quint),
    translate var(--rise, 0.18s) var(--ease-out-quint),
    font-size var(--rise, 0.18s) var(--ease-out-quint),
    letter-spacing var(--rise, 0.18s) var(--ease-out-quint);
}

.island-field--inside:focus-within .island-field__label,
.island-field--inside.island-field--filled .island-field__label {
  top: 0.45rem;
  translate: 0 0;
  font-size: 0.62rem;
  font-weight: 500;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.island-field--inside:focus-within .island-field__label {
  color: var(--color-brand);
}

/* The label is the placeholder while it rests, so the control does not say the same thing
   under it. */
.island-field--inside:not(:focus-within) :deep(input::placeholder) {
  color: transparent;
}

/* Room for the risen label, taken whether or not it has risen, so nothing shifts under it. */
/* The markdown editor writes its own padding, so it is told how much room the label wants
   rather than having it set from here. */
.island-field--inside :deep(.island-markdown) {
  --md-top: 1.7rem;
}

.island-field--inside :deep(input),
.island-field--inside :deep(.picker__search) {
  padding-top: 1.5rem;
  padding-bottom: 0.5rem;
}

/* What stands beside the typing, like the phone field's flag, is not moved down to make room
   for the label: it fills the box and sits on the middle of it. */
.island-field--inside :deep(.picker__shut) {
  padding-top: 0;
  padding-bottom: 0;
}

.island-field--inside .island-field__said {
  margin-top: 0.3rem;
}

.island-field__label {
  font-family: var(--font-body);
  font-size: 0.7rem;
  font-weight: 500;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--color-ash);
}

/* Wrong, and said in the label too: the eye goes to the name of the field before the line
   under it. */
.island-field--inside:has(.island-field__said--wrong) .island-field__label {
  color: var(--color-danger);
}

/* The mark goes with the name it marks rather than staying blue against a red label. */
.island-field--inside:has(.island-field__said--wrong) .island-field__must {
  color: currentcolor;
}

.island-field__must {
  margin-left: 0.15rem;
  color: var(--color-brand);
}

.island-field__said {
  min-height: 1rem;
  font-family: var(--font-body);
  font-size: 0.72rem;
  line-height: 1.35;
  color: var(--color-ash);
}

.island-field__said--wrong {
  color: var(--color-danger);
}
</style>
