<template>
  <v-main>
    <top-banner title="Documents" />
    <div
      class="mx-auto my-5"
      style="max-width: 800px"
    >
      <document-table />
    </div>
  </v-main>
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

<style lang="scss" scoped>
.v-row {
  background-color: #212121;
}
</style>
