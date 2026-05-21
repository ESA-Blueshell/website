<script lang="ts" setup>
import {Field} from "vee-validate"
import {computed, watch} from "vue"
import {type AnswerRequest, type QuestionResponse, QuestionType} from "@/services/api"

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

const requireText = (val: string | undefined | null) => {
  if (!required.value) return true
  return (typeof val === "string" && val.trim().length > 0) || "This field is required"
}

const requireAtLeastOneSelection = (selections: boolean[] | undefined | null) => {
  if (!required.value) return true
  const arr = Array.isArray(selections) ? selections : []
  return arr.some(Boolean) || "Select at least one option"
}

const requireExactlyOneSelection = (selections: boolean[] | undefined | null) => {
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
      <v-textarea
        class="answer-field__open"
        :error-messages="meta.touched ? errors : []"
        :model-value="value"
        placeholder="Your answer"
        :rows="1"
        auto-grow
        @blur="handleBlur"
        @update:model-value="(v: string) => handleChange(v)"
      />
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
      <v-radio-group
        class="answer-field__radio"
        :error-messages="meta.touched ? errors : []"
        :model-value="(() => {
          const i = (value ?? []).findIndex(Boolean)
          return i >= 0 ? i : null
        })()"
        hide-details="auto"
        @blur="handleBlur"
        @update:model-value="(idx: number) => {
          const arr = new Array(question.choiceLabels!.length).fill(false)
          if (idx != null && idx >= 0) arr[idx] = true
          handleChange(arr)
        }"
      >
        <v-radio
          v-for="(opt, j) in question.choiceLabels"
          :key="j"
          :label="opt"
          :value="j"
          class="answer-field__option"
        />
      </v-radio-group>
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
        <v-checkbox
          v-for="(opt, j) in question.choiceLabels"
          :key="j"
          :label="opt"
          :model-value="value?.[j] ?? false"
          hide-details
          density="comfortable"
          class="answer-field__option"
          @blur="handleBlur"
          @update:model-value="(checked: boolean) => {
            const arr = Array.isArray(value)
              ? [...value]
              : new Array(question.choiceLabels!.length).fill(false)
            arr[j] = checked
            handleChange(arr)
          }"
        />
        <div
          v-if="meta.touched && errors?.length"
          class="text-error text-caption mt-1 ms-1"
        >
          {{ errors[0] }}
        </div>
      </div>
    </Field>
  </template>
</template>

<style lang="scss" scoped>
.answer-field {
  &__radio,
  &__checkbox {
    margin-inline-start: 0.75rem;
  }

  &__option :deep(.v-selection-control) {
    min-height: 36px;
  }

  &__option :deep(.v-label) {
    opacity: 0.95;
    line-height: 1.4;
    white-space: pre-wrap;
    word-break: break-word;
  }

  &__checkbox {
    display: flex;
    flex-direction: column;
    gap: 0.1rem;
  }

  &__radio :deep(.v-input__details),
  &__checkbox + .text-error {
    padding-inline: 0;
  }
}
</style>
