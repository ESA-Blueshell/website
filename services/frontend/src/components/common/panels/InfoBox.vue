<script lang="ts" setup>
import {computed, ref} from "vue"

defineOptions({name: "InfoBox"})

/**
 * A labelled box holding one piece of context, optionally openable.
 *
 * There were three of these and no component: a labelled lifted box in the bulk dialog, a
 * help alert behind a "?" in the same dialog's header, and an accordion on the cohort page.
 * Same intent — a small caps label, a line saying what is inside, and a body you may or may
 * not need — spelled three ways, so no two of them looked alike.
 *
 * Collapsed, it is the labelled box: label, summary, nothing else. Give it `expandable` and
 * the summary keeps its place while the body moves behind a chevron, which is what makes a
 * page of these readable — the reader sees what each holds before deciding to open one.
 */
const props = withDefaults(defineProps<{
  /** Small caps line naming what the box holds. */
  label: string
  /** One line of context, read before the body is opened. Sits beside nothing else. */
  summary?: string | null
  /** Set to put the body behind a chevron. Without it the body is simply always shown. */
  expandable?: boolean
  /** Whether an expandable box starts open. Ignored when it is not expandable. */
  defaultOpen?: boolean
  /**
   * Drop the lifted surface and sit on whatever is behind it.
   *
   * A tint says "this is an aside". The box holding what a page is actually about is not an
   * aside, and tinting it stacks a panel inside a card inside a page — three surfaces for one
   * thing. Such a box keeps the label and the summary and loses the box.
   */
  flush?: boolean
  testid?: string | null
}>(), {
  summary: null,
  expandable: false,
  defaultOpen: false,
  flush: false,
  testid: null,
})

defineSlots<{
  /** The body. Hidden behind the chevron when the box is expandable and closed. */
  default?: () => unknown
  /** Controls on the header's trailing edge, before the chevron. */
  actions?: () => unknown
}>()

const open = ref(props.defaultOpen)

/** A box with nothing behind the chevron has nothing to open. */
const canToggle = computed(() => props.expandable)
const bodyShown = computed(() => !props.expandable || open.value)
</script>

<template>
  <div
    class="info-box"
    :class="{'info-box--flush': flush}"
    :data-testid="testid ?? undefined"
  >
    <div
      class="info-box__header"
      :class="{'info-box__header--clickable': canToggle}"
      :role="canToggle ? 'button' : undefined"
      :tabindex="canToggle ? 0 : undefined"
      :aria-expanded="canToggle ? String(open) : undefined"
      @click="canToggle && (open = !open)"
      @keydown.enter="canToggle && (open = !open)"
      @keydown.space.prevent="canToggle && (open = !open)"
    >
      <div class="info-box__heading">
        <span class="info-box__label">{{ label }}</span>
        <span
          v-if="summary"
          class="info-box__summary text-body-2"
          data-testid="info-box-summary"
        >{{ summary }}</span>
      </div>

      <div
        v-if="$slots.actions || canToggle"
        class="info-box__actions"
        @click.stop
      >
        <slot name="actions" />
        <v-btn
          v-if="canToggle"
          :aria-label="open ? `Hide ${label}` : `Show ${label}`"
          data-testid="info-box-toggle"
          :icon="open ? 'mdi-chevron-up' : 'mdi-chevron-down'"
          size="small"
          variant="text"
          @click="open = !open"
        />
      </div>
    </div>

    <v-expand-transition>
      <div
        v-if="bodyShown && $slots.default"
        class="info-box__body"
        data-testid="info-box-body"
      >
        <slot />
      </div>
    </v-expand-transition>
  </div>
</template>

<style lang="scss" scoped>
// A lifted surface rather than a border, matching the contribution-periods widget: these sit
// inside cards that are already the surface colour, where a border reads as a second frame.
.info-box {
  min-width: 0;
  padding: 10px 14px;
  border-radius: 6px;
  background: rgba(var(--v-theme-on-surface), 0.04);
}

// No tint and no inset: the box is the thing behind it, with a label on top.
.info-box--flush {
  padding: 0;
  border-radius: 0;
  background: none;
}

.info-box__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  min-width: 0;
}

.info-box__header--clickable {
  cursor: pointer;
  user-select: none;
}

// Label above the summary, both allowed to truncate rather than push the actions off the row.
.info-box__heading {
  min-width: 0;
}

// Self-contained overline treatment: Vuetify 4 (MD3) dropped the `.text-overline` utility.
.info-box__label {
  display: block;
  font-size: 0.6875rem;
  font-weight: 600;
  letter-spacing: 0.1em;
  line-height: 1.4;
  text-transform: uppercase;
  color: rgba(var(--v-theme-on-surface), 0.7);
}

.info-box__summary {
  display: block;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.info-box__actions {
  display: flex;
  align-items: center;
  gap: 4px;
  flex: 0 0 auto;
}

.info-box__body {
  padding-top: 10px;
}
</style>
