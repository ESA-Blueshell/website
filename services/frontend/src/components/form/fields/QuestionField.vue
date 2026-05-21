<script lang="ts" setup>
import {Field} from "vee-validate"
import {computed} from "vue"
import {type QuestionRequest, QuestionType} from "@/services/api"

const props = withDefaults(defineProps<{ canMoveUp?: boolean; canMoveDown?: boolean }>(), {
  canMoveUp: true,
  canMoveDown: true,
})
const emit = defineEmits<{ (e: "moveUp"): void; (e: "moveDown"): void; (e: "remove"): void }>()
const model = defineModel<QuestionRequest>({required: true})

const isDescription = computed(() => model.value.type === QuestionType.DESCRIPTION)
const isChoice = computed(
  () => model.value.type === QuestionType.RADIO || model.value.type === QuestionType.CHECKBOX,
)
const typeLabel = computed(() => {
  switch (model.value.type) {
    case QuestionType.OPEN: return "Open question"
    case QuestionType.RADIO: return "Multiple choice"
    case QuestionType.CHECKBOX: return "Checkboxes"
    case QuestionType.DESCRIPTION: return "Description"
    default: return "Question"
  }
})
const typeIcon = computed(() => {
  switch (model.value.type) {
    case QuestionType.OPEN: return "mdi-form-textbox"
    case QuestionType.RADIO: return "mdi-radiobox-marked"
    case QuestionType.CHECKBOX: return "mdi-checkbox-marked-outline"
    case QuestionType.DESCRIPTION: return "mdi-text"
    default: return "mdi-help-circle-outline"
  }
})

function setLabel(v: string) {
  model.value = {...model.value, label: v}
}

function setRequired(v: boolean) {
  model.value = {...model.value, required: v}
}

function ensureChoices(): string[] {
  return model.value.choiceLabels ?? []
}

function setChoiceLabel(j: number, v: string) {
  const next = ensureChoices().slice()
  next[j] = v
  model.value = {...model.value, choiceLabels: next}
}

function addChoice() {
  model.value = {...model.value, choiceLabels: [...ensureChoices(), ""]}
}

function moveChoiceUp(j: number) {
  const next = ensureChoices().slice()
  if (j <= 0) return
  ;[next[j - 1]!, next[j]!] = [next[j]!, next[j - 1]!]
  model.value = {...model.value, choiceLabels: next}
}

function moveChoiceDown(j: number) {
  const next = ensureChoices().slice()
  if (j >= next.length - 1) return
  ;[next[j + 1]!, next[j]!] = [next[j]!, next[j + 1]!]
  model.value = {...model.value, choiceLabels: next}
}

function removeChoice(j: number) {
  const next = ensureChoices().slice()
  if (next.length <= 2) return
  next.splice(j, 1)
  model.value = {...model.value, choiceLabels: next}
}
</script>

<template>
  <div class="question-editor">
    <div class="question-editor__header">
      <v-icon
        :icon="typeIcon"
        size="small"
        class="me-1"
      />
      <span class="question-editor__type">
        {{ isDescription ? `${model.idx + 1} Description` : `${model.idx + 1} Question · ${typeLabel}` }}
      </span>

      <div class="question-editor__actions">
        <v-tooltip
          v-if="!isDescription"
          location="top"
          :text="model.required ? 'Click to make optional' : 'Click to require an answer'"
        >
          <template #activator="{ props: tip }">
            <v-btn
              v-bind="tip"
              :prepend-icon="model.required ? 'mdi-asterisk' : 'mdi-asterisk-circle-outline'"
              :color="model.required ? 'error' : undefined"
              variant="plain"
              size="small"
              @click="setRequired(!model.required)"
            >
              {{ model.required ? 'Required' : 'Optional' }}
            </v-btn>
          </template>
        </v-tooltip>

        <v-tooltip
          location="top"
          text="Move question up"
        >
          <template #activator="{ props: tip }">
            <v-btn
              v-bind="tip"
              :disabled="!props.canMoveUp"
              icon="mdi-chevron-up"
              variant="plain"
              @click="emit('moveUp')"
            />
          </template>
        </v-tooltip>
        <v-tooltip
          location="top"
          text="Move question down"
        >
          <template #activator="{ props: tip }">
            <v-btn
              v-bind="tip"
              :disabled="!props.canMoveDown"
              icon="mdi-chevron-down"
              variant="plain"
              @click="emit('moveDown')"
            />
          </template>
        </v-tooltip>
        <v-tooltip
          location="top"
          :text="isDescription ? 'Delete description' : 'Delete question'"
        >
          <template #activator="{ props: tip }">
            <v-btn
              v-bind="tip"
              icon="mdi-trash-can-outline"
              variant="plain"
              @click="emit('remove')"
            />
          </template>
        </v-tooltip>
      </div>
    </div>

    <Field
      v-slot="{ value, errors, handleChange, handleBlur }"
      :model-value="model.label"
      :name="`survey.questions[${model.idx}].label`"
      rules="required"
    >
      <v-textarea
        :error-messages="errors"
        :label="isDescription ? 'Description text' : 'Question text'"
        :model-value="value"
        :rows="isDescription ? 2 : 1"
        auto-grow
        required
        @blur="handleBlur"
        @update:model-value="(val: string) => { setLabel(val); handleChange(val) }"
      />
    </Field>

    <div
      v-if="isChoice"
      class="question-editor__choices"
    >
      <template
        v-for="(_choiceLabel, j) in model.choiceLabels ?? []"
        :key="j"
      >
        <Field
          v-slot="{ value, errors, handleChange, handleBlur }"
          :model-value="model.choiceLabels?.[j] ?? ''"
          :name="`survey.questions[${model.idx}].choiceLabels[${j}]`"
          rules="required|maxChars:100"
        >
          <v-text-field
            :error-messages="errors"
            :label="`Option ${j + 1}`"
            :model-value="value"
            :prepend-icon="model.type === QuestionType.RADIO ? 'mdi-radiobox-blank' : 'mdi-checkbox-blank-outline'"
            required
            @blur="handleBlur"
            @update:model-value="(val: string) => { setChoiceLabel(j, val); handleChange(val) }"
          >
            <template #append>
              <v-tooltip
                location="top"
                text="Move option up"
              >
                <template #activator="{ props: tip }">
                  <v-btn
                    v-bind="tip"
                    :disabled="j === 0"
                    icon="mdi-chevron-up"
                    variant="plain"
                    @click="moveChoiceUp(j)"
                  />
                </template>
              </v-tooltip>
              <v-tooltip
                location="top"
                text="Move option down"
              >
                <template #activator="{ props: tip }">
                  <v-btn
                    v-bind="tip"
                    :disabled="j === (model.choiceLabels?.length ?? 0) - 1"
                    icon="mdi-chevron-down"
                    variant="plain"
                    @click="moveChoiceDown(j)"
                  />
                </template>
              </v-tooltip>
              <v-tooltip
                location="top"
                :text="(model.choiceLabels?.length ?? 0) <= 2 ? 'At least two options are required' : 'Delete option'"
              >
                <template #activator="{ props: tip }">
                  <v-btn
                    v-bind="tip"
                    :disabled="(model.choiceLabels?.length ?? 0) <= 2"
                    icon="mdi-trash-can-outline"
                    variant="plain"
                    @click="removeChoice(j)"
                  />
                </template>
              </v-tooltip>
            </template>
          </v-text-field>
        </Field>
      </template>
    </div>

    <v-btn
      v-if="isChoice"
      class="question-editor__add-option"
      block
      variant="tonal"
      prepend-icon="mdi-plus"
      @click="addChoice()"
    >
      Add option
    </v-btn>
  </div>
</template>

<style lang="scss" scoped>
.question-editor {
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
  padding: 1rem 1.1rem;
  border: 1px solid rgba(var(--v-theme-on-surface), 0.10);
  border-radius: 10px;
  background-color: rgba(var(--v-theme-surface), 0.7);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  transition: border-color 120ms ease, box-shadow 120ms ease;

  &:focus-within {
    border-color: rgb(var(--v-theme-primary));
    box-shadow: 0 0 0 3px rgba(var(--v-theme-primary), 0.12);
  }

  &__header {
    display: flex;
    align-items: center;
    gap: 0.25rem;
  }

  &__type {
    font-size: 0.85rem;
    font-weight: 600;
    color: rgba(var(--v-theme-on-surface), 0.7);
    text-transform: uppercase;
    letter-spacing: 0.04em;
  }

  &__actions {
    margin-inline-start: auto;
    display: flex;
    align-items: center;
    gap: 0.1rem;
  }

  &__choices {
    display: flex;
    flex-direction: column;
    gap: 0.5rem;
    margin-inline-start: 1rem;

    :deep(.v-input__prepend) {
      padding-inline-end: 0.65rem;
      margin-inline-end: 0;
    }
  }
}
</style>
