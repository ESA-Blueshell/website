<script lang="ts" setup>
import {computed, onMounted, ref} from "vue"
import ManagerCard from "@/components/common/cards/ManagerCard.vue"
import TopBanner from "@/components/common/banners/TopBanner.vue"
import UserPicker from "@/components/form/fields/UserPicker.vue"
import {$handleNetworkError} from "@/plugins/handleNetworkError"
import {
  addSeat,
  boardTitle,
  dropBoard,
  dropSeat,
  linkSeatMember,
  loadBoards,
  saveBoard,
  saveSeat,
  type Board,
  type BoardSeat,
} from "@/domains/boards/adapters/boards"

defineOptions({name: "BoardManagerPage"})

const boards = ref<Board[]>([])
const boardId = ref<number | null>(null)

type BoardDraft = {
  id?: number
  number: number
  name: string
  candidate: string
  cheer: string
  accent: string
  description: string
  startDate: string
  endDate: string
  image: string
  version?: number
}

type SeatDraft = {
  id?: number
  role: string
  startDate: string
  endDate: string
  displayName: string
  nickname: string
  description: string
  image: string
}

const blankBoard = (): BoardDraft => ({
  number: (boards.value[0]?.number ?? 0) + 1,
  name: "", candidate: "", cheer: "", accent: "", description: "",
  startDate: "", endDate: "", image: "",
})

const boardDialog = ref<boolean>(false)
const boardDraft = ref<BoardDraft>(blankBoard())

const seatDialog = ref<boolean>(false)
const seatDraft = ref<SeatDraft>({
  role: "", startDate: "", endDate: "", displayName: "", nickname: "", description: "", image: "",
})

const linkDialog = ref<boolean>(false)
const linkSeat = ref<BoardSeat | null>(null)
const linkUserId = ref<number | null>(null)

const selected = computed<Board | null>(
  () => boards.value.find((board) => board.id === boardId.value) ?? null,
)

const refresh = async () => {
  try {
    boards.value = await loadBoards()
    if (!boards.value.some((board) => board.id === boardId.value)) {
      boardId.value = boards.value[0]?.id ?? null
    }
  } catch (error) {
    $handleNetworkError(error)
  }
}

const openBoard = (board?: Board) => {
  boardDraft.value = board
    ? {
      id: board.id,
      number: board.number,
      name: board.name ?? "",
      candidate: board.candidate,
      cheer: board.cheer ?? "",
      accent: board.accent ?? "",
      description: board.description ?? "",
      startDate: board.startDate,
      endDate: board.endDate ?? "",
      image: board.image ?? "",
      version: board.version,
    }
    : blankBoard()
  boardDialog.value = true
}

const submitBoard = async () => {
  try {
    const draft = boardDraft.value
    await saveBoard({
      ...draft,
      name: draft.name || null,
      candidate: draft.candidate || null,
      cheer: draft.cheer || null,
      accent: draft.accent || null,
      description: draft.description || null,
      endDate: draft.endDate || null,
      image: draft.image || null,
    })
    boardDialog.value = false
    await refresh()
  } catch (error) {
    $handleNetworkError(error)
  }
}

const removeBoard = async (board: Board) => {
  try {
    await dropBoard(board.id)
    await refresh()
  } catch (error) {
    $handleNetworkError(error)
  }
}

const openSeat = (seat?: BoardSeat) => {
  const board = selected.value
  seatDraft.value = seat
    ? {
      id: seat.id,
      role: seat.role,
      startDate: seat.startDate,
      endDate: seat.endDate ?? "",
      displayName: seat.name ?? "",
      nickname: seat.nickname ?? "",
      description: seat.description ?? "",
      image: seat.image ?? "",
    }
    : {
      role: "",
      startDate: board?.startDate ?? "",
      endDate: board?.endDate ?? "",
      displayName: "",
      nickname: "",
      description: "",
      image: "",
    }
  seatDialog.value = true
}

const submitSeat = async () => {
  const board = selected.value
  if (board == null) return
  try {
    const draft = seatDraft.value
    const payload = {
      role: draft.role,
      startDate: draft.startDate,
      endDate: draft.endDate || null,
      displayName: draft.displayName || null,
      nickname: draft.nickname || null,
      description: draft.description || null,
      image: draft.image || null,
    }
    if (draft.id == null) {
      await addSeat(board.id, payload)
    } else {
      await saveSeat(board.id, draft.id, payload)
    }
    seatDialog.value = false
    await refresh()
  } catch (error) {
    $handleNetworkError(error)
  }
}

const removeSeat = async (seat: BoardSeat) => {
  const board = selected.value
  if (board == null) return
  try {
    await dropSeat(board.id, seat.id)
    await refresh()
  } catch (error) {
    $handleNetworkError(error)
  }
}

const openLink = (seat: BoardSeat) => {
  linkSeat.value = seat
  linkUserId.value = seat.userId ?? null
  linkDialog.value = true
}

const applyLink = async (seat: BoardSeat, userId: number | null) => {
  const board = selected.value
  if (board == null) return
  try {
    await linkSeatMember(board.id, seat.id, userId)
    linkDialog.value = false
    await refresh()
  } catch (error) {
    $handleNetworkError(error)
  }
}

const submitLink = async (userId: number | null) => {
  const seat = linkSeat.value
  if (seat != null) await applyLink(seat, userId)
}

const detachSeat = (seat: BoardSeat) => applyLink(seat, null)

onMounted(refresh)
</script>

<template>
  <v-main>
    <top-banner title="Boards" />

    <v-container>
      <div class="mx-auto my-3 board-manager">
        <manager-card
          eyebrow="Boards"
          flush
          spaced
          testid="board-list"
        >
          <template #actions>
            <v-btn
              data-testid="board-add"
              prepend-icon="mdi-plus"
              size="small"
              variant="text"
              @click="openBoard()"
            >
              Add board
            </v-btn>
          </template>

          <v-table
            class="manager-table"
            density="compact"
          >
            <thead>
              <tr>
                <th style="width: 8%">
                  No.
                </th>
                <th style="width: 28%">
                  Board
                </th>
                <th style="width: 20%">
                  Starts
                </th>
                <th style="width: 20%">
                  Ends
                </th>
                <th style="width: 12%">
                  Seats
                </th>
                <th style="width: 12%" />
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="board in boards"
                :key="board.id"
                class="manager-table__row"
                :class="{'board--selected': board.id === boardId}"
                :data-testid="`board-row-${board.id}`"
                @click="boardId = board.id"
              >
                <td class="text-medium-emphasis">
                  {{ board.number }}
                </td>
                <td class="font-weight-medium">
                  {{ boardTitle(board) }}
                </td>
                <td class="text-medium-emphasis">
                  {{ board.startDate }}
                </td>
                <td class="text-medium-emphasis">
                  {{ board.endDate ?? "—" }}
                </td>
                <td class="text-medium-emphasis">
                  {{ board.members.length }}
                </td>
                <td class="text-right">
                  <v-menu location="bottom end">
                    <template #activator="{props: menuProps}">
                      <v-btn
                        v-bind="menuProps"
                        :aria-label="`${boardTitle(board)} actions`"
                        :data-testid="`board-menu-${board.id}`"
                        icon="mdi-dots-vertical"
                        size="small"
                        variant="text"
                        @click.stop
                      />
                    </template>
                    <v-list density="compact">
                      <v-list-item
                        :data-testid="`board-edit-${board.id}`"
                        prepend-icon="mdi-pencil"
                        title="Edit"
                        @click="openBoard(board)"
                      />
                      <v-list-item
                        prepend-icon="mdi-delete"
                        title="Delete"
                        @click="removeBoard(board)"
                      />
                    </v-list>
                  </v-menu>
                </td>
              </tr>
            </tbody>
          </v-table>
        </manager-card>

        <manager-card
          :eyebrow="selected ? `${boardTitle(selected)} seats` : 'Seats'"
          flush
          testid="board-seats"
        >
          <template #actions>
            <v-btn
              :disabled="selected == null"
              data-testid="board-add-seat"
              prepend-icon="mdi-plus"
              size="small"
              variant="text"
              @click="openSeat()"
            >
              Add seat
            </v-btn>
          </template>

          <p
            v-if="selected == null"
            class="text-body-2 text-medium-emphasis pa-4 mb-0"
          >
            Pick a board to edit its seats.
          </p>

          <v-table
            v-else
            class="manager-table"
            data-testid="board-seat-table"
            density="compact"
          >
            <thead>
              <tr>
                <th style="width: 28%">
                  Name
                </th>
                <th style="width: 34%">
                  Title
                </th>
                <th style="width: 16%">
                  Member
                </th>
                <th style="width: 12%">
                  Blurb
                </th>
                <th style="width: 10%" />
              </tr>
            </thead>
            <tbody>
              <tr v-if="selected.members.length === 0">
                <td
                  class="text-medium-emphasis"
                  colspan="5"
                >
                  Nobody on this board yet.
                </td>
              </tr>
              <tr
                v-for="seat in selected.members"
                :key="seat.id"
                :data-testid="`board-seat-row-${seat.id}`"
              >
                <td class="font-weight-medium">
                  {{ seat.name ?? "—" }}
                </td>
                <td class="text-medium-emphasis">
                  {{ seat.role }}
                </td>
                <td>
                  <v-chip
                    :color="seat.userId == null ? 'warning' : undefined"
                    size="small"
                    :variant="seat.userId == null ? 'flat' : 'tonal'"
                  >
                    {{ seat.userId == null ? "Unlinked" : `#${seat.userId}` }}
                  </v-chip>
                </td>
                <td class="text-medium-emphasis">
                  {{ seat.description ? "Yes" : "—" }}
                </td>
                <td class="text-right">
                  <v-menu location="bottom end">
                    <template #activator="{props: menuProps}">
                      <v-btn
                        v-bind="menuProps"
                        :aria-label="`${seat.name ?? seat.role} actions`"
                        :data-testid="`board-seat-menu-${seat.id}`"
                        icon="mdi-dots-vertical"
                        size="small"
                        variant="text"
                      />
                    </template>
                    <v-list density="compact">
                      <v-list-item
                        prepend-icon="mdi-pencil"
                        title="Edit"
                        @click="openSeat(seat)"
                      />
                      <v-list-item
                        :data-testid="`board-seat-link-${seat.id}`"
                        prepend-icon="mdi-account-search"
                        title="Link a member"
                        @click="openLink(seat)"
                      />
                      <v-list-item
                        v-if="seat.userId != null"
                        :data-testid="`board-seat-unlink-${seat.id}`"
                        prepend-icon="mdi-account-off"
                        title="Detach"
                        @click="detachSeat(seat)"
                      />
                      <v-list-item
                        prepend-icon="mdi-delete"
                        title="Remove"
                        @click="removeSeat(seat)"
                      />
                    </v-list>
                  </v-menu>
                </td>
              </tr>
            </tbody>
          </v-table>
        </manager-card>
      </div>
    </v-container>

    <v-dialog
      v-model="boardDialog"
      max-width="520"
    >
      <v-card data-testid="board-dialog">
        <v-card-title>{{ boardDraft.id == null ? "Add board" : "Edit board" }}</v-card-title>
        <v-card-text>
          <v-text-field
            v-model.number="boardDraft.number"
            data-testid="board-number"
            hint="The board's place in the line; the ninth board is 9"
            label="Number"
            persistent-hint
            type="number"
          />
          <v-text-field
            v-model="boardDraft.name"
            data-testid="board-name"
            hint="The name the board chose for itself; blank for a board with none recorded"
            label="Name"
            persistent-hint
          />
          <v-text-field
            v-model="boardDraft.cheer"
            data-testid="board-cheer"
            hint="The line the board shouts"
            label="Cheer"
            persistent-hint
          />
          <v-text-field
            v-model="boardDraft.accent"
            data-testid="board-accent"
            hint="The board's own colour; blank means the association's blue"
            label="Colour"
            persistent-hint
          />
          <v-textarea
            v-model="boardDraft.description"
            data-testid="board-description"
            label="Description"
            rows="3"
          />
          <v-text-field
            v-model="boardDraft.candidate"
            hint="Kept for the column behind it; the board's own name is used when blank"
            label="Candidate"
            persistent-hint
          />
          <v-text-field
            v-model="boardDraft.startDate"
            label="Starts"
            type="date"
          />
          <v-text-field
            v-model="boardDraft.endDate"
            label="Ends"
            type="date"
          />
          <v-text-field
            v-model="boardDraft.image"
            hint="Asset file name, e.g. board9/board9.jpg"
            label="Photograph"
            persistent-hint
          />
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn @click="boardDialog = false">
            Cancel
          </v-btn>
          <v-btn
            color="primary"
            data-testid="board-save"
            variant="flat"
            @click="submitBoard"
          >
            Save
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <v-dialog
      v-model="seatDialog"
      max-width="620"
    >
      <v-card data-testid="board-seat-dialog">
        <v-card-title>{{ seatDraft.id == null ? "Add seat" : "Edit seat" }}</v-card-title>
        <v-card-text>
          <v-text-field
            v-model="seatDraft.displayName"
            data-testid="board-seat-name"
            hint="Who held the seat; a linked member's own name is shown instead"
            label="Name"
            persistent-hint
          />
          <v-text-field
            v-model="seatDraft.nickname"
            data-testid="board-seat-nickname"
            hint="The name the seat was known by, without the quotes around it"
            label="Nickname"
            persistent-hint
          />
          <v-text-field
            v-model="seatDraft.role"
            data-testid="board-seat-role"
            label="Title"
          />
          <v-text-field
            v-model="seatDraft.startDate"
            label="Starts"
            type="date"
          />
          <v-text-field
            v-model="seatDraft.endDate"
            label="Ends"
            type="date"
          />
          <v-textarea
            v-model="seatDraft.description"
            label="Blurb"
            rows="4"
          />
          <v-text-field
            v-model="seatDraft.image"
            hint="Asset file name, e.g. board9/Emma.jpg"
            label="Portrait"
            persistent-hint
          />
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn @click="seatDialog = false">
            Cancel
          </v-btn>
          <v-btn
            color="primary"
            data-testid="board-seat-save"
            variant="flat"
            @click="submitSeat"
          >
            Save
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <v-dialog
      v-model="linkDialog"
      max-width="520"
    >
      <v-card data-testid="board-link-dialog">
        <v-card-title>Link a member</v-card-title>
        <v-card-text>
          <user-picker
            v-model="linkUserId"
            label="Member"
          />
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn @click="linkDialog = false">
            Cancel
          </v-btn>
          <v-btn
            color="primary"
            data-testid="board-link-save"
            variant="flat"
            @click="submitLink(linkUserId)"
          >
            Link
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </v-main>
</template>

<style lang="scss" scoped>
.board-manager {
  max-width: 980px;
}

.board--selected > td {
  background: rgba(var(--v-theme-primary), 0.08);
}
</style>
