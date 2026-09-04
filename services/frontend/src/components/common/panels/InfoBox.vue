<script lang="ts" setup>
import {computed, ref} from "vue"

defineOptions({name: "InfoBox"})

/**
 * A labelled box holding one piece of context, optionally openable.
 *
 * One component for what three places need: a small caps label, a line saying what is inside,
 * and a body the reader may or may not want. Collapsed it is a labelled box, a heading wearing
 * the count of what it holds; given `expandable` the body moves behind a chevron, which is what
 * makes a page of these readable — each says what it holds before anybody opens it.
 */
const props = withDefaults(defineProps<{
  /** Heading naming what the box holds. */
  label: string
  /** How many things are inside, worn as a badge on the label. Omit to show no badge. */
  count?: number | null
  /** Set to put the body behind a chevron. Without it the body is simply always shown. */
  expandable?: boolean
  /** Whether an expandable box starts open. Ignored when it is not expandable. */
  defaultOpen?: boolean
  testid?: string | null
}>(), {
  count: null,
  expandable: false,
  defaultOpen: false,
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
        <v-badge
          v-if="count != null"
          color="primary"
          :content="count"
          data-testid="info-box-count"
          :offset-x="-6"
          :offset-y="-2"
        >
          <span class="info-box__label">{{ label }}</span>
        </v-badge>
        <span
          v-else
          class="info-box__label"
        >{{ label }}</span>
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
  padding: 14px 16px;
  border-radius: 6px;
  background: rgba(var(--v-theme-on-surface), 0.04);
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

// The heading truncates rather than pushing the actions off the row.
.info-box__heading {
  min-width: 0;
}

// A section heading rather than an overline: these name the halves of a page, so they carry
// the weight of one. Vuetify 4 (MD3) dropped `.text-overline`, so the caps are spelled here.
// Clear of the word rather than over its last letter: the label is short and the badge is
// nearly as tall as it, so the default overlap swallowed a glyph.
.info-box__heading :deep(.v-badge__badge) {
  min-width: 16px;
  height: 16px;
  padding: 0 4px;
  font-size: 0.625rem;
}

.info-box__label {
  display: block;
  font-size: 0.9375rem;
  font-weight: 700;
  letter-spacing: 0.06em;
  line-height: 1.3;
  text-transform: uppercase;
  color: rgb(var(--v-theme-on-surface));
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
