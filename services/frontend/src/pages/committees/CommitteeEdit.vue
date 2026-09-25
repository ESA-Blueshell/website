<script lang="ts" setup>
import {computed, ref} from "vue"
import {useRoute, useRouter} from "vue-router"
import CommitteeEditor from "@/domains/committees/components/CommitteeEditor.vue"
import {type Committee, useCommitteeRights, useCommittees} from "@/domains/committees"
import {useReturnTo} from "@/composables/useReturnTo"
import NotFound from "@/pages/NotFound.vue"

defineOptions({name: "CommitteeEditPage"})

/**
 * One committee added or corrected on its own page. It goes back where it came from, and to the
 * committee's own page once it is saved, at the address it has then.
 */
const route = useRoute()
const router = useRouter()
const {committees, ready, refresh} = useCommittees()
const {isBoard, sitsOn} = useCommitteeRights()

const adding = computed(() => route.params.address == null)
const answered = ref(false)
void ready.then(() => { answered.value = true })
const committee = computed<Committee | null>(() => committees.value.find(one => one.slug === route.params.address) ?? null)
const mayEdit = computed(() => isBoard.value || (committee.value != null && sitsOn(committee.value.id)))

const back = useReturnTo(adding.value ? "/committees" : `/committees/${String(route.params.address)}`)

const saved = async (now: Committee) => {
  await refresh()
  void router.replace(`/committees/${now.slug}`)
}
</script>

<template>
  <committee-editor
    v-if="adding ? isBoard : committee && mayEdit"
    :as-board="isBoard"
    :back="back"
    :committee="adding ? null : committee"
    @cancel="router.replace(back)"
    @saved="saved"
  />
  <not-found v-else-if="answered" />
</template>
