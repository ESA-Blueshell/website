<script lang="ts" setup>
/* Every board, newest first: its year, its members and where it stands. Opening one renders the
   site's own board editor inside Management. */
import {computed, ref} from "vue"
import SearchBox from "@/components/island/SearchBox.vue"
import StateMark from "@/components/island/StateMark.vue"
import {type Board, type BoardStanding, academicYear, boardName, standingOf, useBoards} from "@/domains/boards"

defineOptions({name: "BoardListPage"})

const {boards, loading} = useBoards()
const search = ref("")

const STANDING: Record<BoardStanding, {kind: "in-step" | "not-created" | "not-compared"; word: string}> = {
  "in office": {kind: "in-step", word: "In office"},
  candidate: {kind: "not-created", word: "Kandi"},
  past: {kind: "not-compared", word: "Handed over"},
}

const shown = computed(() => {
  const needle = search.value.trim().toLowerCase()
  return boards.value.filter((board) => needle === "" ||
    [boardName(board.number, board.name), academicYear(board.startDate, board.endDate), ...board.members.map((one) => one.name ?? "")]
      .some((value) => value.toLowerCase().includes(needle)))
})
const standing = (board: Board) => STANDING[standingOf(board, boards.value)]
const people = (board: Board) => `${board.members.length} ${board.members.length === 1 ? "member" : "members"}`
</script>

<template>
  <div
    class="boards"
    data-testid="board-list"
  >
    <header class="boards__head">
      <div>
        <p class="boards__eyebrow">
          Content
        </p>
        <h1 class="boards__title">
          Board
        </h1>
        <p class="boards__note">
          Every board the association has had, the one in office and the one about to take over.
        </p>
      </div>
      <router-link
        class="boards__action"
        data-testid="board-list-new"
        to="/management/boards/new"
      >
        Add a board
      </router-link>
    </header>

    <search-box
      v-model="search"
      label="Search boards"
      testid="board-list-search"
    />

    <p
      v-if="!loading && shown.length === 0"
      class="boards__note"
      data-testid="board-list-empty"
    >
      No board matches.
    </p>

    <ul class="boards__rows">
      <li
        v-for="board in shown"
        :key="board.number"
        class="boards__row"
        :data-testid="`board-row-${board.number}`"
      >
        <span class="boards__name">
          <router-link :to="`/management/boards/${board.number}`">{{ boardName(board.number, board.name) }}</router-link>
        </span>
        <span class="boards__sub">{{ academicYear(board.startDate, board.endDate) }}</span>
        <span class="boards__sub">{{ people(board) }}</span>
        <state-mark
          :kind="standing(board).kind"
          :testid="`board-standing-${board.number}`"
        >
          {{ standing(board).word }}
        </state-mark>
      </li>
    </ul>
  </div>
</template>

<style scoped>
.boards {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  max-width: 76rem;
  padding: 2rem 2.4rem 3rem;
}

.boards__head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: 1rem;
}

.boards__eyebrow {
  margin: 0;
  font-size: 11px;
  letter-spacing: 0.3em;
  text-transform: uppercase;
  color: var(--color-eyebrow, var(--color-ash));
}

.boards__title {
  margin: 0;
  font-family: var(--font-display);
  font-size: clamp(1.4rem, 3vw, 2rem);
}

.boards__note {
  margin: 0;
  max-width: 48rem;
  color: var(--color-ash);
}

.boards__action {
  padding: 0.45rem 0.9rem;
  border: 1px solid var(--color-hairline);
  font-size: 0.86rem;
  color: var(--color-chalk);
  text-decoration: none;
}

.boards__rows {
  display: flex;
  flex-direction: column;
  margin: 0;
  padding: 0;
  list-style: none;
  border-top: 1px solid var(--color-hairline);
}

.boards__row {
  display: grid;
  grid-template-columns: minmax(0, 1.4fr) 7rem 7rem 9rem;
  align-items: center;
  gap: 1rem;
  padding: 0.65rem 0.4rem;
  border-bottom: 1px solid var(--color-hairline);
}

.boards__name {
  overflow: hidden;
  font-weight: 600;
  text-overflow: ellipsis;
}

.boards__name a {
  color: var(--color-chalk);
}

.boards__sub {
  overflow: hidden;
  font-size: 0.84rem;
  color: var(--color-ash);
  text-overflow: ellipsis;
  white-space: nowrap;
}

@media (max-width: 839px) {
  .boards {
    padding: 1.2rem 1.1rem 2rem;
  }

  .boards__row {
    grid-template-columns: minmax(0, 1fr) auto;
  }
}
</style>
