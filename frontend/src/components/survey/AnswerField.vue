<script lang="ts" setup>
import {Field} from "vee-validate"
import {computed, watchEffect} from "vue"
import {type Answer, type Question, QuestionType} from "@/lib"

interface Props {
  question: Question
  name: string
}

const props = defineProps<Props>()
const model = defineModel<Answer>({required: true})

const choiceCount = computed<number>(() => props.question.choiceLabels?.length ?? 0)

watchEffect(() => {
  if (!model.value) {
    model.value = {questionId: props.question.id!}
  } else if (model.value.questionId !== props.question.id) {
    model.value = {...model.value, questionId: props.question.id!}
  }

  if (props.question.type === QuestionType.RADIO || props.question.type === QuestionType.CHECKBOX) {
    const need = choiceCount.value
    const curr = Array.isArray(model.value.optionSelections) ? model.value.optionSelections.slice() : []
    while (curr.length < need) curr.push(false)
    if (curr.length > need) curr.length = need
    model.value = {...model.value, optionSelections: curr}
  }
})
</script>

<template>
  <!-- Description questions are rendered outside in SurveyForm; nothing to answer here -->
  <template v-if="question.type === QuestionType.OPEN">
    <Field
      v-slot="{ value, errors, handleChange, handleBlur }"
      :name="`${name}.textResponse`"
      rules="required"
      :model-value="model.textResponse ?? ''"
    >
      <v-text-field
        :model-value="value"
        :label="question.label || 'Answer'"
        :error-messages="errors"
        required
        @update:model-value="(v: string) => { model = { ...model, textResponse: v }; handleChange(v) }"
        @blur="handleBlur"
      />
    </Field>
  </template>

  <template v-else-if="question.type === QuestionType.RADIO || question.type === QuestionType.CHECKBOX">
    <Field
      v-slot="{ value = [], errors, handleChange, handleBlur }"
      :name="`${name}.optionSelections`"
      :model-value="model.optionSelections ?? []"
      :rules="(val: boolean[]) => (Array.isArray(val) && val.some(Boolean)) || 'Select at least one option'"
    >
      <div>
        <v-checkbox
          v-for="(opt, j) in (question.choiceLabels ?? [])"
          :key="j"
          :label="opt"
          :model-value="(value?.[j] ?? false)"
          :error-messages="errors"
          hide-details
          @update:model-value="(checked: boolean) => {
            const next = Array.isArray(value) ? value.slice() : Array((question.choiceLabels?.length ?? 0)).fill(false)

            if (question.type === QuestionType.RADIO) {
              for (let k = 0; k < next.length; k++) next[k] = false
              next[j] = checked
            } else {
              next[j] = checked
            }

            model = { ...model, optionSelections: next }
            handleChange(next)
          }"
          @blur="handleBlur"
        />
      </div>
    </Field>
  </template>
</template>

<style scoped lang="scss">
.v-checkbox .v-selection-control {
  min-height: 40px !important;
}
</style>
