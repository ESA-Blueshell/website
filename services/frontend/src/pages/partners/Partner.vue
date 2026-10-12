<script setup lang="ts">
import {PARTNER_PAGES, type PartnerSlug} from "@/domains/association"
import PartnerPage from "@/domains/association/island/PartnerPage.vue"

const {slug} = defineProps<{slug: PartnerSlug}>()
</script>

<template>
  <main class="site-main">
    <partner-page :content="PARTNER_PAGES[slug]" />
  </main>
</template>
