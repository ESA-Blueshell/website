<script lang="ts" setup>
import CutButton from "@/components/island/CutButton.vue"

/* Under a list whose rows can be ticked: how many are, what can be done with them, and a way to
   clear. A list that keeps it in view with nothing ticked says so, "0 selected", and its actions
   wait, disabled, until something is. */
const {count, noun = "selected", always = false, testid = undefined} = defineProps<{
  count: number
  noun?: string
  /** Stays in view with nothing ticked, its actions disabled. */
  always?: boolean
  testid?: string
}>()

const emit = defineEmits<{clear: []}>()
</script>

<template>
  <div
    v-if="always || count > 0"
    class="selection-bar"
    :class="{'selection-bar--idle': count === 0}"
    :data-testid="testid"
  >
    <span class="selection-bar__n">{{ count }} {{ noun }}</span>
    <span class="selection-bar__gap" />
    <!-- A fieldset, because disabling it disables every action in it at once. -->
    <fieldset
      class="selection-bar__acts"
      :disabled="count === 0"
    >
      <slot />
    </fieldset>
    <cut-button
      v-if="count > 0"
      small
      :testid="testid ? `${testid}-clear` : undefined"
      tone="quiet"
      @click="emit('clear')"
    >
      Clear
    </cut-button>
  </div>
</template>

<style scoped>
.selection-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.6rem 1rem;
  margin-top: 2px;
  padding: 0.75rem 1rem 0.75rem 1.4rem;
  background-color: var(--color-raised);
  box-shadow: inset 0 2px 0 var(--color-brand);
}

.selection-bar__n {
  font-family: "Shellhouse One", sans-serif;
  font-size: 0.95rem;
  text-transform: uppercase;
}

.selection-bar__gap {
  flex-grow: 1;
}

.selection-bar__acts {
  display: contents;
}

.selection-bar--idle {
  box-shadow: inset 0 2px 0 var(--color-hairline);
}

.selection-bar--idle .selection-bar__n {
  color: var(--color-ash);
}

/* Waiting, not faded out: a dimmed button's words fall under what can be read, and these are
   there to be read before anything is ticked. */
.selection-bar--idle :deep(.island-cut:disabled) {
  opacity: 1;
  color: var(--color-ash);
  background-color: var(--band-ground);
}
</style>
