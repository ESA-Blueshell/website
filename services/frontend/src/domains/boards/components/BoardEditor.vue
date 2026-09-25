<script lang="ts" setup>
import {computed, ref, watch} from "vue"
import ColourControl from "@/components/island/ColourControl.vue"
import {isHexColour} from "@/components/island/colour"
import ConfirmDialog from "@/components/island/ConfirmDialog.vue"
import CutButton from "@/components/island/CutButton.vue"
import EditPage from "@/components/island/EditPage.vue"
import FormControl from "@/components/island/FormControl.vue"
import FormFields from "@/components/island/FormFields.vue"
import FormSection from "@/components/island/FormSection.vue"
import ImagePicker from "@/components/island/ImagePicker.vue"
import NoticeBox from "@/components/island/NoticeBox.vue"
import PreviewFrame from "@/components/island/PreviewFrame.vue"
import Timeline from "@/components/island/Timeline.vue"
import type {Picture} from "@/components/island/pictures"
import {dropBoard, saveBoardOrReason, storeBoardPhoto, type Board} from "../adapters/boards"
import {boardStops} from "../boardAxis"
import {countOf} from "../copy"
import BoardBand from "../island/BoardBand.vue"
import {academicYear, boardEyebrow, boardName} from "../reading"

/**
 * A board written down or corrected on its own page, with the timeline and the board's band drawn
 * beside the form as the board page will show them. A refusal keeps what was typed.
 */
defineOptions({name: "BoardEditor"})

const props = defineProps<{
  /** The board being corrected, or nothing where one is being added. */
  board: Board | null
  /** Every board, for the timeline the preview draws. */
  boards: Board[]
  /** The number a board being added is suggested, read off the line. */
  nextNumber: number
  back: string
}>()

const emit = defineEmits<{
  (event: "saved", board: Board): void
  (event: "removed"): void
  (event: "cancel"): void
}>()

const adding = computed(() => props.board == null)

const number = ref("1")
const name = ref("")
const cheer = ref("")
const colour = ref("")
const description = ref("")
const startDate = ref("")
const endDate = ref("")
const photo = ref<Picture | null>(null)
const failure = ref<string | null>(null)
const saving = ref(false)

const dayOf = (date?: string | null): string => (date ?? "").trim().slice(0, 10)

watch(() => [props.board, props.nextNumber] as const, ([board, next]) => {
  number.value = String(board?.number ?? next)
  name.value = board?.name ?? ""
  cheer.value = board?.cheer ?? ""
  colour.value = board?.accent ?? ""
  description.value = board?.description ?? ""
  startDate.value = dayOf(board?.startDate)
  endDate.value = dayOf(board?.endDate)
  photo.value = board?.photo ?? null
  failure.value = null
}, {immediate: true})

const numbered = computed(() => Number(number.value))
const colourOk = computed(() => colour.value.trim() === "" || isHexColour(colour.value.trim()))
const accent = computed(() => (colourOk.value && colour.value.trim()) || "var(--color-brand)")
const complete = computed(() => Number.isInteger(numbered.value) && numbered.value > 0 && startDate.value !== "" && colourOk.value)

/** The board as the page will draw it, from what is typed now. */
const drafted = computed<Board>(() => ({
  ...(props.board ?? {id: -1, members: [], version: 0}),
  number: complete.value ? numbered.value : props.board?.number ?? props.nextNumber,
  name: name.value.trim() || null,
  cheer: cheer.value.trim() || null,
  accent: (colourOk.value && colour.value.trim()) || null,
  description: description.value.trim() || null,
  startDate: startDate.value || props.board?.startDate || new Date().toISOString().slice(0, 10),
  endDate: endDate.value || null,
  photo: photo.value,
}) as Board)

const stops = computed(() => boardStops([
  ...props.boards.filter(one => one.id !== props.board?.id),
  drafted.value,
]))

const photoLabel = computed(() => {
  const year = academicYear(drafted.value.startDate, drafted.value.endDate)
  const named = boardName(drafted.value.number, drafted.value.name)
  return year ? `${named}, ${year}` : named
})

const confirming = ref(false)
const removing = ref(false)
const removalFailure = ref<string | null>(null)

const question = computed(() => {
  const board = props.board
  if (!board) return ""
  const named = boardName(board.number, board.name)
  const held = board.members.length
  if (held === 0) return `${named} holds no members. Removing it takes it off the timeline.`
  return `${named} holds ${countOf(held, "member", "members")}, and every one of them `
    + "is somebody's place in the association's history."
})

const removeBoard = async () => {
  const board = props.board
  if (!board || removing.value) return
  removing.value = true
  removalFailure.value = null
  try {
    const result = await dropBoard(board.id)
    if (!result.ok) {
      removalFailure.value = result.reason
      return
    }
    confirming.value = false
    emit("removed")
  } finally {
    removing.value = false
  }
}

const submit = async () => {
  if (!complete.value || saving.value) return
  saving.value = true
  failure.value = null
  try {
    const result = await saveBoardOrReason({
      id: props.board?.id,
      number: numbered.value,
      name: name.value.trim() || null,
      cheer: cheer.value.trim() || null,
      accent: colour.value.trim() || null,
      description: description.value.trim() || null,
      startDate: startDate.value,
      endDate: endDate.value || null,
      photo: photo.value?.path ?? null,
      version: props.board?.version,
    })
    if (!result.ok) {
      failure.value = result.reason
      return
    }
    emit("saved", result.board)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <edit-page
    :accent="accent"
    :back="{to: back, label: 'Board'}"
    :eyebrow="board ? boardName(board.number, board.name) : 'Board'"
    testid="board-edit"
    :title="adding ? 'Add a board' : 'Edit board'"
  >
    <template
      v-if="board"
      #actions
    >
      <cut-button
        testid="board-edit-remove"
        tone="danger"
        @click="confirming = true"
      >
        Remove board
      </cut-button>
    </template>

    <form
      class="board-editor"
      @submit.prevent="submit"
    >
      <form-section title="The board">
        <form-fields>
          <div class="form-span">
            <image-picker
              label="Board photo"
              :picture="photo"
              :store="storeBoardPhoto"
              testid="board-edit-photo"
              @update:picture="photo = $event"
            />
          </div>
          <form-control
            v-model="number"
            kind="count"
            label="Number*"
            testid="board-edit-number"
          />
          <form-control
            v-model="name"
            label="Name"
            testid="board-edit-name"
          />
          <form-control
            v-model="cheer"
            label="Cheer"
            testid="board-edit-cheer"
          />
          <colour-control
            v-model="colour"
            label="Colour"
            placeholder="#3387fa"
            testid="board-edit-accent"
          />
          <div class="form-span">
            <form-control
              v-model="description"
              kind="markdown"
              label="Description"
              :max-length="4000"
              testid="board-edit-description"
            />
          </div>
        </form-fields>
      </form-section>

      <form-section title="Term">
        <form-fields>
          <form-control
            v-model="startDate"
            kind="date"
            label="Takes office*"
            testid="board-edit-start"
          />
          <form-control
            v-model="endDate"
            kind="date"
            label="Hands over"
            testid="board-edit-end"
          />
        </form-fields>
      </form-section>

      <notice-box
        v-if="failure"
        testid="board-edit-failure"
        tone="danger"
      >
        {{ failure }}
      </notice-box>
    </form>

    <template #footer>
      <div class="board-editor__save">
        <cut-button
          testid="board-edit-cancel"
          tone="quiet"
          @click="emit('cancel')"
        >
          Cancel
        </cut-button>
        <cut-button
          :disabled="!complete || saving"
          testid="board-edit-save"
          tone="solid"
          @click="submit"
        >
          {{ saving ? "Saving" : adding ? "Add the board" : "Save" }}
        </cut-button>
      </div>
    </template>

    <template #preview>
      <preview-frame>
        <timeline
          :accent="accent"
          :selected-id="drafted.number"
          :stops="stops"
          testid-prefix="board-edit-preview"
        />
        <div
          class="board-editor__page"
          :style="{'--accent': accent}"
        >
          <board-band
            :cheer="drafted.cheer ?? ''"
            :description="drafted.description ?? ''"
            :eyebrow="boardEyebrow(drafted.number, drafted.startDate, drafted.endDate)"
            :label="photoLabel"
            :name="drafted.name ?? ''"
            :photo="photo"
            testid="board-edit-preview-band"
          />
        </div>
      </preview-frame>
    </template>
  </edit-page>

  <confirm-dialog
    v-if="board"
    :accent="accent"
    confirm-label="Remove the board"
    :failure="removalFailure"
    :open="confirming"
    :question="question"
    testid="board-remove-dialog"
    title="Remove this board?"
    :working="removing"
    working-label="Removing"
    @confirm="removeBoard"
    @update:open="confirming = $event"
  />
</template>

<style scoped>
.board-editor__save {
  display: flex;
  gap: 0.5rem;
  justify-content: flex-end;
}

/* The board page's own reading of the colour, which the band inks its words in. */
.board-editor__page {
  --accent-ink: color-mix(in oklab, var(--accent) 86%, var(--color-chalk));
}

:where([data-theme="light"]) .board-editor__page {
  --accent-ink: color-mix(in oklab, var(--accent) 62%, var(--color-chalk));
}
</style>
