<script lang="ts" setup>
import {nextTick, ref} from "vue"

/**
 * A song's title, which whoever may rename it changes where it stands: pressed, it becomes a field
 * holding the title, saved by Enter or by leaving it and put back by Escape. For anybody else it is
 * the title alone.
 */
defineOptions({name: "VoteTrackTitle"})

const {title, renamable = false, testid = undefined} = defineProps<{
  title: string
  renamable?: boolean
  testid?: string
}>()

const emit = defineEmits<{rename: [title: string]}>()

const editing = ref(false)
const typed = ref("")
const field = ref<HTMLInputElement | null>(null)

async function edit() {
  typed.value = title
  editing.value = true
  await nextTick()
  field.value?.select()
}

function save() {
  if (!editing.value) return
  editing.value = false
  const next = typed.value.trim()
  if (next !== "" && next !== title) emit("rename", next)
}

function cancel() {
  editing.value = false
}
</script>

<template>
  <input
    v-if="editing"
    ref="field"
    v-model="typed"
    aria-label="Song title"
    class="track-title__field"
    :data-testid="testid ? `${testid}-field` : undefined"
    maxlength="255"
    @blur="save"
    @keydown.enter.prevent="save"
    @keydown.esc.prevent="cancel"
  >
  <button
    v-else-if="renamable"
    :aria-label="`Rename ${title}`"
    class="track-title track-title--renamable"
    :data-testid="testid"
    type="button"
    @click="edit"
  >
    <span class="track-title__words">{{ title }}</span>
    <svg
      aria-hidden="true"
      class="track-title__mark"
      fill="none"
      stroke="currentColor"
      stroke-linecap="round"
      stroke-linejoin="round"
      stroke-width="1.6"
      viewBox="0 0 24 24"
    ><path d="M4 20h4L19 9l-4-4L4 16v4zM13.5 6.5l4 4" /></svg>
  </button>
  <span
    v-else
    class="track-title"
    :data-testid="testid"
  >{{ title }}</span>
</template>

<style scoped>
.track-title {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  min-width: 0;
  max-width: 100%;
  padding: 0;
  font: inherit;
  color: inherit;
  text-align: left;
  background: none;
  border: 0;
}

.track-title__words {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.track-title--renamable {
  cursor: text;
}

.track-title__mark {
  flex: none;
  width: 0.85rem;
  height: 0.85rem;
  opacity: 0.55;
}

.track-title--renamable:hover .track-title__mark,
.track-title--renamable:focus-visible .track-title__mark {
  opacity: 1;
}

/* The field takes the title's place and size, ruled like the place's other fields. */
.track-title__field {
  width: 100%;
  padding: 0.05rem 0.1rem;
  font: inherit;
  color: var(--color-chalk);
  background: none;
  border: 0;
  border-bottom: 1px solid color-mix(in oklab, var(--color-chalk) 45%, transparent);
  outline: none;
}
</style>
