<script lang="ts" setup>
import ArchiveDialog from "@/components/island/ArchiveDialog.vue"
import {setCommitteeArchived, type Committee} from "../adapters/committees"

/** Archiving a committee, or bringing one back. */
defineOptions({name: "ArchiveCommitteeDialog"})

const props = defineProps<{open: boolean; committee: Pick<Committee, "id" | "name" | "archived">}>()

const emit = defineEmits<{
  (event: "update:open", open: boolean): void
  (event: "saved", committee: Committee): void
}>()

const save = async (archived: boolean) => {
  const result = await setCommitteeArchived(props.committee.id, archived)
  return result.ok ? {ok: true as const, saved: result.committee} : result
}
</script>

<template>
  <archive-dialog
    :archived="committee.archived"
    :leaving="`${committee.name} leaves the reel and the pickers, keeps its page and its events and joins the committees we used to have.`"
    :name="committee.name"
    :open="open"
    :returning="`${committee.name} goes back on the reel and into the pickers, among the committees that run.`"
    :save="save"
    testid="archive-committee-dialog"
    @saved="emit('saved', $event)"
    @update:open="emit('update:open', $event)"
  />
</template>
