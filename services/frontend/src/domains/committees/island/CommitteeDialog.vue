<script lang="ts" setup>
import {computed, ref, watch} from "vue"
import ArtCells from "@/components/island/ArtCells.vue"
import ImagePicker from "@/components/island/ImagePicker.vue"
import ModalDialog from "@/components/island/ModalDialog.vue"
import SearchPicker from "@/components/island/SearchPicker.vue"
import type {Picture} from "@/components/island/pictures"
import EventGamesPicker from "@/domains/games/island/EventGamesPicker.vue"
import {loadMemberAccounts, type MemberAccount} from "@/domains/user"
import {
  addCommittee,
  type Committee,
  type CommitteeDraft,
  listCommittees,
  saveCommitteeAsBoard,
  saveOwnCommitteePage,
  storeCommitteeBanner,
} from "../adapters/committees"
import {cellOf} from "../useCommittees"

/**
 * A committee added or corrected from its own pages. The board writes all of it; a committee's
 * own members write its description, banner and games, and see the rest read-only.
 *
 * A refusal keeps what was typed. The banner is stored when chosen and put on the committee only
 * by Save, like every other field here.
 */
defineOptions({name: "CommitteeDialog"})

type Seat = {userId: number; role: string}

const props = defineProps<{
  open: boolean
  /** The committee being corrected, or nothing where the board is adding one. */
  committee: Committee | null
  /** Whether the board is writing, rather than one of the committee's own members. */
  asBoard: boolean
}>()

const emit = defineEmits<{
  (event: "update:open", open: boolean): void
  (event: "saved", committee: Committee): void
}>()

const adding = computed(() => props.committee == null)

const name = ref("")
const slug = ref("")
const listed = ref(true)
const description = ref("")
const banner = ref<Picture | null>(null)
const gameCodes = ref<string[]>([])
const seats = ref<Seat[]>([])
const failure = ref<string | null>(null)
const saving = ref(false)

/** Every member account, for the board to seat; read once and kept. */
const accounts = ref<MemberAccount[]>([])

// Opening fills the form from the committee as it stands, and the board's seats from its own list.
watch(
  () => [props.open, props.committee] as const,
  async ([open]) => {
    if (!open) return
    const committee = props.committee
    name.value = committee?.name ?? ""
    slug.value = committee?.slug ?? ""
    listed.value = committee?.listed ?? true
    description.value = committee?.description ?? ""
    banner.value = (committee?.banner as Picture | null | undefined) ?? null
    gameCodes.value = [...(committee?.gameCodes ?? [])]
    seats.value = []
    failure.value = null
    if (!props.asBoard) return
    if (accounts.value.length === 0) accounts.value = (await loadMemberAccounts()) ?? []
    if (committee != null) {
      const held = committee.members ?? (await listCommittees()).find(one => one.id === committee.id)?.members ?? []
      seats.value = held.map(member => ({userId: member.userId, role: member.role ?? ""}))
    }
  },
  {immediate: true},
)

// A new committee's address follows its name until somebody types one of their own.
// TWIN: `addressOf` in `CommitteeAddress.kt` makes the address the api keeps.
const slugTouched = ref(false)
watch(name, typed => {
  if (adding.value && !slugTouched.value) slug.value = typed.trim().toLowerCase().replace(/[^\p{L}\p{N}]+/gu, "-").replace(/^-+|-+$/g, "")
})

const accountOptions = computed(() => accounts.value
  .filter(account => !seats.value.some(seat => seat.userId === account.id))
  .map(account => ({key: String(account.id), label: account.name, note: account.email ?? undefined})))

const nameOf = (userId: number) => accounts.value.find(account => account.id === userId)?.name ?? `Member ${userId}`

const seat = (key: string) => {
  seats.value = [...seats.value, {userId: Number(key), role: ""}]
}

const unseat = (userId: number) => {
  seats.value = seats.value.filter(one => one.userId !== userId)
}

const storeBanner = (file: File) => storeCommitteeBanner(file, props.committee?.id ?? null)

const complete = computed(() => description.value.trim() !== ""
  && (!props.asBoard || (name.value.trim() !== "" && slug.value.trim() !== "" && seats.value.length > 0)))

/** The committee as Every committee will show it, drawn from what is typed now. */
const preview = computed(() => [cellOf({
  id: props.committee?.id ?? 0,
  name: name.value.trim() || "New committee",
  slug: slug.value,
  description: description.value,
  listed: listed.value,
  archived: props.committee?.archived ?? false,
  banner: banner.value,
  gameCodes: [],
  version: 0,
  createdAt: "",
  updatedAt: "",
})])

const draft = (): CommitteeDraft => ({
  name: name.value.trim(),
  slug: slug.value.trim(),
  listed: listed.value,
  description: description.value.trim(),
  banner: banner.value?.path ?? null,
  members: seats.value.map(one => ({userId: one.userId, role: one.role.trim() || null})),
  gameCodes: gameCodes.value,
})

const submit = async () => {
  if (!complete.value || saving.value) return
  saving.value = true
  failure.value = null
  try {
    const committee = props.committee
    const written = draft()
    const result = committee == null
      ? await addCommittee(written)
      : props.asBoard
        ? await saveCommitteeAsBoard(committee.id, committee.version, written)
        : await saveOwnCommitteePage(committee.id, {description: written.description, banner: written.banner, gameCodes: written.gameCodes})
    if (!result.ok) {
      failure.value = result.reason
      return
    }
    emit("saved", result.committee)
    emit("update:open", false)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <modal-dialog
    :open="open"
    testid="committee-dialog"
    :title="adding ? 'Add a committee' : `Edit ${committee?.name}`"
    @update:open="emit('update:open', $event)"
  >
    <form
      id="committee-dialog-form"
      class="committee-form"
      @submit.prevent="submit"
    >
      <template v-if="asBoard">
        <div class="committee-form__row">
          <label class="committee-form__field">
            <span class="committee-form__label">Name</span>
            <input
              v-model="name"
              class="committee-form__input"
              data-testid="committee-dialog-name"
              maxlength="100"
              required
              type="text"
            >
          </label>
          <label class="committee-form__field">
            <span class="committee-form__label">Address</span>
            <input
              v-model="slug"
              class="committee-form__input"
              data-testid="committee-dialog-slug"
              maxlength="64"
              required
              type="text"
              @input="slugTouched = true"
            >
          </label>
        </div>
        <label class="committee-form__check">
          <input
            v-model="listed"
            data-testid="committee-dialog-listed"
            type="checkbox"
          >
          Listed among the committees to join
        </label>
      </template>
      <div
        v-else
        class="committee-form__fixed"
        data-testid="committee-dialog-fixed"
      >
        <p class="committee-form__label">
          Name, address, Listed and members
        </p>
        <p>{{ committee?.name }} at /committees/{{ committee?.slug }}, {{ committee?.listed ? "listed" : "not listed" }}.</p>
        <p class="committee-form__note">
          The board changes these.
        </p>
      </div>

      <label class="committee-form__field">
        <span class="committee-form__label">Description</span>
        <textarea
          v-model="description"
          class="committee-form__input committee-form__input--tall"
          data-testid="committee-dialog-description"
          maxlength="4000"
          required
          rows="5"
        />
      </label>

      <image-picker
        label="Banner"
        :picture="banner"
        :store="storeBanner"
        testid="committee-dialog-banner"
        @update:picture="banner = $event"
      />

      <event-games-picker
        v-model="gameCodes"
        testid="committee-dialog-games"
      />

      <div
        v-if="asBoard"
        class="committee-form__field"
      >
        <span class="committee-form__label">Members</span>
        <ul
          v-if="seats.length > 0"
          class="committee-form__seats"
        >
          <li
            v-for="one in seats"
            :key="one.userId"
            class="committee-form__seat"
            :data-testid="`committee-dialog-seat-${one.userId}`"
          >
            <span class="committee-form__seat-name">{{ nameOf(one.userId) }}</span>
            <input
              v-model="one.role"
              aria-label="Role"
              class="committee-form__input committee-form__input--role"
              maxlength="255"
              placeholder="Role"
              type="text"
            >
            <button
              :aria-label="`Take ${nameOf(one.userId)} off the committee`"
              class="committee-form__remove"
              type="button"
              @click="unseat(one.userId)"
            >
              ×
            </button>
          </li>
        </ul>
        <search-picker
          empty-note="Every member is on it already."
          :options="accountOptions"
          placeholder="Add a member"
          testid-prefix="committee-dialog-member"
          @pick="seat"
        />
      </div>

      <div class="committee-form__preview">
        <span class="committee-form__label">In Every committee</span>
        <art-cells
          :cells="preview"
          testid-prefix="committee-dialog-preview"
        />
      </div>

      <p
        v-if="failure"
        class="committee-form__failure"
        data-testid="committee-dialog-failure"
        role="alert"
      >
        {{ failure }}
      </p>
    </form>

    <template #footer>
      <div class="committee-form__actions">
        <button
          class="committee-form__button committee-form__button--ghost"
          data-testid="committee-dialog-cancel"
          type="button"
          @click="emit('update:open', false)"
        >
          Cancel
        </button>
        <button
          class="committee-form__button committee-form__button--go"
          data-testid="committee-dialog-save"
          :disabled="!complete || saving"
          form="committee-dialog-form"
          type="submit"
        >
          {{ saving ? "Saving" : adding ? "Add the committee" : "Save" }}
        </button>
      </div>
    </template>
  </modal-dialog>
</template>

<style scoped>
.committee-form {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  padding-bottom: 0.35rem;
}

.committee-form__row {
  display: flex;
  flex-wrap: wrap;
  gap: 0.9rem;
}

.committee-form__field {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 0.3rem;
  min-width: 10rem;
}

.committee-form__label {
  margin: 0;
  font-family: var(--font-display);
  font-size: 0.62rem;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--color-ash);
}

.committee-form__input {
  width: 100%;
  padding: 0.55rem 0.75rem;
  font-family: inherit;
  font-size: 0.92rem;
  color: var(--color-chalk);
  background-color: color-mix(in oklab, var(--color-chalk) 7%, transparent);
  border: 0;
}

.committee-form__input--tall {
  resize: vertical;
}

.committee-form__input--role {
  flex: 1;
  padding: 0.35rem 0.6rem;
}

.committee-form__input:focus-visible {
  outline: 2px solid var(--color-brand);
  outline-offset: 1px;
}

.committee-form__check {
  display: inline-flex;
  gap: 0.5rem;
  align-items: center;
  font-size: 0.9rem;
  color: var(--color-chalk);
}

.committee-form__fixed {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
  font-size: 0.9rem;
  color: var(--color-chalk);
}

.committee-form__fixed p {
  margin: 0;
}

.committee-form__note {
  color: var(--color-ash);
}

.committee-form__seats {
  display: flex;
  flex-direction: column;
  gap: 0.35rem;
  margin: 0 0 0.4rem;
  padding: 0;
  list-style: none;
}

.committee-form__seat {
  display: flex;
  gap: 0.5rem;
  align-items: center;
}

.committee-form__seat-name {
  flex: 0 0 40%;
  overflow: hidden;
  font-size: 0.9rem;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.committee-form__remove {
  font-size: 1.1rem;
  line-height: 1;
  color: var(--color-ash);
  cursor: pointer;
  background: none;
  border: 0;
}

.committee-form__preview {
  max-width: 16rem;
}

.committee-form__preview :deep(.art-cells) {
  grid-template-columns: minmax(0, 1fr);
  margin-top: 0.4rem;
}

.committee-form__failure {
  margin: 0;
  font-size: 0.85rem;
  color: var(--color-danger);
}

.committee-form__actions {
  display: flex;
  gap: 0.5rem;
  justify-content: flex-end;
  margin-top: 1rem;
  padding-top: 0.85rem;
  border-top: 1px solid color-mix(in oklab, var(--color-chalk) 12%, transparent);
}

.committee-form__button {
  padding: 0.45rem 0.9rem;
  font-family: inherit;
  font-size: 0.85rem;
  color: var(--color-chalk);
  cursor: pointer;
  border: 1px solid color-mix(in oklab, var(--color-chalk) 16%, transparent);
}

.committee-form__button--ghost {
  background: transparent;
}

.committee-form__button--go {
  color: var(--color-void);
  background: var(--color-brand);
  border-color: transparent;
}

.committee-form__button--go:disabled {
  cursor: default;
  opacity: 0.5;
}
</style>
