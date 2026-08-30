<script lang="ts" setup>
import {computed, ref} from "vue"

/**
 * Picking one of a list, in the island's own dress.
 *
 * A scrolling panel that holds everything rather than a handful shown under a box: an
 * association with a long history has more teams than a glance can take, and cutting the list
 * short meant the one somebody wanted might simply not be there. It all is; the box narrows it.
 *
 * A component rather than the same markup three times: the games of a season, the pool of
 * teams, and the team a line-up is started from all ask this same question.
 */
defineOptions({name: "IslandPicker"})

const props = withDefaults(defineProps<{
  options: Array<{key: string; label: string; note?: string}>
  testidPrefix: string
  placeholder?: string
  /** Said where the list is empty, which is a different thing from nothing matching. */
  emptyNote?: string
  /**
   * The row already chosen, which stays in the list rather than replacing it.
   *
   * Choosing again is the same act as choosing, so it is the same control: swapping the list
   * for a name and a cross would mean two ways to do one thing, and the second only findable
   * once the first had been used.
   */
  selectedKey?: string | null
  disabled?: boolean
}>(), {
  placeholder: "Search",
  emptyNote: "There is nothing to choose from.",
  selectedKey: null,
  disabled: false,
})

const emit = defineEmits<{
  (event: "pick", key: string): void
}>()

const search = ref("")

const matches = computed(() => {
  const term = search.value.trim().toLowerCase()
  if (term === "") return props.options
  return props.options.filter(one => one.label.toLowerCase().includes(term))
})
</script>

<template>
  <div class="picker">
    <input
      v-model="search"
      :aria-label="placeholder"
      class="picker__search"
      :data-testid="`${testidPrefix}-search`"
      :placeholder="placeholder"
      type="text"
    >

    <p
      v-if="options.length === 0"
      class="picker__note"
      :data-testid="`${testidPrefix}-none`"
    >
      {{ emptyNote }}
    </p>

    <ul
      v-else-if="matches.length > 0"
      class="picker__list"
      :data-testid="`${testidPrefix}-list`"
    >
      <li
        v-for="one in matches"
        :key="one.key"
      >
        <button
          :aria-pressed="one.key === selectedKey"
          class="picker__row"
          :class="{'picker__row--on': one.key === selectedKey}"
          :data-testid="`${testidPrefix}-${one.key}`"
          :disabled="disabled"
          type="button"
          @click="emit('pick', one.key)"
        >
          <span class="picker__label">{{ one.label }}</span>
          <span
            v-if="one.note"
            class="picker__note-inline"
          >{{ one.note }}</span>
        </button>
      </li>
    </ul>

    <p
      v-else
      class="picker__note"
      :data-testid="`${testidPrefix}-no-matches`"
    >
      Nothing answers to that.
    </p>
  </div>
</template>

<style scoped>
.picker {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
}

/*
 * Cut on the same diagonal as everything else here, so a field reads as part of the island
 * rather than as a browser control dropped into it.
 */
.picker__search {
  padding: 0.6rem 0.9rem;
  font-family: var(--font-body);
  font-size: 0.9rem;
  color: var(--color-chalk);
  background-color: color-mix(in oklab, var(--color-chalk) 7%, transparent);
  border: 0;
  clip-path: polygon(0.55rem 0, 100% 0, calc(100% - 0.55rem) 100%, 0 100%);
}

.picker__search::placeholder {
  color: var(--color-ash);
}

/*
 * Scrolls rather than being cut short: everything is in here, and the box above narrows it.
 * Ten rows is about a screenful on a phone and comfortably short of one on a desktop.
 */
.picker__list {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
  max-height: 17rem;
  padding: 0;
  margin: 0;
  overflow-y: auto;
  list-style: none;
  overscroll-behavior: contain;
}

.picker__row {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 0.15rem;
  width: 100%;
  padding: 0.55rem 0.9rem;
  overflow: hidden;
  font-family: var(--font-body);
  color: var(--color-chalk);
  text-align: left;
  cursor: pointer;
  background-color: color-mix(in oklab, var(--color-chalk) 5%, transparent);
  border: 0;
  clip-path: polygon(0.55rem 0, 100% 0, calc(100% - 0.55rem) 100%, 0 100%);
}

.picker__row::before {
  position: absolute;
  inset: 0;
  content: "";
  background-color: color-mix(in oklab, var(--color-brand) 26%, transparent);
  scale: 0 1;
  transform-origin: left center;
  transition: scale 260ms cubic-bezier(0.22, 1, 0.36, 1);
}

.picker__row:hover:not(:disabled)::before,
.picker__row:focus-visible::before {
  scale: 1 1;
}

/* Chosen, and filled in the association's own blue: the same language the choice at the top
   of the dialog speaks, so "this one" reads the same wherever it is said. */
.picker__row--on {
  color: var(--color-void);
  background-color: var(--color-brand);
}

.picker__row--on .picker__note-inline {
  color: color-mix(in oklab, var(--color-void) 70%, transparent);
}

.picker__row--on::before {
  background-color: color-mix(in oklab, var(--color-brand) 82%, black);
}

.picker__row:disabled {
  cursor: default;
  opacity: 0.55;
}

.picker__label,
.picker__note-inline {
  position: relative;
}

.picker__label {
  font-size: 0.92rem;
}

.picker__note-inline {
  font-size: 0.72rem;
  color: var(--color-ash);
}

.picker__note {
  margin: 0;
  font-family: var(--font-body);
  font-size: 0.85rem;
  color: var(--color-ash);
}

@media (prefers-reduced-motion: reduce) {
  .picker__row::before {
    transition: none;
  }
}
</style>
