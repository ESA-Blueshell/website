<script lang="ts" setup>
import {computed, onBeforeUnmount, nextTick, ref, watch} from "vue"

/**
 * Picking one of a list, in the island's own dress.
 *
 * A scrolling panel that holds everything rather than a handful shown under a box: a long list has
 * more in it than a glance can take, and cutting it short meant the one somebody wanted might
 * simply not be there. It all is; the box narrows it. A component rather than the same markup in
 * every dialog that asks for one of a list.
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

/**
 * Whether the list is down.
 *
 * A list that is always there pushes everything under it down the dialog and grows the form
 * as you type. Down only while the field is being used, over what follows rather than through
 * it, and gone again once something is chosen.
 */
const open = ref(false)

const field = ref<HTMLElement | null>(null)
const list = ref<HTMLElement | null>(null)

/**
 * Where the list is drawn, in the page's own coordinates.
 *
 * Rendered at the end of the document rather than beside the field, because every ancestor
 * that scrolls or is cut on the island's diagonal would otherwise clip it — and one of them
 * always is. Positioned against the field's box instead, and told again whenever anything
 * moves under it.
 */
const box = ref({top: 0, left: 0, width: 0, above: false})

const place = () => {
  const anchor = field.value?.getBoundingClientRect()
  if (!anchor) return
  const room = window.innerHeight - anchor.bottom
  // Opens upward where there is no room below it, which on a short window there often is not.
  const above = room < 260 && anchor.top > room
  box.value = {
    top: above ? anchor.top - 6 : anchor.bottom + 6,
    left: anchor.left,
    width: anchor.width,
    above,
  }
}

/**
 * A press anywhere but the field and its list is a press elsewhere, so the list goes.
 *
 * The list is found by its mark rather than by the ref, because it is drawn at the end of the
 * document and this has to hold however it got there.
 */
const elsewhere = (event: Event) => {
  const target = event.target as Element | null
  if (field.value?.parentElement?.contains(target)) return
  if (target?.closest?.("[data-island-picker-list]")) return
  open.value = false
}

const watching = (on: boolean) => {
  const how = on ? window.addEventListener : window.removeEventListener
  how("scroll", place, true)
  how("resize", place)
  // Bubbling rather than capturing, so the list's own handler can stop a press on itself from
  // ever reading as a press elsewhere. Capturing would run first and close it under the click.
  const doc = on ? document.addEventListener : document.removeEventListener
  doc("pointerdown", elsewhere)
}

watch(open, async (down) => {
  if (!down) {
    watching(false)
    return
  }
  place()
  await nextTick()
  place()
  watching(true)
})

onBeforeUnmount(() => watching(false))

const chosen = computed(() =>
  props.options.find(one => one.key === props.selectedKey)?.label ?? "")

const pick = (key: string) => {
  open.value = false
  search.value = ""
  emit("pick", key)
}

/**
 * Closes when focus leaves the field and the list together, not when it moves between them.
 *
 * The list counts as part of this control even though it is drawn at the end of the document,
 * so it is found by its mark rather than by asking whether this element contains it — which it
 * does not. Without that, pressing a row moves focus out of the field, the list is taken down
 * between the press and the release, and the press lands on nothing.
 */
const leave = (event: FocusEvent) => {
  const next = event.relatedTarget as Element | null
  if (next && (event.currentTarget as HTMLElement).contains(next)) return
  if (next?.closest?.("[data-island-picker-list]")) return
  open.value = false
}

const matches = computed(() => {
  const term = search.value.trim().toLowerCase()
  if (term === "") return props.options
  return props.options.filter(one => one.label.toLowerCase().includes(term))
})
</script>

<template>
  <div
    class="picker"
    @focusout="leave"
  >
    <input
      ref="field"
      v-model="search"
      :aria-controls="`${testidPrefix}-list`"
      aria-haspopup="listbox"
      :aria-label="placeholder"
      class="picker__search"
      :data-testid="`${testidPrefix}-search`"
      :placeholder="chosen || placeholder"
      type="text"
      @click="open = true"
      @input="open = true"
    >

    <p
      v-if="options.length === 0"
      class="picker__note"
      :data-testid="`${testidPrefix}-none`"
    >
      {{ emptyNote }}
    </p>

    <!--
      Drawn at the end of the document: over everything, and out of reach of the ancestors
      that scroll or are cut on the island's diagonal, every one of which would clip it.
    -->
    <Teleport to="body">
      <ul
        v-if="open && matches.length > 0"
        :id="`${testidPrefix}-list`"
        ref="list"
        class="picker__list"
        :class="{'picker__list--above': box.above}"
        data-island-picker-list
        :data-testid="`${testidPrefix}-list`"
        :style="{
          top: `${box.top}px`,
          left: `${box.left}px`,
          width: `${box.width}px`,
        }"
        @mousedown.stop
        @pointerdown.stop
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
            @click="pick(one.key)"
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
        v-else-if="open && options.length > 0"
        ref="list"
        class="picker__note picker__note--over"
        data-island-picker-list
        :data-testid="`${testidPrefix}-no-matches`"
        :style="{
          top: `${box.top}px`,
          left: `${box.left}px`,
          width: `${box.width}px`,
        }"
        @mousedown.stop
        @pointerdown.stop
      >
        Nothing answers to that.
      </p>
    </Teleport>
  </div>
</template>

<style scoped>
.picker {
  position: relative;
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
  position: fixed;
  /* Above the dialog it is opened from, which sits at 2401: a menu belongs over the surface
     that raised it, the way every other overlay on the site stacks.
     
     And taking its own pointer events back. A modal dialog turns them off on the body while it
     is open, so that everything behind it is inert -- but this is drawn at the end of the body
     to escape the dialog's clipping, and would be made inert along with the page it is over. */
  z-index: 2500;
  pointer-events: auto;
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  max-height: 15rem;
  padding: 0.35rem;
  margin: 0;
  overflow-y: auto;
  list-style: none;
  background-color: var(--color-pit);
  border: 1px solid color-mix(in oklab, var(--color-chalk) 14%, transparent);
  box-shadow: 0 1rem 2rem rgb(0 0 0 / 45%);
  overscroll-behavior: contain;
}

/* Anchored by its bottom edge where it opens upward, so it grows away from the field. */
.picker__list--above {
  transform: translateY(-100%);
}

.picker__note--over {
  position: fixed;
  z-index: 2500;
  margin: 0;
  pointer-events: auto;
  padding: 0.6rem 0.75rem;
  background-color: var(--color-pit);
  border: 1px solid color-mix(in oklab, var(--color-chalk) 14%, transparent);
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
