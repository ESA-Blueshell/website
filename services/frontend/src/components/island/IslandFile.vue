<script lang="ts" setup>
/* A file to pick, with the file itself shown: a picture as a picture, anything else by kind. */
import {computed, onBeforeUnmount, ref, useId, watch} from "vue"

defineOptions({name: "IslandFile"})

const {
  accept = undefined,
  invalid = false,
  disabled = false,
  say = "Choose a file",
  describedBy = undefined,
  testid = undefined,
} = defineProps<{
  accept?: string
  invalid?: boolean
  disabled?: boolean
  /** What the empty field says it wants. */
  say?: string
  describedBy?: string
  testid?: string
}>()

const file = defineModel<File | null>({default: null})

const field = ref<HTMLInputElement | null>(null)
const controlId = `${useId()}-file`

const looksLike = computed<"image" | "pdf" | "sheet" | "text" | "file">(() => {
  const kind = file.value?.type ?? ""
  const name = file.value?.name.toLowerCase() ?? ""
  if (kind.startsWith("image/")) return "image"
  if (kind === "application/pdf" || name.endsWith(".pdf")) return "pdf"
  if (kind.includes("sheet") || /\.(csv|xlsx?|numbers)$/.test(name)) return "sheet"
  if (kind.startsWith("text/") || /\.(md|txt|docx?|pages)$/.test(name)) return "text"
  return "file"
})

/* Held rather than derived, so the browser is not asked for a new url on every render. */
const shown = ref<string>("")

watch(file, (picked) => {
  if (shown.value !== "") URL.revokeObjectURL(shown.value)
  shown.value = picked && picked.type.startsWith("image/") ? URL.createObjectURL(picked) : ""
}, {immediate: true})

onBeforeUnmount(() => {
  if (shown.value !== "") URL.revokeObjectURL(shown.value)
})

const weightOf = (bytes: number): string => {
  if (bytes === 0) return ""
  const mb = bytes / 1024 / 1024
  return mb >= 1 ? `${mb.toFixed(1)} MB` : `${Math.max(1, Math.round(bytes / 1024))} KB`
}

const take = (event: Event) => {
  file.value = (event.target as HTMLInputElement).files?.[0] ?? null
}

const clear = () => {
  file.value = null
  // The input is the template's own, so it is there for as long as this button can be pressed.
  ;(field.value as HTMLInputElement).value = ""
}
</script>

<template>
  <div
    class="island-file"
    :class="{'island-file--wrong': invalid, 'island-file--held': file !== null}"
  >
    <input
      :id="controlId"
      ref="field"
      :accept="accept"
      :aria-describedby="describedBy"
      class="island-file__input"
      :data-testid="testid"
      :disabled="disabled"
      type="file"
      @change="take"
    >

    <label
      class="island-file__plate"
      :for="controlId"
    >
      <img
        v-if="looksLike === 'image' && shown !== ''"
        alt=""
        class="island-file__shot"
        :src="shown"
      >

      <svg
        v-else
        aria-hidden="true"
        class="island-file__mark"
        fill="none"
        stroke="currentColor"
        stroke-linecap="round"
        stroke-linejoin="round"
        stroke-width="1.5"
        viewBox="0 0 24 24"
      >
        <template v-if="file === null">
          <path d="M12 16V4" />
          <path d="m7.5 8.5 4.5-4.5 4.5 4.5" />
          <path d="M4 16v3.5h16V16" />
        </template>
        <template v-else>
          <path d="M6 3h7l5 5v13H6z" />
          <path d="M13 3v5h5" />
          <path
            v-if="looksLike === 'pdf'"
            d="M9 13h6M9 16.5h6"
          />
          <path
            v-else-if="looksLike === 'sheet'"
            d="M9 12h6M9 15h6M12 12v6"
          />
          <path
            v-else-if="looksLike === 'text'"
            d="M9 12h6M9 15h6M9 18h3"
          />
        </template>
      </svg>
    </label>

    <div class="island-file__said">
      <label
        class="island-file__name"
        :for="controlId"
      >{{ file === null ? say : file.name }}</label>
      <span class="island-file__weight">
        {{ file === null ? "Pick one from this machine" : weightOf(file.size) }}
      </span>
    </div>

    <button
      v-if="file !== null"
      aria-label="Take the file off"
      class="island-file__off"
      :data-testid="testid ? `${testid}-clear` : undefined"
      type="button"
      @click="clear"
    >
      <svg
        aria-hidden="true"
        fill="none"
        stroke="currentColor"
        stroke-linecap="round"
        stroke-width="1.7"
        viewBox="0 0 24 24"
      >
        <path d="M6 6 18 18M18 6 6 18" />
      </svg>
    </button>
  </div>
</template>

<style scoped>
.island-file {
  display: flex;
  align-items: center;
  gap: 0.85rem;
  width: 100%;
  padding: 0.55rem 0.7rem;
  background-color: color-mix(in oklab, var(--color-chalk) 7%, transparent);
  border-bottom: 1px solid var(--color-hairline);
}

.island-file--wrong {
  border-bottom-color: var(--color-danger);
}

/* Kept rather than replaced: the dialog, the keyboard and the label all hang off it. */
.island-file__input {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip-path: inset(50%);
}

/* The file itself where there is one to show, and what the field wants where there is not. */
.island-file__plate {
  flex: none;
  display: grid;
  place-items: center;
  width: 3.4rem;
  height: 3.4rem;
  overflow: hidden;
  background-color: color-mix(in oklab, var(--color-chalk) 6%, transparent);
  color: var(--color-ash);
  cursor: pointer;
}

.island-file__plate:hover {
  color: var(--color-chalk);
  background-color: color-mix(in oklab, var(--color-chalk) 11%, transparent);
}

.island-file__input:focus-visible + .island-file__plate {
  outline: 2px solid var(--color-brand);
  outline-offset: 2px;
}

.island-file__shot {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.island-file__mark {
  width: 22px;
  height: 22px;
}

.island-file__said {
  display: flex;
  flex-direction: column;
  gap: 0.1rem;
  min-width: 0;
}

.island-file__name {
  overflow: hidden;
  font-family: var(--font-body);
  font-size: 0.85rem;
  color: var(--color-chalk);
  text-overflow: ellipsis;
  white-space: nowrap;
  cursor: pointer;
}

.island-file__weight {
  font-family: var(--font-bitmap);
  font-size: 0.68rem;
  color: var(--color-ash);
}

.island-file__off {
  margin-left: auto;
  display: grid;
  place-items: center;
  padding: 0.2rem;
  border: 0;
  background: none;
  color: var(--color-ash);
  cursor: pointer;
}

.island-file__off:hover {
  color: var(--color-chalk);
}

.island-file__off svg {
  width: 15px;
  height: 15px;
}
</style>
