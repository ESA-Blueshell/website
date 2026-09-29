<script lang="ts" setup generic="T">
import {computed, ref, watch} from "vue"
import ConfirmDialog from "./ConfirmDialog.vue"

/**
 * Archiving a record, or bringing one back, said plainly before it happens. What is archived,
 * what it does and how it is saved are the caller's; the asking, the working state and a refusal
 * said in the api's words are this dialog's.
 */
defineOptions({name: "ArchiveDialog"})

const props = defineProps<{
  open: boolean
  name: string
  archived: boolean
  /** What archiving it does, said before it happens. */
  leaving: string
  /** What bringing it back does. */
  returning: string
  save: (archived: boolean) => Promise<{ok: true; saved: T} | {ok: false; reason: string}>
  testid: string
}>()

const emit = defineEmits<{
  (event: "update:open", open: boolean): void
  (event: "saved", saved: T): void
}>()

const working = ref(false)
const failure = ref<string | null>(null)
watch(() => props.open, open => { if (open) failure.value = null })

const archiving = computed(() => !props.archived)

const confirm = async () => {
  if (working.value) return
  working.value = true
  failure.value = null
  try {
    const result = await props.save(archiving.value)
    if (!result.ok) {
      failure.value = result.reason
      return
    }
    emit("saved", result.saved)
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
    :question="archiving ? leaving : returning"
    :testid="testid"
    :title="archiving ? `Archive ${name}?` : `Bring ${name} back?`"
    :working="working"
    :working-label="archiving ? 'Archiving' : 'Bringing it back'"
    @confirm="confirm"
    @update:open="emit('update:open', $event)"
  />
</template>
