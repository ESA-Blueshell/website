<script lang="ts" setup>
import ArchiveDialog from "@/components/island/ArchiveDialog.vue"
import {setGameArchived, type CasualGame} from "../adapters/games"

/** Archiving a game, or bringing one back. Archiving is casual only: a game fielded in competition stays there. */
defineOptions({name: "ArchiveGameDialog"})

const props = defineProps<{open: boolean; game: CasualGame}>()

const emit = defineEmits<{
  (event: "update:open", open: boolean): void
  (event: "saved", game: CasualGame): void
}>()

const save = async (archived: boolean) => {
  const result = await setGameArchived(props.game.code, archived)
  return result.ok ? {ok: true as const, saved: result.game} : result
}
</script>

<template>
  <archive-dialog
    :archived="game.archived"
    :leaving="`${game.name} leaves the reel and the pickers, keeps its page and joins the games we used to play. `
      + 'Events and committees that already name it keep naming it.'"
    :name="game.name"
    :open="open"
    :returning="`${game.name} goes back on the reel and into the pickers, among the games we play.`"
    :save="save"
    testid="archive-game-dialog"
    @saved="emit('saved', $event)"
    @update:open="emit('update:open', $event)"
  />
</template>
