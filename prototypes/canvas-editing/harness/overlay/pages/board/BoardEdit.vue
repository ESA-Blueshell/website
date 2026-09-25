<script lang="ts" setup>
import {computed} from "vue"
import {useRoute, useRouter} from "vue-router"
import BoardEditor from "@/domains/boards/components/BoardEditor.vue"
import {nextBoardNumber, useBoards, useMayEditBoards, type Board} from "@/domains/boards"
import {useReturnTo} from "@/composables/useReturnTo"
import NotFound from "@/pages/NotFound.vue"

defineOptions({name: "BoardEditPage"})

/** A board added or corrected on its own page, and back to the board page on that board. */
const route = useRoute()
const router = useRouter()
const {boards, loading} = useBoards()
const mayEdit = useMayEditBoards()

const adding = computed(() => route.params.number == null)
const board = computed<Board | null>(() => boards.value.find(one => one.number === Number(route.params.number)) ?? null)
const back = useReturnTo(adding.value ? "/board" : `/board?board=${String(route.params.number)}`)

const saved = (now: Board) => void router.replace(`/board?board=${now.number}`)
</script>

<template>
  <board-editor
    v-if="mayEdit && !loading && (adding || board)"
    :back="back"
    :board="adding ? null : board"
    :boards="boards"
    :next-number="nextBoardNumber(boards)"
    @cancel="router.replace(back)"
    @removed="router.replace('/board')"
    @saved="saved"
  />
  <not-found v-else-if="!loading" />
</template>
