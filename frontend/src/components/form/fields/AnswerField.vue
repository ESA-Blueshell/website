<template v-if="answer">
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
import {watch} from "vue"
import {type Answer, type Question, QuestionType} from "@/services/api"


const props = withDefaults(defineProps<{
  question: Question
}>(), {})

const answer = defineModel<Answer>({
  default: () => ({questionId: undefined, textResponse: "", optionSelections: []}),
})

watch(() => props.question, (q) => {
  if (!answer.value.questionId) {
    if (q.type === QuestionType.OPEN) {
      answer.value = {
        questionId: q.id,
        textResponse: "",
      } as Answer
    } else if (q.type === QuestionType.RADIO || q.type === QuestionType.CHECKBOX) {
      answer.value = {
        questionId: q.id,
        optionSelections: new Array(q.choiceLabels!.length).fill(false),
      } as Answer
    } else {
      answer.value = {
        questionId: q.id,
      } as Answer
    }
  }
}, {immediate: true})
</script>

<style lang="scss" scoped>
.v-checkbox .v-selection-control {
  min-height: 40px !important;
}
</style>
