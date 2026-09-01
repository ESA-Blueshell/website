<script lang="ts" setup>
import {computed, onMounted, reactive, ref} from "vue"
import TopBanner from "@/components/common/banners/TopBanner.vue"
import BoardMemberRow from "@/components/common/rows/BoardMemberRow.vue"
import {$require} from "@/plugins/require"
import {$handleNetworkError} from "@/plugins/handleNetworkError"
import {boardTitle, loadBoards, seatTitle, type Board} from "@/domains/boards/adapters/boards"

defineOptions({name: "BoardPage"})

const boards = ref<Board[]>([])
const loading = ref<boolean>(true)
const expandedBoards = reactive<Record<number, boolean>>({})

/** The board in office: the one the association is currently run by, shown open. */
const sitting = computed<Board | null>(() => boards.value[0] ?? null)
const previous = computed<Board[]>(() => boards.value.slice(1))

const asset = (image?: string | null) => (image ? $require(`@/assets/${image}`) : "")

const seatsOf = (board: Board) =>
  board.members.map((seat) => ({
    name: seatTitle(seat),
    title: seat.role,
    description: seat.description ?? undefined,
    image: asset(seat.image),
  }))

const toggleBoard = (id: number) => {
  expandedBoards[id] = !expandedBoards[id]
}

onMounted(async () => {
  try {
    boards.value = await loadBoards()
  } catch (error) {
    $handleNetworkError(error)
  } finally {
    loading.value = false
  }
})
</script>

<template>
  <v-main>
    <top-banner title="Board" />

    <div
      class="mx-auto board-page"
      style="max-width: 800px"
    >
      <v-progress-circular
        v-if="loading"
        class="d-block mx-auto my-16"
        indeterminate
      />

      <p
        v-else-if="boards.length === 0"
        class="text-body-1 text-center my-16"
        data-testid="board-empty"
      >
        No boards recorded yet.
      </p>

      <template v-else>
        <!-- The board in office, open, with the ones before it behind their own headings. -->
        <section
          v-if="sitting"
          class="mt-3 mb-5 board-sitting"
          :data-testid="`board-${sitting.id}`"
        >
          <h1 class="text-center">
            {{ boardTitle(sitting) }}
          </h1>

          <v-img
            v-if="asset(sitting.image)"
            class="rounded-lg board-photo"
            cover
            eager
            :src="asset(sitting.image)"
          />

          <board-member-row
            v-for="(member, index) in seatsOf(sitting)"
            :key="`${sitting.id}-${member.name}-${index}`"
            class="my-10 board-seat"
            :member="member"
            :reverse="index % 2 === 1"
            :style="{'--stagger': `${index * 70}ms`}"
          />
        </section>

        <section
          v-for="board in previous"
          :key="board.id"
          class="mt-3 mb-5"
          :data-testid="`board-${board.id}`"
        >
          <v-card
            :aria-expanded="String(!!expandedBoards[board.id])"
            class="px-5 board-toggle"
            :data-testid="`board-toggle-${board.id}`"
            role="button"
            @click="toggleBoard(board.id)"
          >
            <h2>{{ boardTitle(board) }}</h2>
            <v-icon
              class="board-chevron"
              :class="{'board-chevron--open': expandedBoards[board.id]}"
              color="grey-darken-1"
              size="24"
            >
              mdi-chevron-down
            </v-icon>
          </v-card>

          <v-expand-transition>
            <div v-show="expandedBoards[board.id]">
              <v-img
                v-if="asset(board.image)"
                class="rounded-lg mt-2 board-photo"
                cover
                eager
                :src="asset(board.image)"
              />

              <board-member-row
                v-for="(member, index) in seatsOf(board)"
                :key="`${board.id}-${member.name}-${index}`"
                class="my-16 board-seat"
                :member="member"
                :reverse="index % 2 === 1"
                :style="{'--stagger': `${index * 70}ms`}"
              />
            </div>
          </v-expand-transition>
        </section>
      </template>
    </div>
  </v-main>
</template>

<style lang="scss" scoped>
.board-toggle {
  display: flex;
  align-items: center;
  justify-content: space-between;
  cursor: pointer;
  transition: transform 180ms ease, box-shadow 180ms ease;

  &:hover {
    transform: translateY(-2px);
    box-shadow: 0 6px 18px rgba(0, 0, 0, 0.18);
  }
}

// The chevron turns with the section rather than being swapped for another icon.
.board-chevron {
  transition: transform 240ms ease;
}

.board-chevron--open {
  transform: rotate(180deg);
}

.board-photo {
  animation: board-photo-in 600ms ease both;
}

// A board's seats arrive one after another, so a board opens as a sequence rather than as a
// block of text appearing at once.
.board-seat {
  animation: board-seat-in 520ms cubic-bezier(0.22, 1, 0.36, 1) both;
  animation-delay: var(--stagger, 0ms);
}

@keyframes board-photo-in {
  from {
    opacity: 0;
    transform: scale(1.02);
  }

  to {
    opacity: 1;
    transform: none;
  }
}

@keyframes board-seat-in {
  from {
    opacity: 0;
    transform: translateY(18px);
  }

  to {
    opacity: 1;
    transform: none;
  }
}

@media (prefers-reduced-motion: reduce) {
  .board-toggle:hover {
    transform: none;
    box-shadow: none;
  }

  .board-chevron,
  .board-photo,
  .board-seat {
    animation: none;
    transition: none;
  }
}
</style>
