<script lang="ts" setup>
import {Field} from "vee-validate"
import {computed, ref, watch, watchEffect} from "vue"
import {type Answer, type Question, QuestionType} from "@/services/api"

interface Props {
  question: Question
  name: string
  modelValue?: Answer | null
}

const props = defineProps<Props>()
const emit = defineEmits<{ (e: "update:modelValue", v: Answer): void }>()

const choiceCount = computed(() => props.question.choiceLabels?.length ?? 0)
const local = ref<Answer>({questionId: props.question.id!})

function normalizeForQuestion(a: Answer): Answer {
  let next: Answer = {...a}

  if (props.question.type === QuestionType.OPEN) {
    if (typeof next.textResponse !== "string") next.textResponse = ""
    delete next.optionSelections
  }

  if (props.question.type === QuestionType.RADIO || props.question.type === QuestionType.CHECKBOX) {
    const need = choiceCount.value
    const curr = Array.isArray(next.optionSelections) ? next.optionSelections.slice() : []
    while (curr.length < need) curr.push(false)
    if (curr.length > need) curr.length = need
    next.optionSelections = curr
    delete next.textResponse
  }

  return next
}

watch(
  () => props.modelValue,
  (v) => {
    local.value = normalizeForQuestion(v ?? {questionId: props.question.id!})
  },
  {immediate: true, deep: true},
)

watchEffect(() => {
  if (local.value.questionId !== props.question.id) {
    local.value = normalizeForQuestion({...local.value, questionId: props.question.id!})
  }
})

function updateAndEmit(next: Answer) {
  local.value = normalizeForQuestion(next)
  emit("update:modelValue", local.value)
}
</script>

<template>
  <template v-if="question.type === QuestionType.OPEN">
    <Field
      v-slot="{ value, errors, handleChange, handleBlur }"
      :model-value="local.textResponse ?? ''"
      :name="`${name}.textResponse`"
      rules="required"
    >
      <v-text-field
        :error-messages="errors"
        :label="question.label || 'Answer'"
        :model-value="value"
        required
        @blur="handleBlur"
        @update:model-value="(v: string) => { updateAndEmit({ ...local, textResponse: v }); handleChange(v) }"
      />
    </Field>
  </template>

  <template v-else-if="question.type === QuestionType.RADIO || question.type === QuestionType.CHECKBOX">
    <Field
      v-slot="{ value = [], errors, handleChange, handleBlur }"
      :model-value="local.optionSelections ?? []"
      :name="`${name}.optionSelections`"
      :rules="(val: boolean[]) => (Array.isArray(val) && val.some(Boolean)) || 'Select at least one option'"
    >
      <div>
        <template v-if="question.type === QuestionType.RADIO">
          <v-radio-group
            :error-messages="errors"
            :model-value="value.findIndex(Boolean)"
            @blur="handleBlur"
            @update:model-value="(idx: number) => {
              const next = Array(choiceCount).fill(false)
              next[idx] = true
              updateAndEmit({ ...local, optionSelections: next })
              handleChange(next)
            }"
          >
            <v-radio
              v-for="(opt, j) in (question.choiceLabels ?? [])"
              :key="j"
              :label="opt"
              :value="j"
            />
          </v-radio-group>
        </template>

        <template v-else>
          <v-checkbox
            v-for="(opt, j) in (question.choiceLabels ?? [])"
            :key="j"
            :label="opt"
            :model-value="(value?.[j] ?? false)"
            hide-details
            @blur="handleBlur"
            @update:model-value="(checked: boolean) => {
              const next = Array.isArray(value) ? value.slice() : Array(choiceCount).fill(false)
              next[j] = checked
              updateAndEmit({ ...local, optionSelections: next })
              handleChange(next)
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

<style lang="scss" scoped>
.v-checkbox .v-selection-control {
  min-height: 40px !important;
}
</style>
