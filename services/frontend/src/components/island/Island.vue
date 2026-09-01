<script lang="ts" setup>
defineOptions({name: "Island"})

withDefaults(defineProps<{
  /**
   * What a test names this island by.
   *
   * The island is the site's, so more than one page stands on it, and a spec that means the
   * esports one has to be able to say so.
   */
  testid?: string
}>(), {testid: "island"})
</script>

<template>
  <!--
    The island's root: everything Tailwind-styled on the pages that stand on it lives
    inside it, because the reset is scoped to this class. The theme, the tile and what
    stays dark regardless are all in island.css, which is why this still has no script
    beyond its name.
  -->
  <div
    class="island"
    :data-testid="testid"
  >
    <slot />
  </div>
</template>
