<script lang="ts" setup>
import {computed, ref, watch} from "vue"
import IslandDialog from "@/components/island/IslandDialog.vue"
import ConfirmDialog from "@/components/island/ConfirmDialog.vue"
import ImagePicker from "@/components/island/ImagePicker.vue"
import IslandPicker from "@/components/island/IslandPicker.vue"
import type {Picture} from "@/components/island/pictures"
import {loadMembers, type Member} from "@/domains/user"
import {
  addMemberOrReason,
  dropMemberOrReason,
  linkMemberAccountOrReason,
  memberTitle,
  saveMemberOrReason,
  storeMemberPortrait,
  type BoardMember,
} from "../adapters/boards"

/**
 * One board membership written down or corrected, from the page it is read on.
 *
 * Everything a membership is: the name it stands under, the nickname the history knows it by,
 * the role in the board's own words, the portrait, the account it belongs to, the stretch it was
 * served and what the person wrote about themselves. One dialog for adding and for
 * correcting, because they are the same fields.
 *
 * A board member is not a person. The name is the membership's own, so somebody who never had an
 * account here is still in the history, and an account is something a membership may
 * additionally have. That link and the serving dates are what the cohort module reads to answer
 * "was on the board that year", which is why the dates are editable rather than silently
 * inherited: a mid-year handover is recorded truthfully rather than credited as a full year.
 *
 * A refusal keeps what was typed. Losing seven fields to find out what the objection was
 * would mean typing them again to ask.
 */
defineOptions({name: "BoardMemberDialog"})

const props = withDefaults(defineProps<{
  open: boolean
  /** The board the membership sits on, which is the only board this dialog writes to. */
  boardId: number | null
  /** The membership being corrected, or nothing where one is being added. */
  member: BoardMember | null
  /** The board's own term, which a new membership is pre-filled from. */
  boardStart?: string | null
  boardEnd?: string | null
  accent?: string
}>(), {boardStart: null, boardEnd: null, accent: undefined})

const emit = defineEmits<{
  (event: "update:open", open: boolean): void
  (event: "saved"): void
  (event: "removed"): void
}>()

const DESCRIPTION_CAP = 4000

const adding = computed(() => props.member == null)

const name = ref("")
/** The membership's own rather than the account's, and recorded apart from the name it sits inside. */
const nickname = ref("")
const role = ref("")
const description = ref("")
const startDate = ref("")
const endDate = ref("")
/** The account the membership belongs to, where it belongs to one. Most of the history does not. */
const userId = ref<number | null>(null)
/**
 * The portrait now held.
 *
 * Chosen bytes go into storage as they are chosen and reach the membership only when this dialog
 * is saved, which is what lets cancelling leave it on the picture it had. The picker's own doc
 * comment is the long version.
 */
const portrait = ref<Picture | null>(null)
const failure = ref<string | null>(null)
const saving = ref(false)

const members = ref<Member[]>([])

/** The day part of a date, so a stored timestamp still fills a date field. */
const dayOf = (date?: string | null): string => (date ?? "").trim().slice(0, 10)

/*
 * Opening fills the form from the membership as it stands, and one being added from the board's
 * own term: the common case is somebody who served the whole year, and it needs no typing.
 *
 * The accounts are read once and kept, because opening the dialog on the next membership asks
 * the same question of the same list.
 */
watch(() => [props.open, props.member] as const, async ([open]) => {
  if (!open) return
  const held = props.member
  name.value = held?.name ?? ""
  nickname.value = held?.nickname ?? ""
  role.value = held?.role ?? ""
  description.value = held?.description ?? ""
  startDate.value = dayOf(held?.startDate ?? props.boardStart)
  endDate.value = dayOf(held?.endDate ?? props.boardEnd)
  userId.value = held?.userId ?? null
  portrait.value = held?.portrait ?? null
  failure.value = null
  if (members.value.length === 0) members.value = await loadMembers()
}, {immediate: true})

const title = computed(() => (props.member ? `Edit ${memberTitle(props.member)}` : "Add a member"))

/** Every account, as the picker asks for them: their name, and their address to tell two apart. */
const memberOptions = computed(() =>
  members.value.map(one => ({key: String(one.id), label: one.name, note: one.email ?? undefined})))

const nameOf = (id: number | null): string =>
  (id == null ? "" : members.value.find(one => one.id === id)?.name ?? `Member ${id}`)

/** How the page will publish the name, so the quoting is visible before it is saved. */
const published = computed(() => (name.value.trim() === ""
  ? ""
  : memberTitle({name: name.value.trim(), nickname: nickname.value.trim() || null})))

/**
 * A membership has to stand under a name and say what it was; the rest of it may be unknown.
 *
 * The dates are the api's own requirement rather than this dialog's: a membership records the
 * stretch it was served, and that is what the association is asked about.
 */
const complete = computed(() =>
  name.value.trim() !== "" && role.value.trim() !== "" && startDate.value !== "")

const confirming = ref(false)
const removing = ref(false)
const removalFailure = ref<string | null>(null)

/** What removing this membership would take with it, said before the question is put. */
const question = computed(() => {
  const held = props.member
  if (!held) return ""
  const who = memberTitle(held)
  const said = held.description?.trim()
    ? " What they wrote about themselves goes with it."
    : ""
  return `${who} held ${held.role} on this board. Removing the member takes it out of the `
    + `association's history.${said}`
})

const askToRemove = () => {
  if (!props.member) return
  failure.value = null
  removalFailure.value = null
  confirming.value = true
}

const removeMember = async () => {
  const held = props.member
  const boardId = props.boardId
  if (!held || boardId == null || removing.value) return
  removing.value = true
  removalFailure.value = null
  try {
    const result = await dropMemberOrReason(boardId, held.id)
    if (!result.ok) {
      // Nothing has gone, so the question stands and says why.
      removalFailure.value = result.reason
      return
    }
    emit("removed")
    confirming.value = false
    emit("update:open", false)
  } finally {
    removing.value = false
  }
}

const submit = async () => {
  const boardId = props.boardId
  if (!complete.value || saving.value || boardId == null) return
  saving.value = true
  failure.value = null
  try {
    const written = {
      role: role.value.trim(),
      startDate: startDate.value,
      endDate: endDate.value || null,
      displayName: name.value.trim(),
      nickname: nickname.value.trim() || null,
      description: description.value.trim() || null,
      // The asset file name the early history still points at, carried through rather than
      // shown: a save replaces every field, so leaving it out would quietly clear it.
      image: props.member?.image ?? null,
      portrait: portrait.value?.path ?? null,
    }
    const held = props.member
    if (held == null) {
      // The account goes with the membership as it is written down: adding takes one request.
      const added = await addMemberOrReason(boardId, {...written, userId: userId.value})
      if (!added.ok) {
        failure.value = added.reason
        return
      }
    } else {
      const saved = await saveMemberOrReason(boardId, held.id, written)
      if (!saved.ok) {
        failure.value = saved.reason
        return
      }
      // The link is its own endpoint, and asked only where it changed: detaching is a null
      // account, and a membership with none carries on standing under its own name.
      if (userId.value !== (held.userId ?? null)) {
        const linked = await linkMemberAccountOrReason(boardId, held.id, userId.value)
        if (!linked.ok) {
          failure.value = linked.reason
          return
        }
      }
    }
    emit("saved")
    emit("update:open", false)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <island-dialog
    :accent="accent"
    :open="open"
    testid="board-member-dialog"
    :title="title"
    @update:open="emit('update:open', $event)"
  >
    <form
      id="board-member-dialog-form"
      class="member-form"
      @submit.prevent="submit"
    >
      <div class="member-form__row">
        <label class="member-form__field">
          <span class="member-form__label">Name</span>
          <input
            v-model="name"
            class="member-form__input"
            data-testid="board-member-dialog-name"
            maxlength="255"
            required
            type="text"
          >
        </label>
        <label class="member-form__field">
          <span class="member-form__label">Nickname</span>
          <input
            v-model="nickname"
            class="member-form__input"
            data-testid="board-member-dialog-nickname"
            maxlength="255"
            type="text"
          >
        </label>
      </div>

      <!-- The two together, quoted, which is the one string a reader has always been shown. -->
      <span
        v-if="published"
        class="member-form__hint"
        data-testid="board-member-dialog-published"
      >Reads as {{ published }}</span>

      <label class="member-form__field">
        <span class="member-form__label">Role</span>
        <input
          v-model="role"
          class="member-form__input"
          data-testid="board-member-dialog-role"
          maxlength="255"
          placeholder="Secretary and Commissioner of the Esports Lounge"
          required
          type="text"
        >
        <span class="member-form__hint">
          In the board's own words, not from a list: nine years of boards have renamed and
          combined their offices
        </span>
      </label>

      <!-- Held until Save, like every other field here: closing without saving leaves the
           member on the portrait it had. Square, because a portrait is drawn on a square plate. -->
      <image-picker
        label="Portrait"
        :picture="portrait"
        shape="icon"
        :store="storeMemberPortrait"
        testid="board-member-dialog-portrait"
        @update:picture="portrait = $event"
      />

      <div class="member-form__field">
        <span class="member-form__label">Account</span>
        <!--
          Shown as attached rather than as a value in the box, because detaching is a different
          act from choosing again and the two must not be the same gesture a pixel apart.
        -->
        <span
          v-if="userId != null"
          class="member-form__attached"
          data-testid="board-member-dialog-attached"
        >
          {{ nameOf(userId) }}
          <button
            :aria-label="`Detach ${nameOf(userId)}`"
            class="member-form__detach"
            data-testid="board-member-dialog-detach"
            type="button"
            @click="userId = null"
          >&times;</button>
        </span>
        <island-picker
          empty-note="Nobody has an account here yet."
          :options="memberOptions"
          placeholder="No account — search a member"
          :selected-key="userId == null ? null : String(userId)"
          testid-prefix="board-member-dialog-account"
          @pick="key => userId = Number(key)"
        />
        <span class="member-form__hint">
          Most people who have sat on a board never had an account here. A member with none
          still records the board that sat.
        </span>
      </div>

      <div class="member-form__row">
        <label class="member-form__field">
          <span class="member-form__label">Took office</span>
          <input
            v-model="startDate"
            class="member-form__input"
            data-testid="board-member-dialog-start"
            required
            type="date"
          >
        </label>
        <label class="member-form__field">
          <span class="member-form__label">Left it</span>
          <input
            v-model="endDate"
            class="member-form__input"
            data-testid="board-member-dialog-end"
            type="date"
          >
        </label>
      </div>

      <span class="member-form__hint">
        Taken from the board, and worth correcting for a handover part-way through the year:
        this is what the association is asked when it answers who served in a given window.
      </span>

      <label class="member-form__field">
        <span class="member-form__label">Blurb</span>
        <textarea
          v-model="description"
          class="member-form__input member-form__input--tall"
          data-testid="board-member-dialog-description"
          :maxlength="DESCRIPTION_CAP"
          rows="4"
        />
        <span class="member-form__hint">What they wrote about themselves, in their own words</span>
      </label>

      <p
        v-if="failure"
        class="member-form__failure"
        data-testid="board-member-dialog-failure"
        role="alert"
      >
        {{ failure }}
      </p>
    </form>

    <!-- In the footer, like the other dialogs on the island. Save names the form it submits
         rather than sitting inside it, which is what lets it stand out here. -->
    <template #footer>
      <div class="member-form__actions">
        <button
          v-if="!adding"
          class="member-form__button member-form__button--drop"
          data-testid="board-member-dialog-remove"
          type="button"
          @click="askToRemove"
        >
          Remove
        </button>
        <button
          class="member-form__button member-form__button--ghost"
          data-testid="board-member-dialog-cancel"
          type="button"
          @click="emit('update:open', false)"
        >
          Cancel
        </button>
        <button
          class="member-form__button member-form__button--go"
          data-testid="board-member-dialog-save"
          :disabled="!complete || saving"
          form="board-member-dialog-form"
          type="submit"
        >
          {{ saving ? "Saving" : "Save" }}
        </button>
      </div>
    </template>
  </island-dialog>

  <confirm-dialog
    :accent="accent"
    confirm-label="Remove the member"
    :failure="removalFailure"
    :open="confirming"
    :question="question"
    testid="board-member-remove-dialog"
    title="Remove this member?"
    :working="removing"
    @confirm="removeMember"
    @update:open="confirming = $event"
  />
</template>

<style>
/* Unscoped: the dialog is portalled out of this component's subtree. */
.member-form {
  display: flex;
  flex-direction: column;
  gap: 0.9rem;
  padding-bottom: 0.35rem;
}

.member-form__row {
  display: flex;
  flex-wrap: wrap;
  gap: 0.85rem;
}

.member-form__field {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 0.3rem;
  min-width: 0;
}

.member-form__label {
  padding: 0;
  font-family: var(--font-display);
  font-size: 0.62rem;
  color: var(--color-ash);
  letter-spacing: 0.1em;
  text-transform: uppercase;
}

.member-form__hint {
  font-size: 0.72rem;
  color: color-mix(in oklab, var(--color-ash) 80%, transparent);
  word-break: break-word;
}

/* One field style across the island: flat, square, and lit by the focus ring rather than by
   a border that competes with the labels above it. */
.member-form__input {
  width: 100%;
  padding: 0.55rem 0.75rem;
  font-family: inherit;
  font-size: 0.92rem;
  color: var(--color-chalk);
  background-color: color-mix(in oklab, var(--color-chalk) 7%, transparent);
  border: 0;
}

.member-form__input::placeholder {
  color: var(--color-ash);
}

.member-form__input--tall {
  resize: vertical;
}

.member-form__input:focus-visible {
  outline: none;
  border-color: var(--dialog-accent, var(--color-brand));
}

/* The account as a fact about the membership, with the one way to undo it beside it. */
.member-form__attached {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
  padding: 0.4rem 0.5rem 0.4rem 0.75rem;
  background-color: color-mix(in oklab, var(--dialog-accent, var(--color-brand)) 16%, transparent);
  font-size: 0.9rem;
  color: var(--color-chalk);
}

.member-form__detach {
  flex: none;
  padding: 0 0.35rem;
  background: none;
  border: 0;
  color: var(--color-ash);
  font-size: 1.1rem;
  line-height: 1;
  cursor: pointer;
}

.member-form__detach:hover,
.member-form__detach:focus-visible {
  color: var(--color-chalk);
}

.member-form__failure {
  margin: 0;
  color: var(--color-danger);
  font-size: 0.85rem;
}

/* Its own rule and its own spacing: see the footer in IslandDialog. */
.member-form__actions {
  display: flex;
  gap: 0.5rem;
  justify-content: flex-end;
  margin-top: 1rem;
  padding-top: 0.85rem;
  border-top: 1px solid color-mix(in oklab, var(--color-chalk) 12%, transparent);
}

.member-form__button {
  padding: 0.45rem 0.9rem;
  border: 1px solid color-mix(in oklab, var(--color-chalk) 16%, transparent);
  color: var(--color-chalk);
  cursor: pointer;
  font-family: inherit;
  font-size: 0.85rem;
}

.member-form__button--ghost {
  background: transparent;
}

/* First in the row and set apart, the way the esports dialogs set their own removal apart. */
.member-form__button--drop {
  margin-right: auto;
  background: color-mix(in oklab, var(--color-danger-tint) 18%, transparent);
  color: var(--color-danger-ink);
}

.member-form__button--drop:hover {
  background: color-mix(in oklab, var(--color-danger-tint) 34%, transparent);
  color: var(--color-danger-ink-strong);
}

.member-form__button--go {
  background: var(--dialog-accent, var(--color-brand));
  border-color: transparent;
  color: var(--color-void);
}

.member-form__button:disabled {
  cursor: not-allowed;
  opacity: 0.5;
}
</style>
