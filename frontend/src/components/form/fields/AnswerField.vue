<template>
  <template v-if="question.type === QuestionType.OPEN">
    <Field
      v-slot="{ value, errors, handleChange, handleBlur }"
      :name="`${name}.textResponse`"
      rules="required"
      :initial-value="normalized.textResponse ?? ''"
    >
      <v-text-field
        :label="question.label || 'Answer'"
        :error-messages="errors"
        :model-value="value"
        required
        @blur="handleBlur"
        @update:model-value="(v: string) => {
          handleChange(v)
          normalized = { ...normalized, textResponse: v }
        }"
      />
    </Field>
  </template>

  <template v-else-if="question.type === QuestionType.RADIO || question.type === QuestionType.CHECKBOX">
    <Field
      v-slot="{ errors, handleChange, handleBlur }"
      :name="`${name}.optionSelections`"
      :rules="(val: boolean[]) => (Array.isArray(val) && val.some(Boolean)) || 'Select at least one option'"
      :initial-value="normalized.optionSelections ?? Array(choiceCount).fill(false)"
    >
      <div>
        <template v-if="question.type === QuestionType.RADIO">
          <v-radio-group
            :error-messages="errors"
            :model-value="selectedIndex"
            @blur="handleBlur"
            @update:model-value="(idx: number | null) => {
              const next = makeOneHot(choiceCount, idx ?? -1)
              handleChange(next)
              normalized = { ...normalized, optionSelections: next }
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
            :model-value="normalized.optionSelections?.[j] ?? false"
            hide-details
            @blur="handleBlur"
            @update:model-value="(checked: boolean) => {
              const next = (normalized.optionSelections ?? Array(choiceCount).fill(false)).slice()
              next[j] = checked
              handleChange(next)
              normalized = { ...normalized, optionSelections: next }
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

const props = defineProps<{ question: Question; name: string }>()
const model = defineModel<Answer>({required: true}) // v-model from parent

const choiceCount = computed(() => props.question.choiceLabels?.length ?? 0)

function normalizeForQuestion(a: Answer): Answer {
  const next: Answer = {...a, questionId: props.question.id!}
  if (props.question.type === QuestionType.OPEN) {
    if (typeof next.textResponse !== "string") next.textResponse = ""
    delete next.optionSelections
  } else if (props.question.type === QuestionType.RADIO || props.question.type === QuestionType.CHECKBOX) {
    const need = choiceCount.value
    const curr = Array.isArray(next.optionSelections) ? next.optionSelections.slice() : []
    while (curr.length < need) curr.push(false)
    if (curr.length > need) curr.length = need
    next.optionSelections = curr
    delete next.textResponse
  }
  return next
}

const normalized = computed<Answer>({
  get: () => normalizeForQuestion(model.value ?? {questionId: props.question.id!}),
  set: (next) => {
    model.value = normalizeForQuestion(next)
  },
})

const selectedIndex = computed<number | null>({
  get: () => {
    const arr = normalized.value.optionSelections ?? []
    const idx = arr.findIndex(Boolean)
    return idx >= 0 ? idx : null
  },
  set: (idx) => {
    const next = makeOneHot(choiceCount.value, typeof idx === "number" ? idx : -1)
    normalized.value = {...normalized.value, optionSelections: next}
  },
})

function makeOneHot(n: number, idx: number): boolean[] {
  const arr = Array(n).fill(false) as boolean[]
  if (idx >= 0 && idx < n) arr[idx] = true
  return arr
}
</script>

<style lang="scss" scoped>
.v-checkbox .v-selection-control {
  min-height: 40px !important;
}
</style>
