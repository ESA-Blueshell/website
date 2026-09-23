<script lang="ts" setup>
/* A moment: a day and a time sharing one box, for an event's start, end and sign-up deadline.
   The value kept is the local `yyyy-MM-ddTHH:mm` a datetime-local input would hold, and it is
   empty until both halves are. */
import {ref, watch} from "vue"
import DateInput from "./DateInput.vue"
import TimeInput from "./TimeInput.vue"

defineOptions({name: "DateTimeInput", inheritAttrs: false})

const {
  invalid = false,
  disabled = false,
  controlId = undefined,
  describedBy = undefined,
  min = undefined,
  step = 15,
  testid = undefined,
} = defineProps<{
  invalid?: boolean
  disabled?: boolean
  controlId?: string
  describedBy?: string
  /** The earliest moment that may be chosen; only its day bars the calendar. */
  min?: string
  step?: number
  testid?: string
}>()

const value = defineModel<string>({default: ""})

const box = ref<HTMLElement | null>(null)
const day = ref("")
const time = ref("")

watch(value, (held) => {
  const parts = /^(\d{4}-\d{2}-\d{2})T(\d{2}:\d{2})/.exec(held)
  if (parts) {
    day.value = parts[1]!
    time.value = parts[2]!
  } else if (held === "") {
    day.value = ""
    time.value = ""
  }
}, {immediate: true})

/* A half on its own is not a moment, so the form is told nothing until both are there. */
const joined = () => {
  value.value = day.value !== "" && time.value !== "" ? `${day.value}T${time.value}` : ""
}
</script>

<template>
  <span
    ref="box"
    class="island-when"
    :class="{'island-when--wrong': invalid}"
    :data-testid="testid"
  >
    <date-input
      v-model="day"
      :anchor-to="box"
      bare
      :control-id="controlId"
      :described-by="describedBy"
      :disabled="disabled"
      :invalid="invalid"
      :min="min?.slice(0, 10)"
      :testid="testid ? `${testid}-day` : undefined"
      v-bind="$attrs"
      @update:model-value="joined"
    />
    <span
      aria-hidden="true"
      class="island-when__rule"
    />
    <time-input
      v-model="time"
      :anchor-to="box"
      aria-label="Time"
      bare
      class="island-when__time"
      :described-by="describedBy"
      :disabled="disabled"
      :invalid="invalid"
      :step="step"
      :testid="testid ? `${testid}-time` : undefined"
      @update:model-value="joined"
    />
  </span>
</template>

<style scoped>
.island-when {
  position: relative;
  display: flex;
  align-items: stretch;
  width: 100%;
  background-color: color-mix(in oklab, var(--color-chalk) 7%, transparent);
  border-bottom: 1px solid var(--color-ok);
}

.island-when:focus-within,
.island-when:has(.island-date--open, .island-time--open) {
  background-color: color-mix(in oklab, var(--color-chalk) 10%, transparent);
  border-bottom-color: var(--color-brand);
}

.island-when--wrong {
  border-bottom-color: var(--color-wrong);
}

.island-when__rule {
  flex: none;
  width: 1px;
  margin: 0.55rem 0;
  background: var(--color-hairline);
}

/* The time is five characters and a clock, so it takes their width and the day the rest. */
.island-when__time {
  flex: 0 0 8.5rem;
  width: auto;
}
</style>
