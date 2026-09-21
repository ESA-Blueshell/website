<script lang="ts" setup>
/* Room for more than a line, on the same ground and rule as the single-line field. */
defineOptions({name: "IslandTextarea"})

const {
  rows = 4,
  placeholder = "",
  invalid = false,
  disabled = false,
  controlId = undefined,
  describedBy = undefined,
  testid = undefined,
} = defineProps<{
  rows?: number
  placeholder?: string
  invalid?: boolean
  disabled?: boolean
  controlId?: string
  describedBy?: string
  testid?: string
}>()

const value = defineModel<string>({default: ""})
</script>

<template>
  <textarea
    :id="controlId"
    v-model="value"
    :aria-describedby="describedBy"
    :aria-invalid="invalid || undefined"
    class="island-textarea"
    :class="{'island-textarea--wrong': invalid}"
    :data-testid="testid"
    :disabled="disabled"
    :placeholder="placeholder"
    :rows="rows"
  />
</template>

<style scoped>
.island-textarea {
  width: 100%;
  padding: 0.6rem 1rem;
  border: 0;
  border-bottom: 1px solid var(--color-hairline);
  background-color: color-mix(in oklab, var(--color-chalk) 7%, transparent);
  font-family: var(--font-body);
  font-size: 0.9rem;
  line-height: 1.5;
  color: var(--color-chalk);
  resize: vertical;
}

.island-textarea::placeholder {
  color: var(--color-ash);
}

.island-textarea:focus-visible {
  outline: none;
  border-bottom-color: var(--color-brand);
  background-color: color-mix(in oklab, var(--color-chalk) 10%, transparent);
}


.island-textarea--wrong {
  border-bottom-color: var(--color-danger);
}

.island-textarea:disabled {
  color: var(--color-ash);
  cursor: not-allowed;
}
</style>
