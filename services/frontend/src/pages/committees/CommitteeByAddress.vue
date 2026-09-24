<script lang="ts" setup>
import {computed, ref, watch} from "vue"
import {useRoute} from "vue-router"
import CommitteePage from "@/domains/committees/components/CommitteePage.vue"
import {loadCommitteePage} from "@/domains/committees"
import NotFound from "@/pages/NotFound.vue"

defineOptions({name: "CommitteeByAddressPage"})

/** One committee's page, found by the address it answers to. */
const route = useRoute()
const address = computed(() => String(route.params.address ?? ""))

type Page = Awaited<ReturnType<typeof loadCommitteePage>>
const page = ref<Page>(null)
// Nothing is known until the api answers; until then this is neither a committee nor a miss.
const answered = ref(false)

const read = async () => {
  page.value = await loadCommitteePage(address.value)
  answered.value = true
  if (page.value) document.title = `${page.value.name} — Blueshell`
}

watch(address, () => {
  answered.value = false
  void read()
}, {immediate: true})
</script>

<template>
  <committee-page
    v-if="page"
    :key="page.id"
    :page="page"
    @changed="read"
  />
  <not-found v-else-if="answered" />
</template>
