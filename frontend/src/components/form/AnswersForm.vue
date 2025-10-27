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
        <p
          v-if="q.type === QuestionType.RADIO || q.type === QuestionType.CHECKBOX"
          class="text-h6 mb-2"
        >
          {{ q.label }}
        </p>
        <answer-field
          :model-value="getAnswer(q)"
          :name="`answersById[${q.id}]`"
          :question="q"
          @update:model-value="(v: Answer) => setAnswer(q, v)"
        />
      </template>
    </div>
  </Form>
</template>

<script lang="ts" setup>
import {computed, onMounted, ref, watch} from "vue"
import {Form, type FormContext} from "vee-validate"
import AnswerField from "@/components/form/fields/AnswerField.vue"
import {type Answer, type Question, QuestionType, type Survey} from "@/services/api"

const props = defineProps<{ survey?: Survey | null }>()
const model = defineModel<Answer[]>({ default: [] })  // v-model from parent

const formRef = ref<FormContext | undefined>()

async function validate() {
  const result = await formRef.value?.validate()
  return result?.valid ?? true
}

const questions = computed<Question[]>(() => props.survey?.questions ?? [])
const nonDescriptions = computed(() => questions.value.filter(q => q.type !== QuestionType.DESCRIPTION))

function makeDefault(q: Question): Answer {
  const base: Answer = { questionId: q.id! }
  if (q.type === QuestionType.OPEN) base.textResponse = ""
  if (q.type === QuestionType.RADIO || q.type === QuestionType.CHECKBOX) {
    base.optionSelections = new Array(q.choiceLabels?.length ?? 0).fill(false)
  }
  return base
}

function getAnswer(q: Question): Answer {
  const existing = model.value.find(a => a.questionId === q.id)
  if (existing) return normalizeForQuestion(q, existing)
  return makeDefault(q)
}

function setAnswer(q: Question, next: Answer) {
  const normalized = normalizeForQuestion(q, { ...next, questionId: q.id! })
  const idx = model.value.findIndex(a => a.questionId === q.id)
  if (idx >= 0) model.value.splice(idx, 1, normalized)
  else model.value.splice(model.value.length, 0, normalized)
}

function normalizeForQuestion(q: Question, a: Answer): Answer {
  const next: Answer = { ...a, questionId: q.id! }
  if (q.type === QuestionType.OPEN) {
    next.textResponse = typeof next.textResponse === "string" ? next.textResponse : ""
    delete next.optionSelections
  } else if (q.type === QuestionType.RADIO || q.type === QuestionType.CHECKBOX) {
    const need = q.choiceLabels?.length ?? 0
    const curr = Array.isArray(next.optionSelections) ? next.optionSelections.slice() : []
    while (curr.length < need) curr.push(false)
    if (curr.length > need) curr.length = need
    next.optionSelections = curr
    delete next.textResponse
  }
  return next
}

// Seed defaults for any missing answers on mount
onMounted(() => {
  const next = model.value.slice()
  for (const q of nonDescriptions.value) {
    if (!next.some(a => a.questionId === q.id)) next.push(makeDefault(q))
  }
  model.value = next
})

// Keep model in sync if survey questions change (add/remove/resize)
watch(
  () => questions.value.map(q => q.id),
  () => {
    if (!props.survey?.questions) return
    const allowedIds = new Set(questions.value.map(q => q.id))
    const next = model.value.slice()
    for (const q of nonDescriptions.value) {
      const existing = next.find(a => a.questionId === q.id)
      if (!existing) next.push(makeDefault(q))
      else {
        // reshape existing answers if option counts changed
        const idx = next.findIndex(a => a.questionId === q.id)
        next.splice(idx, 1, normalizeForQuestion(q, existing))
      }
    }
    model.value = next.filter(a => allowedIds.has(a.questionId))
  },
  { deep: true },
)

defineExpose({ validate })
</script>

