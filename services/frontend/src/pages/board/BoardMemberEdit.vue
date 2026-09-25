<script lang="ts" setup>
import {computed} from "vue"
import {useRoute, useRouter} from "vue-router"
import BoardMemberEditor from "@/domains/boards/components/BoardMemberEditor.vue"
import {useBoards, useMayEditBoards, type Board, type BoardMember} from "@/domains/boards"
import {useReturnTo} from "@/composables/useReturnTo"
import NotFound from "@/pages/NotFound.vue"

/** A member added to a board or corrected on their own page, and back to that board. */
defineOptions({name: "BoardMemberEditPage"})

const route = useRoute()
const router = useRouter()
const {boards, loading, refresh} = useBoards()
const mayEdit = useMayEditBoards()

const board = computed<Board | null>(() => boards.value.find(one => one.number === Number(route.params.number)) ?? null)
const adding = computed(() => route.params.member == null)
const member = computed<BoardMember | null>(() => board.value?.members?.find(one => one.id === Number(route.params.member)) ?? null)
const onBoard = `/board?board=${String(route.params.number)}`
const back = useReturnTo(onBoard)

/* The board page draws from the one list, so it is read again before the page shows it. */
const done = async () => {
  await refresh()
  void router.replace(onBoard)
}
</script>

<template>
  <board-member-editor
    v-if="mayEdit && board && (adding || member)"
    :back="back"
    :board="board"
    :member="adding ? null : member"
    @cancel="router.replace(back)"
    @removed="done"
    @saved="done"
  />
  <not-found v-else-if="!loading" />
</template>
