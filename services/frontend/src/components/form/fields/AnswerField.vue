<script lang="ts" setup>
import CheckBox from "@/components/island/CheckBox.vue"
import RadioGroup from "@/components/island/RadioGroup.vue"
import TextArea from "@/components/island/TextArea.vue"
import {computed, reactive, watch} from "vue"
import {type AnswerRequest, type QuestionResponse, QuestionType} from "@/domains/events"

const props = withDefaults(
  defineProps<{
    question: QuestionResponse
    /** What the form's checks say of this answer. */
    errorMessages?: string[]
  }>(),
  {errorMessages: () => []},
)

const emit = defineEmits<{blur: []}>()

// A default handed to an unbound v-model stays raw, so its checks would never see an edit.
const answer = defineModel<AnswerRequest>({
  default: () => reactive({questionId: 0, textResponse: "", optionSelections: []}),
})

const said = computed<string | undefined>(() => props.errorMessages[0])
const blank = (): boolean[] => new Array(props.question.choiceLabels!.length).fill(false)
const picked = computed<string | null>(() => {
  const i = (answer.value.optionSelections ?? []).findIndex(Boolean)
  return i >= 0 ? String(i) : null
})

const pick = (key: string | null) => {
  const next = blank()
  if (key !== null) next[Number(key)] = true
  answer.value.optionSelections = next
}

const tick = (j: number, checked: boolean) => {
  const next = Array.isArray(answer.value.optionSelections) ? [...answer.value.optionSelections] : blank()
  next[j] = checked
  answer.value.optionSelections = next
}

watch(
  () => props.question,
  (q) => {
    if (!answer.value.questionId || answer.value.questionId <= 0) {
      if (q.type === QuestionType.OPEN) {
        answer.value = {
          questionId: q.id,
          textResponse: "",
        }
      } else if (q.type === QuestionType.RADIO || q.type === QuestionType.CHECKBOX) {
        answer.value = {
          questionId: q.id,
          optionSelections: new Array(q.choiceLabels!.length).fill(false),
        }
      } else {
        answer.value = {
          questionId: q.id,
        }
      }
    }
  },
  {immediate: true},
)
</script>

<template v-if="answer">
  <template v-if="question.type === QuestionType.OPEN">
    <text-area
      class="answer-field__open"
      :invalid="!!said"
      :model-value="answer.textResponse ?? ''"
      placeholder="Your answer"
      :rows="2"
      @blur="emit('blur')"
      @update:model-value="(typed: string) => (answer.textResponse = typed)"
    />
  </template>

  <template v-else-if="question.type === QuestionType.RADIO">
    <radio-group
      class="answer-field__radio"
      :model-value="picked"
      :name="`answer-${question.idx}`"
      :options="(question.choiceLabels ?? []).map((label, j) => ({key: String(j), label}))"
      @focusout="emit('blur')"
      @update:model-value="pick"
    />
  </template>

  <template v-else-if="question.type === QuestionType.CHECKBOX">
    <div class="answer-field__checkbox">
      <check-box
        v-for="(opt, j) in question.choiceLabels"
        :key="j"
        :label="opt"
        :model-value="answer.optionSelections?.[j] ?? false"
        @focusout="emit('blur')"
        @update:model-value="(checked: boolean) => tick(j, checked)"
      />
    </div>
  </template>

  <p
    v-if="said"
    class="answer-field__said"
  >
    {{ said }}
  </p>
</template>

<style lang="scss" scoped>
.answer-field__checkbox {
  display: flex;
  flex-direction: column;
  gap: 0.45rem;
}

.answer-field__said {
  margin: 0.3rem 0 0;
  font-size: 0.8rem;
  color: var(--color-danger);
}
</style>
