<script lang="ts" setup>
import {computed} from "vue"
import ArchiveDialog from "@/components/island/ArchiveDialog.vue"
import {setCommitteeArchived, type Committee} from "../adapters/committees"

/** Archiving a committee, or bringing one back. */
defineOptions({name: "ArchiveCommitteeDialog"})

const props = defineProps<{open: boolean; committee: Pick<Committee, "id" | "name" | "archived" | "listed">}>()

const emit = defineEmits<{
  (event: "update:open", open: boolean): void
  (event: "saved", committee: Committee): void
}>()

const save = (archived: boolean) => setCommitteeArchived(props.committee.id, archived)

// An unlisted committee runs again off the reel, which the board would otherwise take for a failure.
const returning = computed(() => props.committee.listed
  ? `${props.committee.name} goes back on the reel and into the pickers, among the committees that run.`
  : `${props.committee.name} goes back into the pickers, among the committees that run. It is unlisted, so it stays off the reel and the menu until it is listed on its edit page.`)
</script>

<template>
  <archive-dialog
    :archived="committee.archived"
    :leaving="`${committee.name} leaves the reel and the pickers, keeps its page and its events and joins the committees we used to have.`"
    :name="committee.name"
    :open="open"
    :returning="returning"
    :save="save"
    testid="archive-committee-dialog"
    @saved="emit('saved', $event)"
    @update:open="emit('update:open', $event)"
  />
</template>
