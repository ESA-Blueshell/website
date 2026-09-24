<script lang="ts" setup>
import {computed, ref, watch} from "vue"
import {Form, type FormContext} from "vee-validate"
import AnswerField from "@/components/form/fields/AnswerField.vue"
import QuestionCard from "@/components/form/common/QuestionCard.vue"
import QuestionLabel from "@/components/form/common/QuestionLabel.vue"
import MarkdownView from "@/components/island/MarkdownView.vue"
import {type AnswerRequest, type QuestionResponse, QuestionType, type SurveyResponse} from "@/domains/events"

const props = defineProps<{ survey?: SurveyResponse | null }>()
const answers = defineModel<AnswerRequest[]>({default: () => []})

const formRef = ref<FormContext | undefined>()
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

async function validate() {
  const result = await formRef.value?.validate()
  return !!result?.valid
}

defineExpose({validate})
</script>

<template>
  <Form
    ref="formRef"
    as="div"
    class="answers-form"
  >
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
          :question="question"
        />
      </question-card>
    </template>
  </Form>
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
