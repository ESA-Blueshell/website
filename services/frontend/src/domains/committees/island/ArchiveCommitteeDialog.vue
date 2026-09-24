<script lang="ts" setup>
import {computed, ref, watch} from "vue"
import ConfirmDialog from "@/components/island/ConfirmDialog.vue"
import {setCommitteeArchived, type Committee} from "../adapters/committees"

/** Archiving a committee, or bringing one back, said plainly before it happens. */
defineOptions({name: "ArchiveCommitteeDialog"})

const props = defineProps<{open: boolean; committee: Pick<Committee, "id" | "name" | "archived">}>()

const emit = defineEmits<{
  (event: "update:open", open: boolean): void
  (event: "saved", committee: Committee): void
}>()

const working = ref(false)
const failure = ref<string | null>(null)
watch(() => props.open, open => { if (open) failure.value = null })

const archiving = computed(() => !props.committee.archived)

const question = computed(() =>
  archiving.value
    ? `${props.committee.name} leaves the reel and the pickers, keeps its page and its events and joins the committees we used to have.`
    : `${props.committee.name} goes back on the reel and into the pickers, among the committees that run.`)

const confirm = async () => {
  if (working.value) return
  working.value = true
  failure.value = null
  try {
    const result = await setCommitteeArchived(props.committee.id, archiving.value)
    if (!result.ok) {
      failure.value = result.reason
      return
    }
    emit("saved", result.committee)
    emit("update:open", false)
  } finally {
    working.value = false
  }
}
</script>

<template>
  <confirm-dialog
    :confirm-label="archiving ? 'Archive' : 'Bring it back'"
    :failure="failure"
    :open="open"
    :question="question"
    testid="archive-committee-dialog"
    :title="archiving ? `Archive ${committee.name}?` : `Bring ${committee.name} back?`"
    :working="working"
    :working-label="archiving ? 'Archiving' : 'Bringing it back'"
    @confirm="confirm"
    @update:open="emit('update:open', $event)"
  />
</template>
