<script lang="ts" setup>
import {QuestionType, type QuestionRequest, type SurveyRequest} from "@/services/api"
import QuestionField from "@/components/form/fields/QuestionField.vue"

type QuestionModel = QuestionRequest
type SurveyModel = SurveyRequest

const model = defineModel<SurveyModel>({default: {questions: []}})

function defaultRequired(type: QuestionType): boolean {
  return type === QuestionType.OPEN || type === QuestionType.RADIO
}

function addQuestion(type: QuestionType) {
  model.value.questions ??= []
  const nextIdx = model.value.questions.length
  const base: QuestionModel = {type, label: "", idx: nextIdx, required: defaultRequired(type)}
  const q: QuestionModel =
    type === QuestionType.OPEN || type === QuestionType.DESCRIPTION
      ? base
      : {
        ...base,
        choiceLabels: ["", ""],
      }
  model.value = {...model.value, questions: [...model.value.questions, q]}
}

function reindex(questions: QuestionModel[]) {
  return questions.map((q, idx) => ({...q, idx}))
}

function updateQuestion(i: number, updated: QuestionModel) {
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
  <div class="survey-editor">
    <header class="survey-editor__header">
      <h3 class="survey-editor__title">
        Edit sign-up form
      </h3>
      <p class="survey-editor__subtitle text-body-2 text-medium-emphasis">
        Define the questions respondents will answer when signing up for this event.
      </p>
    </header>

    <div
      v-if="(model.questions ?? []).length"
      class="survey-editor__list"
    >
      <template
        v-for="(q, i) in model.questions"
        :key="q.idx"
      >
        <question-field
          :can-move-down="i < model.questions.length - 1"
          :can-move-up="i > 0"
          :model-value="q"
          @remove="removeQuestion(i)"
          @update:model-value="(val: QuestionModel) => updateQuestion(i, val)"
          @move-up="moveQuestionUp(i)"
          @move-down="moveQuestionDown(i)"
        />
      </template>
    </div>
    <p
      v-else
      class="text-medium-emphasis text-body-2 mb-2"
    >
      No questions yet. Add a question or a free-text description below to build the sign-up form.
    </p>

    <v-menu location="bottom">
      <template #activator="{ props }">
        <v-btn
          block
          v-bind="props"
          variant="tonal"
          prepend-icon="mdi-plus"
        >
          Add question to sign-up form
        </v-btn>
      </template>
      <v-list>
        <v-list-item
          prepend-icon="mdi-text"
          @click="addQuestion(QuestionType.DESCRIPTION)"
        >
          Description without a question
        </v-list-item>
        <v-list-item
          prepend-icon="mdi-form-textbox"
          @click="addQuestion(QuestionType.OPEN)"
        >
          Open question
        </v-list-item>
        <v-list-item
          prepend-icon="mdi-radiobox-marked"
          @click="addQuestion(QuestionType.RADIO)"
        >
          Multiple choice question
        </v-list-item>
        <v-list-item
          prepend-icon="mdi-checkbox-marked-outline"
          @click="addQuestion(QuestionType.CHECKBOX)"
        >
          Question with checkboxes
        </v-list-item>
      </v-list>
    </v-menu>

  </div>
</template>

<style lang="scss" scoped>
.survey-editor {
  padding: 1rem;
  border-radius: 10px;
  border: 1px solid rgba(var(--v-theme-on-surface), 0.10);
  background-color: rgba(var(--v-theme-surface), 0.55);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);

  &__header {
    margin-bottom: 1rem;
  }

  &__title {
    font-size: 1.05rem;
    font-weight: 600;
    line-height: 1.3;
    margin: 0 0 0.15rem;
  }

  &__subtitle {
    margin: 0;
  }

  &__list {
    display: flex;
    flex-direction: column;
    gap: 0.75rem;
    margin-bottom: 0.85rem;
  }
}
</style>
