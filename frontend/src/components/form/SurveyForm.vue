<script lang="ts" setup>
import {type Question, QuestionType, type Survey} from "@/services/api"
import QuestionField from "@/components/form/fields/QuestionField.vue"
import {computed, ref} from "vue"

const model = defineModel<Survey>({default: {questions: []}})
const id = ref<number | undefined>(model.value.id)
const initialQuestions = ref<Question[]>(JSON.parse(JSON.stringify(model.value.questions ?? [])))
const initialJson = ref(JSON.stringify(initialQuestions.value))
const isDirty = computed(() => JSON.stringify(model.value.questions) !== initialJson.value)

function addQuestion(type: QuestionType) {
  model.value.questions ??= []
  const nextIdx = model.value.questions.length
  const base = {type, label: "", idx: nextIdx} as Question
  const q: Question =
    type === QuestionType.OPEN || type === QuestionType.DESCRIPTION
      ? base
      : {
        ...base,
        choiceLabels: ["", ""],
      }
  model.value = {...model.value, questions: [...model.value.questions, q]}
}

function reindex(questions: Question[]) {
  return questions.map((q, idx) => ({...q, idx}))
}

function updateQuestion(i: number, updated: Question) {
  const next = model.value.questions.slice()
  next[i] = {...updated, idx: i}
  model.value = {...model.value, questions: next}
}

function removeQuestion(i: number) {
  const next = model.value.questions.slice()
  next.splice(i, 1)
  model.value = {...model.value, questions: reindex(next)}
}

function moveQuestionUp(i: number) {
  if (i <= 0) return
  const next = model.value.questions.slice()
  ;[next[i - 1]!, next[i]!] = [next[i]!, next[i - 1]!]
  model.value = {...model.value, questions: reindex(next)}
}

function moveQuestionDown(i: number) {
  if (i >= model.value.questions.length - 1) return
  const next = model.value.questions.slice()
  ;[next[i + 1]!, next[i]!] = [next[i]!, next[i + 1]!]
  model.value = {...model.value, questions: reindex(next)}
}
</script>

<template>
  <div class="pa-4 form">
    <template
      v-for="(q, i) in model.questions"
      :key="q.idx"
    >
      <question-field
        :can-move-down="i < model.questions.length - 1"
        :can-move-up="i > 0"
        :model-value="q"
        @remove="removeQuestion(i)"
        @update:model-value="(val: Question) => updateQuestion(i, val)"
        @move-up="moveQuestionUp(i)"
        @move-down="moveQuestionDown(i)"
      />
    </template>

    <v-menu location="bottom">
      <template #activator="{ props }">
        <v-btn
          block
          v-bind="props"
          variant="outlined"
        >
          Add question or text to sign-up form
        </v-btn>
      </template>
      <v-list>
        <v-list-item @click="addQuestion(QuestionType.DESCRIPTION)">
          Description without a question
        </v-list-item>
        <v-list-item @click="addQuestion(QuestionType.OPEN)">
          Open question
        </v-list-item>
        <v-list-item @click="addQuestion(QuestionType.RADIO)">
          Multiple choice question
        </v-list-item>
        <v-list-item @click="addQuestion(QuestionType.CHECKBOX)">
          Question with checkboxes
        </v-list-item>
      </v-list>
    </v-menu>

    <v-expand-transition class="mt-4">
      <v-alert
        v-if="id && isDirty"
        prominent
        type="warning"
        variant="outlined"
      >
        Editing the form will remove existing responses after submit.
      </v-alert>
    </v-expand-transition>
  </div>
</template>

<style lang="scss" scoped>
@use '../../styles/settings';

.form {
  border-radius: settings.$border-radius-root;
  border: 1px solid rgb(var(--v-theme-accent));
}
</style>
