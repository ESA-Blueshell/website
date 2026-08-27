<script lang="ts" setup>
defineOptions({name: "EsportsIsland"})
</script>

<template>
  <!--
    The island's root. Everything Tailwind-styled on the esports pages lives
    inside this element, because the island's reset is scoped to this class and
    reaches nothing above it.

    It commits to dark. These pages carry photography edge to edge and are the
    association's shopfront rather than a screen somebody works in, so there is
    no light variant to keep in step. The theme toggle in the navigation still
    sets the site-wide preference; these pages simply do not read it.
  -->
  <div
    class="esports-island"
    data-testid="esports-island"
  >
    <slot />
  </div>
</template>
