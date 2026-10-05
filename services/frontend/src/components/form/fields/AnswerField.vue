<script lang="ts" setup>
import {Field} from "vee-validate"
import CheckBox from "@/components/island/CheckBox.vue"
import RadioGroup from "@/components/island/RadioGroup.vue"
import TextArea from "@/components/island/TextArea.vue"
import {computed, watch} from "vue"
import {type AnswerRequest, type QuestionResponse, QuestionType} from "@/domains/events"

const props = withDefaults(
  defineProps<{
    question: QuestionResponse
  }>(),
  {},
)

const answer = defineModel<AnswerRequest>({
  default: () => ({questionId: 0, textResponse: "", optionSelections: []}),
})

const required = computed(() => props.question.required === true)

const requireText = (val: unknown) => {
  if (!required.value) return true
  return (typeof val === "string" && val.trim().length > 0) || "This field is required"
}

const requireAtLeastOneSelection = (selections: unknown) => {
  if (!required.value) return true
  const arr = Array.isArray(selections) ? selections : []
  return arr.some(Boolean) || "Select at least one option"
}

const requireExactlyOneSelection = (selections: unknown) => {
  const arr = Array.isArray(selections) ? selections : []
  const chosen = arr.filter(Boolean).length
  if (chosen > 1) return "Select exactly one option"
  if (required.value && chosen === 0) return "Select one option"
  return true
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
    <Field
      v-slot="{ value, errors, handleChange, handleBlur, meta }"
      v-model="answer.textResponse"
      :name="`${question.idx}.textResponse`"
      :rules="requireText"
      :validate-on-mount="false"
    >
      <text-area
        class="answer-field__open"
        :invalid="meta.touched && errors.length > 0"
        :model-value="value ?? ''"
        placeholder="Your answer"
        :rows="2"
        @blur="handleBlur"
        @update:model-value="(v: string) => handleChange(v)"
      />
      <p
        v-if="meta.touched && errors.length"
        class="answer-field__said"
      >
        {{ errors[0] }}
      </p>
    </Field>
  </template>

  <template v-else-if="question.type === QuestionType.RADIO">
    <Field
      v-slot="{ value, errors, handleChange, handleBlur, meta }"
      v-model="answer.optionSelections"
      :name="`${question.idx}.optionSelections`"
      :rules="requireExactlyOneSelection"
      :validate-on-mount="false"
    >
      <radio-group
        class="answer-field__radio"
        :model-value="(() => {
          const i = (value ?? []).findIndex(Boolean)
          return i >= 0 ? String(i) : null
        })()"
        :name="`answer-${question.idx}`"
        :options="(question.choiceLabels ?? []).map((label, j) => ({key: String(j), label}))"
        @focusout="handleBlur"
        @update:model-value="(key: string | null) => {
          const arr = new Array(question.choiceLabels!.length).fill(false)
          if (key !== null) arr[Number(key)] = true
          handleChange(arr)
        }"
      />
      <p
        v-if="meta.touched && errors.length"
        class="answer-field__said"
      >
        {{ errors[0] }}
      </p>
    </Field>
  </template>

  <template v-else-if="question.type === QuestionType.CHECKBOX">
    <Field
      v-slot="{ value, errors, handleChange, handleBlur, meta }"
      v-model="answer.optionSelections"
      :name="`${question.idx}.optionSelections`"
      :rules="requireAtLeastOneSelection"
      :validate-on-mount="false"
    >
      <div class="answer-field__checkbox">
        <check-box
          v-for="(opt, j) in question.choiceLabels"
          :key="j"
          :label="opt"
          :model-value="value?.[j] ?? false"
          @focusout="handleBlur"
          @update:model-value="(checked: boolean) => {
            const arr = Array.isArray(value)
              ? [...value]
              : new Array(question.choiceLabels!.length).fill(false)
            arr[j] = checked
            handleChange(arr)
          }"
        />
        <p
          v-if="meta.touched && errors?.length"
          class="answer-field__said"
        >
          {{ errors[0] }}
        </p>
      </div>
    </Field>
  </template>
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
