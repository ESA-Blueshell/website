<script lang="ts" setup>
import {reactive, watch} from "vue"
import type {FormQuestion} from "@/lib"

const props = defineProps({
  initialForm: {
    type: Object,
    default: () => {}
  },
})
const emits = defineEmits(["change"])

// Form can be filled with objects. Each object will be a question/part of the question form
// Four types are possible: 'description', 'open', 'checkbox' and 'radio'
// Each object should have a 'prompt' attribute
// For 'checkbox' and 'radio' a 'options' array of options should be included
const form = reactive(props.initialForm
  ? JSON.parse(JSON.stringify(props.initialForm))
  : [])

watch(form, async (newForm) => {
  emits("change", newForm)
})


// Adds a new question to the form
function createQuestion(type: string) {
  if (type === "open" || type === "description") {
    form.push({type: type, prompt: ""})
  } else {
    form.push({type: type, prompt: "", options: ["", ""]})
  }
}

// Moves the ith element one index up in the given array. This probably throws an exception if i == 0
function moveUp(array: FormQuestion[], i: number) {
  const temp = array[i]
  array[i] = array[i - 1] as FormQuestion
  array[i - 1] = temp as FormQuestion
}

// Moves the ith element one index down in the given array. This probably throws an exception if i == array.length-1
function moveDown(array: FormQuestion[], i: number) {
  const temp = array[i]
  array[i] = array[i + 1] as FormQuestion
  array[i + 1] = temp as FormQuestion
}

</script>

<template>
  <div class="pa-4 form">
    <!--
      The actual form
    -->
    <div
      v-for="(question,i) in form"
      :key="i"
    >
      <v-text-field
        v-model="question.prompt"
        :label="`${question.type === 'description' ? `Description ${i+1}` : `Question ${i+1}`}`"
      >
        <template #append>
          <!-- Button to add option (v-if question has options) -->
          <v-tooltip
            v-if="question.type === 'radio' || question.type === 'checkbox'"
            location="top"
            text="Add option"
          >
            <template #activator="{ props }">
              <v-btn
                icon="mdi-plus"
                v-bind="props"
                variant="plain"
                @click="question.options.push('')"
              />
            </template>
          </v-tooltip>

          <!-- Buttons for moving the question up or down and remove button -->
          <v-btn
            :disabled="i === form.length-1"
            icon="mdi-chevron-down"
            variant="plain"
            @click="moveDown(form, i)"
          />
          <v-btn
            :disabled="i === 0"
            icon="mdi-chevron-up"
            variant="plain"
            @click="moveUp(form, i)"
          />
          <v-btn
            icon="mdi-close"
            variant="plain"
            @click="form.splice(i,1)"
          />
        </template>
      </v-text-field>

      <!--
        If the question has options, add some text-fields for those options
      -->
      <div v-if="question.type === 'radio' || question.type === 'checkbox'">
        <v-text-field
          v-for="(option, j) in question.options"
          :key="j"
          v-model="question.options[j]"
          :label="`Option ${j+1}`"
          :prepend-icon="question.type==='radio' ? 'mdi-radiobox-marked' : 'mdi-checkbox-marked'"
          density="compact"
        >
          <template #append>
            <!-- Buttons for moving the option up or down and remove button -->
            <v-btn
              :disabled="j === question.options.length-1"
              icon="mdi-chevron-down"
              variant="plain"
              @click="moveDown(question.options, j)"
            />
            <v-btn
              :disabled="j === 0"
              icon="mdi-chevron-up"
              variant="plain"
              @click="moveUp(question.options, j)"
            />
            <v-btn
              icon="mdi-close"
              variant="plain"
              @click="question.options.splice(j,1)"
            />
          </template>
        </v-text-field>
      </div>

      <v-divider
        v-if="i !== form.length-1"
        class="mb-4"
      />
    </div>

    <!--
          Button adding a new question to the form
        -->
    <v-menu location="bottom">
      <template #activator="{ props }">
        <v-btn
          block
          class="mt-2"
          v-bind="props"
          variant="outlined"
        >
          Add question or text to sign-up form
        </v-btn>
      </template>
      <v-list>
        <v-list-item @click="createQuestion('description')">
          Description without a question
        </v-list-item>
        <v-list-item @click="createQuestion('open')">
          Open question
        </v-list-item>
        <v-list-item @click="createQuestion('radio')">
          Multiple choice question
        </v-list-item>
        <v-list-item @click="createQuestion('checkbox')">
          Question with checkboxes
        </v-list-item>
      </v-list>
    </v-menu>

    <v-expand-transition>
      <v-alert
        v-if="initialForm !== undefined && initialForm !== null && JSON.stringify(initialForm) !== JSON.stringify(form)"
        prominent
        type="warning"
        variant="outlined"
      >
        Woah there! Looks like you made some changes to the sign-up form. Keep in mind that when you submit any
        changes to the form, all existing sign-ups <b>will be removed</b>!
      </v-alert>
    </v-expand-transition>
  </div>
</template>

<style lang="scss" scoped>
@use '../../styles/settings';

.form {
  border-radius: settings.$border-radius-root;
  border-width: 1px;
  border-color: rgb(var(--v-theme-accent));
  border-style: solid;
}
</style>
