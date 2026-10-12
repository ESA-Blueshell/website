<template>
  <main class="site-main">
    <top-banner title="Documents" />
    <div
      class="site-column documents-column"
      style="max-width: 800px"
    >
      <document-table />
    </div>
  </main>
</template>

<script lang="ts">
import TopBanner from "@/components/common/banners/TopBanner.vue"
import DocumentTable from "@/components/base/DocumentTable.vue"
import {$require} from "@/plugins/require.js"

export default {
  name: "Documents",
  components: {TopBanner: TopBanner, DocumentTable},
  methods: {
    $require,
  },
}
</script>

<style scoped>
.documents-column {
  margin-block: 20px;
}
</style>
