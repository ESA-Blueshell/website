<script lang="ts" setup>
import {computed, ref, watch} from "vue"
import ConfirmDialog from "@/components/island/ConfirmDialog.vue"
import {setGameArchived, type CasualGame} from "../adapters/games"

/**
 * Archiving a game, or bringing one back, said plainly before it happens. Archiving is casual
 * only: a game fielded in competition stays there.
 */
defineOptions({name: "ArchiveGameDialog"})

const props = defineProps<{open: boolean; game: CasualGame}>()

const emit = defineEmits<{
  (event: "update:open", open: boolean): void
  (event: "saved", game: CasualGame): void
}>()

const working = ref(false)
const failure = ref<string | null>(null)
watch(() => props.open, open => { if (open) failure.value = null })

const archiving = computed(() => !props.game.archived)

const question = computed(() =>
  archiving.value
    ? `${props.game.name} leaves the reel and the pickers, keeps its page and joins the games we used to play. `
      + "Events and committees that already name it keep naming it."
    : `${props.game.name} goes back on the reel and into the pickers, among the games we play.`)

const confirm = async () => {
  if (working.value) return
  working.value = true
  failure.value = null
  try {
    const result = await setGameArchived(props.game.code, archiving.value)
    if (!result.ok) {
      failure.value = result.reason
      return
    }
    emit("saved", result.game)
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
    testid="archive-game-dialog"
    :title="archiving ? `Archive ${game.name}?` : `Bring ${game.name} back?`"
    :working="working"
    :working-label="archiving ? 'Archiving' : 'Bringing it back'"
    @confirm="confirm"
    @update:open="emit('update:open', $event)"
  />
</template>
