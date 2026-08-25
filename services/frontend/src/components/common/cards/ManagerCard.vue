<script lang="ts" setup>
defineOptions({name: "ManagerCard"})

/**
 * A section of a management page: a header naming what the section is, an optional action
 * or two beside it, and a body.
 *
 * Every management page had its own copy of this card, and the copies had drifted — the same
 * background and shadow, but header padding of `18px 18px 14px` on one page and
 * `10px 14px 8px` on three others. Same intent, no home. This is the home.
 */
withDefaults(defineProps<{
  /** Small caps line above the title, naming the area rather than the section. */
  eyebrow?: string | null
  title?: string | null
  /** One line under the title, for counts, ranges and other context. */
  subtitle?: string | null
  testid?: string | null
  /** Bottom margin, so a caller does not reach for `mb-*` and pick a different one. */
  spaced?: boolean
  /**
   * Render the body without padding, for content that supplies its own — a `v-list` or a
   * table sits flush against the header, a form or a filter row does not.
   */
  flush?: boolean
}>(), {
  eyebrow: null,
  title: null,
  subtitle: null,
  testid: null,
  spaced: false,
  flush: false,
})

defineSlots<{
  /** The body of the card. */
  default?: () => unknown
  /** Actions on the header's trailing edge, e.g. a refresh button. */
  actions?: () => unknown
  /** Replaces the whole header, for a section whose heading is not a title and a subtitle. */
  header?: () => unknown
}>()
</script>

<template>
  <v-card
    class="manager-card"
    :class="{'mb-4': spaced}"
    :data-testid="testid ?? undefined"
    rounded="lg"
    variant="flat"
  >
    <div class="manager-card__header">
      <slot name="header">
        <div class="manager-card__heading">
          <p
            v-if="eyebrow"
            class="text-overline mb-1"
          >
            {{ eyebrow }}
          </p>
          <h2
            v-if="title"
            class="text-h6 mb-1"
          >
            {{ title }}
          </h2>
          <p
            v-if="subtitle"
            class="text-caption text-medium-emphasis mb-0"
          >
            {{ subtitle }}
          </p>
        </div>

        <div
          v-if="$slots.actions"
          class="manager-card__actions"
        >
          <slot name="actions" />
        </div>
      </slot>
    </div>

    <div
      v-if="$slots.default && !flush"
      class="manager-card__body"
    >
      <slot />
    </div>
    <slot v-else-if="$slots.default" />
  </v-card>
</template>
