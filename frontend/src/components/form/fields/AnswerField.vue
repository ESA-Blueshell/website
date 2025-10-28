<template>
  <template v-if="question.type === QuestionType.OPEN">
    <Field
      v-slot="{ value, errors, handleChange, handleBlur }"
      v-model="answer.textResponse"
      :name="`${question.idx}.textResponse`"
      rules="required"
    >
      <v-text-field
        :label="question.label || 'Answer'"
        :error-messages="errors"
        :model-value="value"
        required
        @blur="handleBlur"
        @update:model-value="(v: string) => {
          handleChange(v)
        }"
      />
    </Field>
  </template>

  <template v-else-if="question.type === QuestionType.RADIO || question.type === QuestionType.CHECKBOX">
    <Field
      v-slot="{ errors, handleChange, handleBlur }"
      v-model="answer"
      :name="`${question.idx}.optionSelections`"
      :rules="(a: Answer) => (Array.isArray(a.optionSelections) && a.optionSelections.some(Boolean)) || 'Select at least one option'"
    >
      <div>
        <template v-if="question.type === QuestionType.RADIO">
          <v-radio-group
            :error-messages="errors"
            :model-value="(() => {
              const i = answer.optionSelections?.findIndex(Boolean) ?? -1
              return i >= 0 ? i : null
            })()"
            @blur="handleBlur"
            @update:model-value="(idx: number) => {
              answer.optionSelections = new Array(question.choiceLabels!.length).fill(false)
              answer.optionSelections[idx] = true
              handleChange(answer)
            }"
          >
            <v-radio
              v-for="(opt, j) in question.choiceLabels"
              :key="j"
              :label="opt"
              :value="j"
            />
          </v-radio-group>
        </template>

        <template v-else>
          <v-checkbox
            v-for="(opt, j) in question.choiceLabels"
            :key="j"
            :label="opt"
            :model-value="answer.optionSelections[j]"
            hide-details
            @blur="handleBlur"
            @update:model-value="(checked: boolean) => {
              answer.optionSelections[j] = checked
              handleChange(answer)
            }"
          />
          <div
            v-if="errors?.length"
            class="text-error text-caption mt-1"
          >
            {{ errors[0] }}
          </div>
        </template>
      </div>
    </Field>
  </template>
</template>

<script lang="ts" setup>
import {Field} from "vee-validate"
import {computed} from "vue"
import {type Answer, type Question, QuestionType} from "@/services/api"


const props = withDefaults(defineProps<{
  question: Question
}>(), {})

const answers = defineModel<Answer[]>("answers", {default: []})
const question = computed<Question>(() => props.question)

const answer = computed<Answer>(() => {
  const previousAnswer = answers.value.find((a: Answer) => a.questionId == question.value.id)
  if (previousAnswer) return previousAnswer

  if (question.value.type === QuestionType.OPEN) {
    return {
      questionId: question.value.id,
      textResponse: "",
    } as Answer
  } else if (question.value.type === QuestionType.RADIO || question.value.type === QuestionType.CHECKBOX) {
    return {
      questionId: question.value.id,
      optionSelections: new Array(question.value.choiceLabels!.length).fill(false),
    } as Answer
  } else {
    return {
      questionId: question.value.id,
    } as Answer
  }
})
</script>

<style lang="scss" scoped>
.v-checkbox .v-selection-control {
  min-height: 40px !important;
}
</style>
