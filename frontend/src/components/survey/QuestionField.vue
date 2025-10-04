<script setup lang="ts">
import { Field } from "vee-validate"
import { type Question, QuestionType } from "@/lib"

interface Props {
  canMoveUp?: boolean
  canMoveDown?: boolean
}
interface Emits {
  (e: "moveUp"): void;
  (e: "moveDown"): void;
  (e: "remove"): void;
}

const props = withDefaults(defineProps<Props>(), {
  canMoveUp: true,
  canMoveDown: true,
})
const emit = defineEmits<Emits>()
const model = defineModel<Question>({ required: true })

function setLabel(v: string) {
  model.value = { ...model.value, label: v }
}

function ensureChoices(): string[] {
  return model.value.choiceLabels ?? []
}

function addChoice() {
  const next = [...ensureChoices(), ""]
  model.value = { ...model.value, choiceLabels: next }
}

function moveChoiceUp(j: number) {
  const next = ensureChoices().slice()
  if (j <= 0) return
  [next[j - 1]!, next[j]!] = [next[j]!, next[j - 1]!]
  model.value = { ...model.value, choiceLabels: next }
}

function moveChoiceDown(j: number) {
  const next = ensureChoices().slice()
  if (j >= next.length - 1) return
  [next[j + 1]!, next[j]!] = [next[j]!, next[j + 1]!]
  model.value = { ...model.value, choiceLabels: next }
}

function removeChoice(j: number) {
  const next = ensureChoices().slice()
  if (next.length <= 2) return
  next.splice(j, 1)
  model.value = { ...model.value, choiceLabels: next }
}
</script>

<template>
  <Field
    v-slot="{ value, errors, handleChange, handleBlur }"
    :name="`survey.questions[${model.idx}].label`"
    rules="required"
    :model-value="model.label"
  >
    <v-text-field
      :model-value="value"
      :label="`${model.type === QuestionType.DESCRIPTION ? `Description ${model.idx+1}` : `Question ${model.idx+1}`}`"
      :error-messages="errors"
      required
      @update:model-value="(val: string) => { setLabel(val); handleChange(val) }"
      @blur="handleBlur"
    >
      <template #append>
        <v-tooltip
          v-if="model.type === QuestionType.RADIO || model.type === QuestionType.CHECKBOX"
          location="top"
          text="Add option"
        >
          <template #activator="{ props: tip }">
            <v-btn
              icon="mdi-plus"
              v-bind="tip"
              variant="plain"
              @click="addChoice()"
            />
          </template>
        </v-tooltip>

        <v-btn
          :disabled="!props.canMoveDown"
          icon="mdi-chevron-down"
          variant="plain"
          @click="emit('moveDown')"
        />
        <v-btn
          :disabled="!props.canMoveUp"
          icon="mdi-chevron-up"
          variant="plain"
          @click="emit('moveUp')"
        />
        <v-btn
          icon="mdi-close"
          variant="plain"
          @click="emit('remove')"
        />
      </template>
    </v-text-field>
  </Field>

  <!-- OPTIONS with Field controlling the value -->
  <template v-if="model.type === QuestionType.RADIO || model.type === QuestionType.CHECKBOX">
    <template
      v-for="(lbl, j) in (model.choiceLabels ?? [])"
      :key="j"
    >
      <Field
        v-slot="{ value, errors, handleChange, handleBlur }"
        :name="`survey.questions[${model.idx}].choiceLabels[${j}]`"
        rules="required"
        :initial-value="lbl"
      >
        <v-text-field
          :model-value="value"
          :label="`Option ${j+1}`"
          :prepend-icon="model.type===QuestionType.RADIO ? 'mdi-radiobox-marked' : 'mdi-checkbox-marked'"
          density="compact"
          :error-messages="errors"
          required
          @update:model-value="(val: string) => {
            const next = (model.choiceLabels ?? []).slice()
            next[j] = val
            model = { ...model, choiceLabels: next }
            handleChange(val)
          }"
          @blur="handleBlur"
        >
          <template #append>
            <v-btn
              :disabled="j === (model.choiceLabels?.length ?? 0) - 1"
              icon="mdi-chevron-down"
              variant="plain"
              @click="moveChoiceDown(j)"
            />
            <v-btn
              :disabled="j === 0"
              icon="mdi-chevron-up"
              variant="plain"
              @click="moveChoiceUp(j)"
            />
            <v-btn
              :disabled="(model.choiceLabels?.length ?? 0) <= 2"
              icon="mdi-close"
              variant="plain"
              @click="removeChoice(j)"
            />
          </template>
        </v-text-field>
      </Field>
    </template>
  </template>
</template>
