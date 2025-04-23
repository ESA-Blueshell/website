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

<script>
import TopBanner from "@/components/banners/TopBanner.vue";
import DocumentTable from "@/components/DocumentTable";
import {$require} from "@/plugins/require";

export default {
  name: "Documents",
  components: {TopBanner: TopBanner, DocumentTable},
  methods: {
    $require
  }
}
</script>

<style scoped>
.v-row {
  background-color: #212121;
}
</style>
