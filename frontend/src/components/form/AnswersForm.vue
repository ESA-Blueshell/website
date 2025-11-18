<script lang="ts" setup>
import {computed, ref, watch} from "vue"
import {Form, type FormContext} from "vee-validate"
import AnswerField from "@/components/form/fields/AnswerField.vue"
import {type Answer, type Question, QuestionType, type Survey} from "@/services/api"

const props = defineProps<{ survey?: Survey | null }>()
const answers = defineModel<Answer[]>({default: []})

const formRef = ref<FormContext | undefined>()
const questions = computed<Question[]>(() => props.survey?.questions ?? [])

const answerIndexByQuestionIdx = ref<Map<number, number>>(new Map())
watch(
  questions,
  (qs) => {
    const map = new Map<number, number>()
    let idx = 0
    for (const q of qs) {
      if (q.type !== QuestionType.DESCRIPTION) {
        map.set(q.idx, idx++)
      }
    }
    answerIndexByQuestionIdx.value = map
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
  >
    <div
      v-for="question in questions"
      :key="question?.idx"
      class="mb-4"
    >
      <p
        v-if="question.type === QuestionType.DESCRIPTION"
        class="text-body-1"
      >
        {{ question.label }}
      </p>

      <template v-else>
        <p
          v-if="question.type === QuestionType.RADIO || question.type === QuestionType.CHECKBOX"
          class="text-h6 mb-2"
        >
          {{ question.label }}
        </p>

        <answer-field
          v-if="answerIndexByQuestionIdx.has(question.idx)"
          v-model="answers[answerIndexByQuestionIdx.get(question.idx)!]"
          :question="question"
        />
      </template>
    </div>
  </Form>
</template>
