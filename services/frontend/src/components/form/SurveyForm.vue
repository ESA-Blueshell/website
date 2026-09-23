<script lang="ts" setup>
/**
 * The questions a sign-up asks, in order. Each question is a QuestionEditor, and a row of quiet
 * buttons adds one of each type. The name, email, Discord and phone number are always asked, so
 * they are never questions here.
 */
import {QuestionType, type QuestionRequest, type SurveyRequest} from "@/domains/events"
import QuestionEditor from "@/components/form/fields/QuestionEditor.vue"
import CutButton from "@/components/island/CutButton.vue"

/* VvField hands every control a label and its messages; the questions carry their own. */
defineOptions({name: "SurveyForm", inheritAttrs: false})

const model = defineModel<SurveyRequest>({default: () => ({questions: []})})

/* The plus says "add" to the eye; the name says it to a reader being told the page. */
const ADDS: {type: QuestionType; label: string; said: string}[] = [
  {type: QuestionType.DESCRIPTION, label: "Description", said: "Add a description"},
  {type: QuestionType.OPEN, label: "Open question", said: "Add an open question"},
  {type: QuestionType.RADIO, label: "Multiple choice", said: "Add a multiple choice question"},
  {type: QuestionType.CHECKBOX, label: "Checkboxes", said: "Add a checkboxes question"},
]

const questions = (): QuestionRequest[] => model.value.questions ?? []

const reindex = (next: QuestionRequest[]): QuestionRequest[] => next.map((q, idx) => ({...q, idx}))

const setQuestions = (next: QuestionRequest[]) => {
  model.value = {...model.value, questions: reindex(next)}
}

const addQuestion = (type: QuestionType) => {
  const base: QuestionRequest = {
    type,
    label: "",
    idx: questions().length,
    required: type === QuestionType.OPEN || type === QuestionType.RADIO,
  }
  const choice = type === QuestionType.RADIO || type === QuestionType.CHECKBOX
  setQuestions([...questions(), choice ? {...base, choiceLabels: ["", ""]} : base])
}

const updateQuestion = (i: number, updated: QuestionRequest) => {
  setQuestions(questions().map((q, at) => (at === i ? updated : q)))
}

const removeQuestion = (i: number) => {
  setQuestions(questions().filter((_, at) => at !== i))
}

const swapQuestions = (a: number, b: number) => {
  const next = questions().slice()
  ;[next[a], next[b]] = [next[b]!, next[a]!]
  setQuestions(next)
}
</script>

<template>
  <div class="survey">
    <div
      v-if="questions().length"
      class="survey__list"
    >
      <question-editor
        v-for="(q, i) in questions()"
        :key="q.idx"
        :can-move-down="i < questions().length - 1"
        :can-move-up="i > 0"
        :model-value="q"
        @move-down="swapQuestions(i, i + 1)"
        @move-up="swapQuestions(i - 1, i)"
        @remove="removeQuestion(i)"
        @update:model-value="(updated: QuestionRequest) => updateQuestion(i, updated)"
      />
    </div>
    <p
      v-else
      class="survey__none"
    >
      No questions yet.
    </p>

    <div class="survey__adds">
      <cut-button
        v-for="add in ADDS"
        :key="add.type"
        :aria-label="add.said"
        tone="quiet"
        @click="addQuestion(add.type)"
      >
        <svg
          aria-hidden="true"
          class="survey__plus"
          fill="none"
          stroke="currentColor"
          stroke-width="1.8"
          viewBox="0 0 24 24"
        ><path d="M12 5v14M5 12h14" /></svg>{{ add.label }}
      </cut-button>
    </div>
  </div>
</template>

<style scoped>
.survey__list {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.survey__none {
  font-size: 0.9rem;
  color: var(--color-ash);
}

.survey__adds {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  padding-top: 0.6rem;
}

/* Preflight draws an svg as a block, which would put the plus above the word. */
.survey__plus {
  display: inline-block;
  width: 14px;
  height: 14px;
  margin-right: 0.4rem;
  vertical-align: -2px;
}
</style>
