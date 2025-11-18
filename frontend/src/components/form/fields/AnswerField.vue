<script lang="ts" setup>
import {Field} from "vee-validate"
import {watch} from "vue"
import {type Answer, type Question, QuestionType} from "@/services/api"

const props = withDefaults(
  defineProps<{
    question: Question
  }>(),
  {},
)

const answer = defineModel<Answer>({
  default: () => ({questionId: undefined, textResponse: "", optionSelections: []}),
})

const requireText = (val: string | undefined | null) =>
  (typeof val === "string" && val.trim().length > 0) || "This field is required"

const requireOptionSelection = (selections: boolean[] | undefined | null) => {
  const arr = Array.isArray(selections) ? selections : []
  return arr.some(Boolean) || "Select at least one option"
}

watch(
  () => props.question,
  (q) => {
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
      <v-text-field
        :label="question.label || 'Answer'"
        :error-messages="meta.touched ? errors : []"
        :model-value="value"
        required
        @blur="handleBlur"
        @update:model-value="(v: string) => handleChange(v)"
      />
    </Field>
  </template>

  <template v-else-if="question.type === QuestionType.RADIO || question.type === QuestionType.CHECKBOX">
    <Field
      v-slot="{ value, errors, handleChange, handleBlur, meta }"
      v-model="answer.optionSelections"
      :name="`${question.idx}.optionSelections`"
      :rules="requireOptionSelection"
      :validate-on-mount="false"
    >
      <div>
        <template v-if="question.type === QuestionType.RADIO">
          <v-radio-group
            :error-messages="meta.touched ? errors : []"
            :model-value="(() => {
              const i = (value ?? []).findIndex(Boolean)
              return i >= 0 ? i : null
            })()"
            @blur="handleBlur"
            @update:model-value="(idx: number) => {
              const arr = new Array(question.choiceLabels!.length).fill(false)
              if (idx >= 0) arr[idx] = true
              handleChange(arr)
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
            :model-value="value?.[j]"
            hide-details
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
            class="text-error text-caption mt-1"
          >
            {{ errors[0] }}
          </div>
        </template>
      </div>
    </Field>
  </template>
</template>

<style lang="scss" scoped>
.v-checkbox .v-selection-control {
  min-height: 40px !important;
}
</style>
