<script lang="ts" setup>
import {computed, ref, watch} from "vue"
import {useRoute} from "vue-router"
import Island from "@/components/island/Island.vue"
import PagePlaceholder from "@/components/island/PagePlaceholder.vue"
import CommitteePage from "@/domains/committees/components/CommitteePage.vue"
import {loadCommitteePage, useCommittees} from "@/domains/committees"
import NotFound from "@/pages/NotFound.vue"

defineOptions({name: "CommitteeByAddressPage"})

/**
 * One committee's page, found by the address it answers to. The committees list, often read
 * already, draws it at once; the page's own read adds who sits on it.
 */
const route = useRoute()
const address = computed(() => String(route.params.address ?? ""))
const {committees} = useCommittees()

type Page = Awaited<ReturnType<typeof loadCommitteePage>>
const page = ref<Page>(null)
// Nothing is known until the api answers; until then this is neither a committee nor a miss.
const answered = ref(false)

const listed = computed(() => committees.value.find(one => one.slug === address.value.toLowerCase()) ?? null)
const shown = computed<Page>(() => {
  if (page.value) return page.value
  const known = listed.value
  if (!known) return null
  const {id, name, slug, description, archived, banner, icon, gameCodes} = known
  return {id, name, slug, description, archived, banner, icon, gameCodes, members: []}
})

const read = async () => {
  page.value = await loadCommitteePage(address.value)
  answered.value = true
}

watch(address, () => {
  page.value = null
  answered.value = false
  void read()
}, {immediate: true})

watch(() => shown.value?.name, name => {
  if (name) document.title = `${name} — Blueshell`
}, {immediate: true})
</script>

<template>
  <committee-page
    v-if="shown && !(answered && !page)"
    :key="shown.id"
    :page="shown"
    @changed="read"
  />
  <not-found v-else-if="answered" />
  <v-main v-else>
    <island>
      <page-placeholder testid="committee-placeholder" />
    </island>
  </v-main>
</template>
