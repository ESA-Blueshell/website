<script lang="ts" setup>
import {computed, onMounted, ref, watch} from "vue"
import {Form, type FormContext} from "vee-validate"
import AnswerField from "@/components/survey/AnswerField.vue"
import {type Answer, type Question, QuestionType, type Survey} from "@/lib"

interface Props {
  survey?: Survey | null
}

const props = defineProps<Props>()
const model = defineModel<Answer[]>({default: []}) // v-model from parent

const formRef = ref<FormContext | undefined>()
defineExpose({
  async validate() {
    const result = await formRef.value?.validate()
    return result?.valid ?? true
  },
})

const nonDescriptionQuestions = computed<Question[]>(() =>
  (props.survey?.questions ?? []).filter(q => q.type !== QuestionType.DESCRIPTION),
)

/**
 * Ensure we have an Answer entry for a given questionId.
 * If not present, we push a default-structured answer.
 */
function ensureAnswerFor(q: Question): Answer {
  const i = model.value.findIndex(a => a.questionId === q.id)
  if (i >= 0) return model.value[i]!

  const base: Answer = {questionId: q.id!}
  if (q.type === QuestionType.OPEN) {
    base.textResponse = ""
  }
  if (q.type === QuestionType.RADIO || q.type === QuestionType.CHECKBOX) {
    base.optionSelections = Array(q.choiceLabels?.length ?? 0).fill(false)
  }

  model.value = [...model.value, base]
  return base
}

onMounted(() => {
  // Create answers for all non-description questions if missing
  for (const q of nonDescriptionQuestions.value) ensureAnswerFor(q)
})

// Keep answers aligned if survey changes (e.g., different questions)
watch(
  () => props.survey?.questions,
  () => {
    if (!props.survey?.questions) return
    // Add missing
    for (const q of nonDescriptionQuestions.value) ensureAnswerFor(q)
    // Remove answers that no longer match any question
    const allowedIds = new Set((props.survey?.questions ?? []).map(q => q.id))
    model.value = model.value.filter(a => allowedIds.has(a.questionId))
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
      v-for="(q, i) in (props.survey?.questions ?? [])"
      :key="q.id ?? q.idx"
      class="mb-4"
    >
      <!-- Description block (no answer input) -->
      <p
        v-if="q.type === QuestionType.DESCRIPTION"
        class="text-body-1"
      >
        {{ q.label }}
      </p>

      <!-- Answerable questions -->
      <template v-else>
        <p class="text-h6 mb-2">
          {{ q.label }}
        </p>
        <answer-field
          v-model="ensureAnswerFor(q)"
          :question="q"
          :name="`answers[${i}]`"
        />
      </template>
    </div>
  </Form>
</template>
