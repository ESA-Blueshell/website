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
          v-model="answers[question.idx]"
          :question="question"
        />
      </template>
    </div>
  </Form>
</template>

<script lang="ts" setup>
import {computed, ref} from "vue"
import {Form, type FormContext} from "vee-validate"
import AnswerField from "@/components/form/fields/AnswerField.vue"
import {type Answer, type Question, QuestionType, type Survey} from "@/services/api"

const props = defineProps<{ survey?: Survey | null }>()
const answers = defineModel<Answer[]>({default: []})

const formRef = ref<FormContext | undefined>()

async function validate() {
  const result = await formRef.value?.validate()
  return !!result?.valid
}

const questions = computed<Question[]>(() => props.survey?.questions ?? [])

defineExpose({validate})
</script>

