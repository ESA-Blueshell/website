<script lang="ts" setup>
import {computed, onMounted, ref, watch} from "vue"
import {Form, type FormContext} from "vee-validate"
import AnswerField from "@/components/survey/AnswerField.vue"
import {type Answer, type Question, QuestionType, type Survey} from "@/lib"

interface Props {
  survey?: Survey | null
}

const props = defineProps<Props>()

const model = defineModel<Answer[]>({default: []})

const formRef = ref<FormContext | undefined>()
defineExpose({
  async validate() {
    const result = await formRef.value?.validate()
    return result?.valid ?? true
  },
})

const questions = computed<Question[]>(() => props.survey?.questions ?? [])
const nonDescriptions = computed(() => questions.value.filter(q => q.type !== QuestionType.DESCRIPTION))

function makeDefault(q: Question): Answer {
  const base: Answer = {questionId: q.id!}
  if (q.type === QuestionType.OPEN) base.textResponse = ""
  if (q.type === QuestionType.RADIO || q.type === QuestionType.CHECKBOX) {
    base.optionSelections = Array(q.choiceLabels?.length ?? 0).fill(false)
  }
  return base
}

function getAnswer(q: Question): Answer {
  const i = model.value.findIndex(a => a.questionId === q.id)
  return i >= 0 ? model.value[i]! : makeDefault(q)
}

function setAnswer(qId: number, v: Answer) {
  const i = model.value.findIndex(a => a.questionId === qId)
  const next = model.value.slice()
  if (i >= 0) next[i] = v
  else next.push(v)
  model.value = next
}

onMounted(() => {
  // ensure answers for all non-description questions
  const next = model.value.slice()
  for (const q of nonDescriptions.value) {
    if (!next.some(a => a.questionId === q.id)) next.push(makeDefault(q))
  }
  model.value = next
})

watch(
  () => questions.value.map(q => q.id),
  () => {
    if (!props.survey?.questions) return
    const allowedIds = new Set(questions.value.map(q => q.id))
    const next = model.value.slice()
    for (const q of nonDescriptions.value) {
      if (!next.some(a => a.questionId === q.id)) next.push(makeDefault(q))
    }
    model.value = next.filter(a => allowedIds.has(a.questionId))
  },
  {deep: true},
)
</script>

<template>
  <Form
    ref="formRef"
    as="div"
  >
    <div
      v-for="(q, i) in questions"
      :key="q?.id ?? q?.idx ?? i"
      class="mb-4"
    >
      <p
        v-if="q.type === QuestionType.DESCRIPTION"
        class="text-body-1"
      >
        {{ q.label }}
      </p>

      <template v-else>
        <p class="text-h6 mb-2">
          {{ q.label }}
        </p>

        <answer-field
          :model-value="getAnswer(q)"
          :name="`answers[${i}]`"
          :question="q"
          @update:model-value="(val: Answer) => setAnswer(q.id!, val)"
        />
      </template>
    </div>
  </Form>
</template>
