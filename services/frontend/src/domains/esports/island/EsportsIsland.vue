<script lang="ts" setup>
defineOptions({name: "EsportsIsland"})
</script>

<template>
  <!--
    The island's root: everything Tailwind-styled on the esports pages lives inside
    it, because the reset is scoped to this class. The theme, the tile and what stays
    dark regardless are all in island.css, which is why this still has no script.
  -->
  <div
    class="esports-island"
    data-testid="esports-island"
  >
    <slot />
  </div>
</template>
