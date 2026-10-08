<script lang="ts" setup>
import {computed, ref, watch} from "vue"
import AnswerField from "@/components/form/fields/AnswerField.vue"
import {answerChecks} from "@/components/form/fields/answerChecks"
import QuestionCard from "@/components/form/common/QuestionCard.vue"
import QuestionLabel from "@/components/form/common/QuestionLabel.vue"
import MarkdownView from "@/components/island/MarkdownView.vue"
import {type AnswerRequest, type QuestionResponse, QuestionType, type SurveyResponse} from "@/domains/events"
import {useFormChecks} from "@/composables/useFormChecks"

const props = defineProps<{ survey?: SurveyResponse | null }>()
const answers = defineModel<AnswerRequest[]>({default: () => []})

const questions = computed<QuestionResponse[]>(() => props.survey?.questions ?? [])

const answerIndexByQuestionIdx = ref<Map<number, number>>(new Map())
watch(
  questions,
  (qs) => {
    const answerMap = new Map<number, number>()
    let answerIdx = 0
    for (const q of qs) {
      if (q.type !== QuestionType.DESCRIPTION) {
        answerMap.set(q.idx, answerIdx++)
      }
    }
    answerIndexByQuestionIdx.value = answerMap
  },
  {immediate: true, deep: true},
)

const answerName = (question: QuestionResponse): string => `answers[${answerIndexByQuestionIdx.value.get(question.idx)}]`

const {errorsOf, touch, attempt} = useFormChecks(() => Object.fromEntries(questions.value
  .filter(question => answerIndexByQuestionIdx.value.has(question.idx))
  .map(question => [answerName(question), {
    value: () => answers.value[answerIndexByQuestionIdx.value.get(question.idx)!],
    checks: answerChecks(question),
  }])))

async function validate() {
  return attempt()
}

defineExpose({validate})
</script>

<template>
  <div class="answers-form">
    <template
      v-for="question in questions"
      :key="question?.idx"
    >
      <question-card
        v-if="question.type === QuestionType.DESCRIPTION"
        description
        class="answers-form__item"
      >
        <markdown-view
          class="text-body-1 answers-form__description"
          :source="question.label"
        />
      </question-card>

      <question-card
        v-else
        class="answers-form__item"
      >
        <question-label
          :label="question.label || 'Question'"
          :required="question.required === true"
        />
        <answer-field
          v-if="answerIndexByQuestionIdx.has(question.idx)"
          v-model="answers[answerIndexByQuestionIdx.get(question.idx)!]"
          :error-messages="errorsOf(answerName(question))"
          :question="question"
          @blur="touch(answerName(question))"
        />
      </question-card>
    </template>
  </div>
</template>

<style lang="scss" scoped>
.answers-form {
  display: flex;
  flex-direction: column;
  gap: 0.85rem;

  &__description {
    white-space: pre-wrap;
    word-break: break-word;
  }
}
</style>
