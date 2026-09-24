<script lang="ts" setup>
/**
 * One question of a sign-up form, as a flat band: its number and type, a Required tick, and
 * buttons to move or delete it. A choice question lists its options under the text, never
 * fewer than two.
 */
import {Field} from "vee-validate"
import {computed} from "vue"
import {type QuestionRequest, QuestionType} from "@/domains/events"
import CheckBox from "@/components/island/CheckBox.vue"
import CutButton from "@/components/island/CutButton.vue"
import FormControl from "@/components/island/FormControl.vue"
import IconButton from "@/components/island/IconButton.vue"

defineOptions({name: "QuestionEditor"})

const {canMoveUp = true, canMoveDown = true} = defineProps<{canMoveUp?: boolean; canMoveDown?: boolean}>()
const emit = defineEmits<{moveUp: []; moveDown: []; remove: []}>()
const model = defineModel<QuestionRequest>({required: true})

const FEWEST_CHOICES = 2

const TYPE_SAID: Record<QuestionType, string> = {
  [QuestionType.DESCRIPTION]: "Description",
  [QuestionType.OPEN]: "Open question",
  [QuestionType.RADIO]: "Multiple choice",
  [QuestionType.CHECKBOX]: "Checkboxes",
}

const isDescription = computed<boolean>(() => model.value.type === QuestionType.DESCRIPTION)
const isChoice = computed<boolean>(
  () => model.value.type === QuestionType.RADIO || model.value.type === QuestionType.CHECKBOX,
)
const number = computed<string>(() => `${model.value.idx + 1}`.padStart(2, "0"))
const choices = computed<string[]>(() => model.value.choiceLabels ?? [])
const atFewest = computed<boolean>(() => choices.value.length <= FEWEST_CHOICES)
const what = computed<string>(() => (isDescription.value ? "description" : "question"))

const setLabel = (label: string) => {
  model.value = {...model.value, label}
}

const setRequired = (required: boolean) => {
  model.value = {...model.value, required}
}

const setChoices = (choiceLabels: string[]) => {
  model.value = {...model.value, choiceLabels}
}

const setChoice = (j: number, label: string) => {
  const next = choices.value.slice()
  next[j] = label
  setChoices(next)
}

const swapChoices = (a: number, b: number) => {
  const next = choices.value.slice()
  ;[next[a], next[b]] = [next[b]!, next[a]!]
  setChoices(next)
}

const removeChoice = (j: number) => {
  if (atFewest.value) return
  setChoices(choices.value.filter((_, at) => at !== j))
}
</script>

<template>
  <div class="question">
    <div class="question__head">
      <span class="question__number">{{ number }}</span>
      <span class="question__type">{{ TYPE_SAID[model.type] }}</span>
      <span class="question__acts">
        <check-box
          v-if="!isDescription"
          class="question__required"
          label="Required"
          :model-value="model.required ?? false"
          @update:model-value="setRequired"
        />
        <icon-button
          :disabled="!canMoveUp"
          :label="`Move ${what} up`"
          @click="emit('moveUp')"
        >
          <svg
            aria-hidden="true"
            fill="none"
            stroke="currentColor"
            stroke-width="1.6"
            viewBox="0 0 24 24"
          ><path d="m6 14.5 6-6 6 6" /></svg>
        </icon-button>
        <icon-button
          :disabled="!canMoveDown"
          :label="`Move ${what} down`"
          @click="emit('moveDown')"
        >
          <svg
            aria-hidden="true"
            fill="none"
            stroke="currentColor"
            stroke-width="1.6"
            viewBox="0 0 24 24"
          ><path d="m6 9.5 6 6 6-6" /></svg>
        </icon-button>
        <icon-button
          danger
          :label="`Delete ${what}`"
          @click="emit('remove')"
        >
          <svg
            aria-hidden="true"
            fill="none"
            stroke="currentColor"
            stroke-width="1.6"
            viewBox="0 0 24 24"
          ><path d="M4.5 7h15M9.5 7V4.5h5V7M6.5 7l1 13h9l1-13" /></svg>
        </icon-button>
      </span>
    </div>

    <Field
      v-slot="{value, errors, handleChange, handleBlur}"
      :model-value="model.label"
      :name="`survey.questions[${model.idx}].label`"
      rules="required"
    >
      <form-control
        :error-messages="errors"
        :kind="isDescription ? 'markdown' : 'textarea'"
        :label="isDescription ? 'Description text*' : 'Question text*'"
        :model-value="value"
        :rows="isDescription ? 2 : 1"
        @blur="handleBlur"
        @update:model-value="(label: string | null) => { setLabel(label ?? ''); handleChange(label ?? '') }"
      />
    </Field>

    <template v-if="isChoice">
      <div class="question__options">
        <div
          v-for="(_choice, j) in choices"
          :key="j"
          class="question__option"
        >
          <span
            aria-hidden="true"
            class="question__glyph"
          >
            <svg
              fill="none"
              stroke="currentColor"
              stroke-width="1.6"
              viewBox="0 0 24 24"
            >
              <circle
                v-if="model.type === QuestionType.RADIO"
                cx="12"
                cy="12"
                r="7.5"
              />
              <rect
                v-else
                height="14"
                width="14"
                x="5"
                y="5"
              />
            </svg>
          </span>
          <Field
            v-slot="{value, errors, handleChange, handleBlur}"
            :model-value="choices[j] ?? ''"
            :name="`survey.questions[${model.idx}].choiceLabels[${j}]`"
            rules="required|maxChars:100"
          >
            <form-control
              :error-messages="errors"
              :label="`Option ${j + 1}*`"
              :model-value="value"
              @blur="handleBlur"
              @update:model-value="(label: string | null) => { setChoice(j, label ?? ''); handleChange(label ?? '') }"
            />
          </Field>
          <span class="question__option-acts">
            <icon-button
              :disabled="j === 0"
              label="Move option up"
              @click="swapChoices(j - 1, j)"
            >
              <svg
                aria-hidden="true"
                fill="none"
                stroke="currentColor"
                stroke-width="1.6"
                viewBox="0 0 24 24"
              ><path d="m6 14.5 6-6 6 6" /></svg>
            </icon-button>
            <icon-button
              :disabled="j === choices.length - 1"
              label="Move option down"
              @click="swapChoices(j, j + 1)"
            >
              <svg
                aria-hidden="true"
                fill="none"
                stroke="currentColor"
                stroke-width="1.6"
                viewBox="0 0 24 24"
              ><path d="m6 9.5 6 6 6-6" /></svg>
            </icon-button>
            <icon-button
              danger
              :disabled="atFewest"
              :label="atFewest ? 'A choice keeps at least two options' : 'Delete option'"
              @click="removeChoice(j)"
            >
              <svg
                aria-hidden="true"
                fill="none"
                stroke="currentColor"
                stroke-width="1.6"
                viewBox="0 0 24 24"
              ><path d="M4.5 7h15M9.5 7V4.5h5V7M6.5 7l1 13h9l1-13" /></svg>
            </icon-button>
          </span>
        </div>
      </div>
      <div>
        <cut-button
          tone="quiet"
          @click="setChoices([...choices, ''])"
        >
          <svg
            aria-hidden="true"
            class="question__plus"
            fill="none"
            stroke="currentColor"
            stroke-width="1.8"
            viewBox="0 0 24 24"
          ><path d="M12 5v14M5 12h14" /></svg>Add option
        </cut-button>
      </div>
    </template>
  </div>
</template>

<style scoped>
.question {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 0.8rem;
  padding: 1rem 1.2rem 1.2rem;
  background-color: var(--band-ground);
}

/* The question being written leans a brand bar along its left edge. */
.question:focus-within::before {
  position: absolute;
  top: 0.8rem;
  bottom: 0.8rem;
  left: 0.45rem;
  width: 3px;
  content: "";
  background: var(--color-brand);
  transform: skewX(-12deg);
}

.question__head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.8rem;
}

.question__number {
  font-family: var(--font-display);
  font-size: 1.15rem;
  line-height: 1;
  color: var(--color-chalk);
}

.question__type {
  font-size: 11px;
  font-weight: 500;
  color: var(--color-ash);
  text-transform: uppercase;
  letter-spacing: 0.3em;
}

.question__acts {
  display: flex;
  align-items: center;
  gap: 0.25rem;
  margin-left: auto;
}

.question__required {
  margin-right: 0.6rem;
}

.question__options {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
  margin-left: 0.25rem;
}

.question__option {
  display: grid;
  grid-template-columns: 1.25rem minmax(0, 1fr) auto;
  align-items: start;
  gap: 0 0.6rem;
}

/* On the input's line rather than centred on the field, whose foot holds room for its message. */
.question__glyph,
.question__option-acts {
  display: flex;
  align-items: center;
  height: 3.25rem;
  color: var(--color-ash);
}

/* Preflight draws an svg as a block, which would put the plus above the word. */
.question__plus {
  display: inline-block;
  width: 14px;
  height: 14px;
  margin-right: 0.4rem;
  vertical-align: -2px;
}

.question__glyph svg {
  width: 16px;
  height: 16px;
}
</style>
