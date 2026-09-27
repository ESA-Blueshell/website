<script lang="ts" setup>
import {computed, ref, watch} from "vue"
import ArtCells from "@/components/island/ArtCells.vue"
import CheckBox from "@/components/island/CheckBox.vue"
import CutButton from "@/components/island/CutButton.vue"
import EditPage from "@/components/island/EditPage.vue"
import FormControl from "@/components/island/FormControl.vue"
import FormField from "@/components/island/FormField.vue"
import FormFields from "@/components/island/FormFields.vue"
import FormSection from "@/components/island/FormSection.vue"
import IconButton from "@/components/island/IconButton.vue"
import ImagePicker from "@/components/island/ImagePicker.vue"
import NoticeBox from "@/components/island/NoticeBox.vue"
import PreviewFrame from "@/components/island/PreviewFrame.vue"
import RecordFact from "@/components/island/RecordFact.vue"
import MarkdownView from "@/components/island/MarkdownView.vue"
import RecordHead from "@/components/island/RecordHead.vue"
import SearchPicker from "@/components/island/SearchPicker.vue"
import type {Picture} from "@/components/island/pictures"
import EventGamesPicker from "@/domains/games/island/EventGamesPicker.vue"
import {useCasualGames} from "@/domains/games"
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
import {cellOf, initialsOf} from "../useCommittees"

/**
 * A committee added or corrected on its own page, with its page head and its cell in Every
 * committee drawn beside the form as they will read. The board writes all of it; a committee's
 * own members write its description, banner and games, and see the rest without changing it.
 */
defineOptions({name: "CommitteeEditor"})

type Seat = {userId: number; role: string}

const props = defineProps<{
  /** The committee being corrected, or nothing where the board is adding one. */
  committee: Committee | null
  asBoard: boolean
  back: string
}>()

const emit = defineEmits<{
  (event: "saved", committee: Committee): void
  (event: "cancel"): void
}>()

const adding = computed(() => props.committee == null)
const ACCENT = "var(--color-brand)"

const name = ref("")
const slug = ref("")
const listed = ref(true)
const description = ref("")
const banner = ref<Picture | null>(null)
const gameCodes = ref<string[]>([])
const seats = ref<Seat[]>([])
const failure = ref<string | null>(null)
const saving = ref(false)
const accounts = ref<MemberAccount[]>([])

watch(() => props.committee, async committee => {
  name.value = committee?.name ?? ""
  slug.value = committee?.slug ?? ""
  listed.value = committee?.listed ?? true
  description.value = committee?.description ?? ""
  banner.value = (committee?.banner as Picture | null | undefined) ?? null
  gameCodes.value = [...(committee?.gameCodes ?? [])]
  failure.value = null
  if (!props.asBoard) return
  if (accounts.value.length === 0) accounts.value = (await loadMemberAccounts()) ?? []
  if (committee != null) {
    const held = committee.members ?? (await listCommittees()).find(one => one.id === committee.id)?.members ?? []
    seats.value = held.map(member => ({userId: member.userId, role: member.role ?? ""}))
  }
}, {immediate: true})

// A new committee's address follows its name until somebody types one of their own.
// TWIN: `addressOf` in `CommitteeAddress.kt` makes the address the api keeps.
const slugTouched = ref(false)
watch(name, typed => {
  if (adding.value && !slugTouched.value) slug.value = typed.trim().toLowerCase().replace(/[^\p{L}\p{N}]+/gu, "-").replace(/^-+|-+$/g, "")
})
const typeSlug = (value: string | null) => {
  slugTouched.value = true
  slug.value = value ?? ""
}

const accountOptions = computed(() => accounts.value
  .filter(account => !seats.value.some(seat => seat.userId === account.id))
  .map(account => ({key: String(account.id), label: account.name, note: account.email ?? undefined})))

const nameOf = (userId: number) => accounts.value.find(account => account.id === userId)?.name ?? `Member ${userId}`
const seat = (key: string) => { seats.value = [...seats.value, {userId: Number(key), role: ""}] }
const unseat = (userId: number) => { seats.value = seats.value.filter(one => one.userId !== userId) }

const storeBanner = (file: File) => storeCommitteeBanner(file, props.committee?.id ?? null)

const complete = computed(() => description.value.trim() !== ""
  && (!props.asBoard || (name.value.trim() !== "" && slug.value.trim() !== "" && seats.value.length > 0)))

const {games} = useCasualGames()
const named = computed(() => gameCodes.value
  .map(code => games.value.find(game => game.code === code))
  .filter(game => game !== undefined))

/** The committee as the pages will draw it, from what is typed now. */
const drafted = computed<Committee>(() => ({
  id: props.committee?.id ?? 0,
  name: name.value.trim() || "New committee",
  slug: slug.value,
  description: description.value,
  listed: listed.value,
  archived: props.committee?.archived ?? false,
  banner: banner.value,
  gameCodes: gameCodes.value,
  version: 0,
  createdAt: "",
  updatedAt: "",
}) as Committee)

const cell = computed(() => [cellOf(drafted.value, codes => codes
  .map(code => games.value.find(game => game.code === code)?.name)
  .filter(one => one !== undefined))])

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
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <edit-page
    :accent="ACCENT"
    :back="{to: back, label: committee ? committee.name : 'Committees'}"
    :eyebrow="committee ? committee.name : 'Committees'"
    testid="committee-edit"
    :title="adding ? 'Add a committee' : 'Edit committee'"
  >
    <template
      v-if="committee"
      #actions
    >
      <cut-button
        :href="`/committees/${committee.slug}`"
        testid="committee-edit-see"
      >
        See the committee
      </cut-button>
    </template>

    <form
      class="committee-editor"
      @submit.prevent="submit"
    >
      <form-section title="The committee">
        <form-fields>
          <div class="form-span">
            <image-picker
              label="Banner"
              :picture="banner"
              :store="storeBanner"
              testid="committee-edit-banner"
              @update:picture="banner = $event"
            />
          </div>
          <form-control
            v-model="name"
            :disabled="!asBoard"
            label="Name*"
            testid="committee-edit-name"
          />
          <form-control
            :disabled="!asBoard"
            label="Address*"
            :model-value="slug"
            testid="committee-edit-slug"
            @update:model-value="typeSlug"
          />
          <div class="form-span">
            <check-box
              v-model="listed"
              :disabled="!asBoard"
              label="Listed among the committees to join"
              testid="committee-edit-listed"
            />
          </div>
          <div class="form-span">
            <event-games-picker
              v-model="gameCodes"
              testid="committee-edit-games"
            />
          </div>
          <div class="form-span">
            <form-control
              v-model="description"
              kind="markdown"
              label="Description*"
              testid="committee-edit-description"
            />
          </div>
        </form-fields>
        <p
          v-if="!asBoard"
          class="committee-editor__note"
          data-testid="committee-edit-fixed"
        >
          The board changes the name, address, listing and members.
        </p>
      </form-section>

      <form-section
        v-if="asBoard"
        title="Members"
      >
        <ul
          v-if="seats.length > 0"
          class="committee-editor__seats"
        >
          <li
            v-for="one in seats"
            :key="one.userId"
            class="committee-editor__seat"
            :data-testid="`committee-edit-seat-${one.userId}`"
          >
            <span class="committee-editor__who">{{ nameOf(one.userId) }}</span>
            <form-control
              v-model="one.role"
              class="committee-editor__role"
              label="Role"
              :testid="`committee-edit-role-${one.userId}`"
            />
            <icon-button
              danger
              :label="`Take ${nameOf(one.userId)} off the committee`"
              :testid="`committee-edit-unseat-${one.userId}`"
              @click="unseat(one.userId)"
            >
              <svg
                aria-hidden="true"
                fill="none"
                stroke="currentColor"
                stroke-linecap="round"
                stroke-width="1.6"
                viewBox="0 0 24 24"
              ><path d="M6 6l12 12M18 6 6 18" /></svg>
            </icon-button>
          </li>
        </ul>
        <form-field
          label="Add a member"
          testid="committee-edit-member"
        >
          <template #default="{controlId, labelId}">
            <search-picker
              :control-id="controlId"
              empty-note="Every member is on it already."
              :labelled-by="labelId"
              :options="accountOptions"
              placeholder="Search a member"
              testid-prefix="committee-edit-member"
              @pick="seat"
            />
          </template>
        </form-field>
      </form-section>

      <notice-box
        v-if="failure"
        testid="committee-edit-failure"
        tone="danger"
      >
        {{ failure }}
      </notice-box>
    </form>

    <template #footer>
      <div class="committee-editor__save">
        <cut-button
          testid="committee-edit-cancel"
          tone="quiet"
          @click="emit('cancel')"
        >
          Cancel
        </cut-button>
        <cut-button
          :disabled="!complete || saving"
          testid="committee-edit-save"
          tone="solid"
          @click="submit"
        >
          {{ saving ? "Saving" : adding ? "Add the committee" : "Save" }}
        </cut-button>
      </div>
    </template>

    <template #preview>
      <div class="committee-editor__previews">
        <preview-frame>
          <record-head
            :accent="ACCENT"
            :archived="drafted.archived"
            :back="{to: '/committees', label: 'Committees'}"
            :banner="banner"
            eyebrow="Committee"
            :initials="initialsOf(drafted.name)"
            testid="committee-edit-preview"
            :title="drafted.name"
          >
            <markdown-view :source="description" />
            <template
              v-if="named.length > 0"
              #facts
            >
              <record-fact :label="named.length === 1 ? 'Game' : 'Games'">
                {{ named.map(game => game.name).join(" · ") }}
              </record-fact>
            </template>
          </record-head>
        </preview-frame>
        <p class="committee-editor__preview-label">
          In Every committee
        </p>
        <div class="committee-editor__cell">
          <art-cells
            :cells="cell"
            testid-prefix="committee-edit-preview-cell"
          />
        </div>
      </div>
    </template>
  </edit-page>
</template>

<style scoped>
.committee-editor__note {
  font-size: 0.88rem;
  color: var(--color-ash);
}

.committee-editor__seats {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
  margin: 0;
  padding: 0;
  list-style: none;
}

.committee-editor__seat {
  display: grid;
  grid-template-columns: minmax(0, 12rem) minmax(0, 1fr) auto;
  gap: 1rem;
  align-items: center;
}

.committee-editor__who {
  overflow: hidden;
  font-size: 0.95rem;
  color: var(--color-chalk);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.committee-editor__save {
  display: flex;
  gap: 0.5rem;
  justify-content: flex-end;
}

.committee-editor__previews {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.committee-editor__preview-label {
  margin-top: 0.75rem;
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 0.3em;
  text-transform: uppercase;
  color: var(--color-eyebrow);
}

.committee-editor__cell {
  max-width: 18rem;
}

.committee-editor__cell :deep(.art-cells) {
  grid-template-columns: minmax(0, 1fr);
}

@media (max-width: 767px) {
  .committee-editor__seat {
    grid-template-columns: minmax(0, 1fr) auto;
  }

  .committee-editor__who {
    grid-column: 1 / -1;
  }
}
</style>
