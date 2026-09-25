<script lang="ts" setup>
import {computed, ref, watch} from "vue"
import ArtCells from "@/components/island/ArtCells.vue"
import CutButton from "@/components/island/CutButton.vue"
import EditPage from "@/components/island/EditPage.vue"
import ImagePicker from "@/components/island/ImagePicker.vue"
import PreviewFrame from "@/components/island/PreviewFrame.vue"
import type {Picture} from "@/components/island/pictures"
import {srcsetOf} from "@/components/island/pictures"
import RecordHead from "@/components/island/RecordHead.vue"
import SliceBand from "@/components/island/SliceBand.vue"
import {saveGameOrganisers, useCommittees} from "@/domains/committees"
import GameOrganisersPicker from "@/domains/committees/island/GameOrganisersPicker.vue"
import GameChannelPicker from "@/domains/discord/island/GameChannelPicker.vue"
import {enterGameInSeason, forgetCompetitionReads, useGames as useCompetitionGames} from "@/domains/esports"
import EsportsGameHead from "@/domains/esports/island/EsportsGameHead.vue"
import {addCasualGame, saveCasualGame, storeGameBanner, storeGameIcon, type CasualGame, type CasualGameDraft, type GameChannel} from "../adapters/games"
import ArchiveGameDialog from "../island/ArchiveGameDialog.vue"
import RemoveGameDialog from "../island/RemoveGameDialog.vue"
import {cellOf, initialsOf, useCasualGames} from "../useCasualGames"

/**
 * One game added or corrected on its own page, from either area: a game is one record, so the
 * casual and the competition pages edit the same fields here. The preview is the area the page
 * was opened from, drawn with its own parts: the casual head and cell, or the competition head
 * and the game's slice on the index.
 *
 * A refusal keeps what was typed. The pictures are stored when chosen and put on the game only by
 * Save, like every other field here.
 */
defineOptions({name: "GameEditor"})

const props = defineProps<{
  /** The game being corrected, or nothing where one is being added. */
  game: CasualGame | null
  area: "casual" | "competition"
  /** The season a new game is entered in by the same save, where it was added from one. */
  enterIn?: number | null
  back: string
}>()

const emit = defineEmits<{
  (event: "saved", game: CasualGame): void
  (event: "removed"): void
  (event: "cancel"): void
}>()

const adding = computed(() => props.game == null)
const {refresh: refreshCasual} = useCasualGames()
const {refresh: refreshCompetition} = useCompetitionGames()
const {committees, refresh: refreshCommittees} = useCommittees()

const name = ref(props.game?.name ?? "")
const slug = ref(props.game?.slug ?? "")
const intro = ref(props.game?.intro ?? "")
const colour = ref(props.game?.accent ?? "")
const sortIndex = ref<number | null>(props.game?.sortIndex ?? null)
const banner = ref<Picture | null>((props.game?.banner as Picture | null | undefined) ?? null)
const icon = ref<Picture | null>((props.game?.icon as Picture | null | undefined) ?? null)
const channels = ref<GameChannel[]>([...(props.game?.channels ?? [])])
const failure = ref<string | null>(null)
const saving = ref(false)

/** The committees naming the game when the page opened, so an unchanged list is not written. */
const organisersBefore = computed(() => props.game == null
  ? []
  : committees.value.filter(committee => committee.gameCodes.includes(props.game!.code)).map(committee => committee.id))
const organisers = ref<number[]>([])
// The committees may answer after the page opens; the list starts from them once they do.
watch(organisersBefore, before => { organisers.value = [...before] }, {immediate: true})

// A new game's address follows its name until somebody types one of their own.
// TWIN: `GameService.addressFor` makes the address the api keeps.
const slugTouched = ref(false)
watch(name, typed => {
  if (adding.value && !slugTouched.value) slug.value = typed.trim().toLowerCase().replace(/[^\p{L}\p{N}]+/gu, "-").replace(/^-+|-+$/g, "")
})

const complete = computed(() => name.value.trim() !== "" && slug.value.trim() !== "")

/** The game as it will be recorded, drawn by the preview before it is. */
const drafted = computed<CasualGame>(() => ({
  code: props.game?.code ?? "NEW",
  name: name.value.trim() || "New game",
  slug: slug.value,
  intro: intro.value,
  accent: colour.value.trim() || null,
  banner: banner.value,
  icon: icon.value,
  sortIndex: sortIndex.value ?? 0,
  archived: props.game?.archived ?? false,
  inCompetition: props.game?.inCompetition ?? false,
  channels: channels.value,
}))
const accent = computed(() => drafted.value.accent || "var(--color-brand)")
const cells = computed(() => [cellOf(drafted.value)])
const slice = computed(() => [{
  id: drafted.value.code,
  title: drafted.value.name,
  meta: "",
  banner: banner.value?.url ?? "",
  srcset: srcsetOf(banner.value),
  icon: icon.value?.url ?? null,
  iconSrcset: srcsetOf(icon.value),
  accent: accent.value,
}])

const draft = (): CasualGameDraft => ({
  name: name.value.trim(),
  slug: slug.value.trim(),
  intro: intro.value.trim() || null,
  accent: colour.value.trim() || null,
  banner: banner.value?.path ?? null,
  icon: icon.value?.path ?? null,
  channels: channels.value,
  sortIndex: sortIndex.value,
})

const submit = async () => {
  if (!complete.value || saving.value) return
  saving.value = true
  failure.value = null
  try {
    const result = props.game == null ? await addCasualGame(draft()) : await saveCasualGame(props.game.code, draft())
    if (!result.ok) {
      failure.value = result.reason
      return
    }
    const same = organisers.value.length === organisersBefore.value.length && organisers.value.every(id => organisersBefore.value.includes(id))
    if (!same) {
      const linked = await saveGameOrganisers(result.game.code, organisers.value)
      if (!linked.ok) {
        failure.value = `${result.game.name} is saved, but its committees are not. ${linked.reason}`
        return
      }
    }
    // A new game is recorded, then entered in the season it was added from: two requests behind
    // one Save. A refusal on the second is said rather than closed over.
    if (adding.value && props.enterIn != null) {
      const entered = await enterGameInSeason(props.enterIn, result.game.code)
      if (!entered.ok) {
        failure.value = `${result.game.name} is recorded, but it could not be entered in the season. `
          + `${entered.reason} Enter it from the season itself.`
        return
      }
    }
    forgetCompetitionReads()
    await Promise.all([refreshCasual(), refreshCompetition(), refreshCommittees()])
    emit("saved", result.game)
  } finally {
    saving.value = false
  }
}

const archiving = ref(false)
const removing = ref(false)
const archived = async () => {
  forgetCompetitionReads()
  await Promise.all([refreshCasual(), refreshCompetition()])
  emit("saved", props.game!)
}
/* Said before the games are read again: without the game this page unmounts, and an emit from a
   component that is gone reaches nobody. */
const removed = async () => {
  forgetCompetitionReads()
  emit("removed")
  await Promise.all([refreshCasual(), refreshCompetition()])
}
</script>

<template>
  <edit-page
    :accent="accent"
    :back="{to: back, label: area === 'casual' ? 'Casual' : 'Competition'}"
    :eyebrow="game ? game.name : area === 'casual' ? 'Casual' : 'Competition'"
    testid="game-edit"
    :title="game ? 'Edit game' : 'Add a game'"
  >
    <template
      v-if="game"
      #actions
    >
      <cut-button
        :href="`/${area}/${game.slug}`"
        testid="game-edit-see"
      >
        See the game
      </cut-button>
    </template>

    <form
      id="game-edit-form"
      class="game-form"
      @submit.prevent="submit"
    >
      <div class="game-form__row">
        <label class="game-form__field">
          <span class="game-form__label">Name</span>
          <input
            v-model="name"
            class="game-form__input"
            data-testid="game-edit-name"
            maxlength="64"
            required
            type="text"
          >
          <span class="game-form__hint">{{ game
            ? `Code: ${game.code}. Set when the game was added, and never changes`
            : "The code everything else files this game under is taken from the name, and never changes after" }}</span>
        </label>
        <label class="game-form__field">
          <span class="game-form__label">Address</span>
          <input
            v-model="slug"
            class="game-form__input"
            data-testid="game-edit-slug"
            maxlength="64"
            required
            type="text"
            @input="slugTouched = true"
          >
          <span class="game-form__hint">/casual/{{ slug }} and /competition/{{ slug }}</span>
        </label>
      </div>

      <label class="game-form__field">
        <span class="game-form__label">Intro</span>
        <textarea
          v-model="intro"
          class="game-form__input game-form__input--tall"
          data-testid="game-edit-intro"
          maxlength="4000"
          rows="4"
        />
      </label>

      <div class="game-form__row">
        <label class="game-form__field">
          <span class="game-form__label">Colour</span>
          <input
            v-model="colour"
            class="game-form__input"
            data-testid="game-edit-accent"
            maxlength="32"
            placeholder="#ff4655"
            type="text"
          >
          <span class="game-form__hint">Leave empty to use the default blue</span>
        </label>
        <label class="game-form__field game-form__field--narrow">
          <span class="game-form__label">Order</span>
          <input
            v-model.number="sortIndex"
            class="game-form__input"
            data-testid="game-edit-order"
            :placeholder="game ? '' : 'Last'"
            type="number"
          >
        </label>
      </div>

      <div class="game-form__pictures">
        <image-picker
          label="Banner"
          :picture="banner"
          :store="storeGameBanner"
          testid="game-edit-banner"
          @update:picture="banner = $event"
        />
        <image-picker
          label="Icon"
          may-be-vector
          :picture="icon"
          shape="icon"
          :store="storeGameIcon"
          testid="game-edit-icon"
          @update:picture="icon = $event"
        />
      </div>

      <game-channel-picker
        v-model="channels"
        testid="game-edit-channels"
      />

      <game-organisers-picker
        v-model="organisers"
        testid="game-edit-organisers"
      />

      <p
        v-if="failure"
        class="game-form__failure"
        data-testid="game-edit-failure"
        role="alert"
      >
        {{ failure }}
      </p>
    </form>

    <template #preview>
      <div class="game-form__previews">
        <preview-frame v-if="area === 'casual'">
          <record-head
            :accent="accent"
            :archived="drafted.archived"
            :back="{to: '/casual', label: 'Casual'}"
            :banner="banner"
            eyebrow="Casual"
            :icon="icon?.url"
            :initials="initialsOf(drafted.name)"
            testid="game-edit-preview"
            :title="drafted.name"
          >
            <template
              v-if="drafted.intro"
              #default
            >
              {{ drafted.intro }}
            </template>
          </record-head>
        </preview-frame>
        <div
          v-if="area === 'casual'"
          class="game-form__cell"
        >
          <art-cells
            :cells="cells"
            testid-prefix="game-edit-preview"
          />
        </div>
        <template v-else>
          <preview-frame>
            <esports-game-head
              :accent="accent"
              :icon="icon?.url"
              :icon-srcset="srcsetOf(icon)"
              :intro="drafted.intro ?? ''"
              :name="drafted.name"
            />
          </preview-frame>
          <preview-frame>
            <slice-band
              :accent="accent"
              :items="slice"
              :open-id="drafted.code"
              testid-prefix="game-edit-preview"
            />
          </preview-frame>
        </template>
      </div>
    </template>

    <template #footer>
      <div class="game-form__actions">
        <template v-if="game">
          <button
            class="game-form__button game-form__button--drop"
            data-testid="game-edit-archive"
            type="button"
            @click="archiving = true"
          >
            {{ game.archived ? "Bring back" : "Archive" }}
          </button>
          <button
            v-if="game.archived"
            class="game-form__button game-form__button--drop"
            data-testid="game-edit-remove"
            type="button"
            @click="removing = true"
          >
            Remove
          </button>
        </template>
        <button
          class="game-form__button game-form__button--ghost"
          data-testid="game-edit-cancel"
          type="button"
          @click="emit('cancel')"
        >
          Cancel
        </button>
        <button
          class="game-form__button game-form__button--go"
          data-testid="game-edit-save"
          :disabled="!complete || saving"
          form="game-edit-form"
          type="submit"
        >
          {{ saving ? "Saving" : adding ? "Add the game" : "Save" }}
        </button>
      </div>
    </template>
  </edit-page>

  <template v-if="game">
    <archive-game-dialog
      v-model:open="archiving"
      :game="game"
      @saved="archived"
    />
    <remove-game-dialog
      v-if="removing"
      :game="game"
      open
      @removed="removed"
      @update:open="removing = $event"
    />
  </template>
</template>

<style scoped>
.game-form {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.game-form__row {
  display: flex;
  flex-wrap: wrap;
  gap: 0.9rem;
}

.game-form__field {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 0.3rem;
  min-width: 10rem;
}

.game-form__field--narrow {
  flex: 0 0 8rem;
  min-width: 0;
}

.game-form__label {
  font-family: var(--font-display);
  font-size: 0.62rem;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--color-ash);
}

.game-form__hint {
  font-size: 0.78rem;
  color: var(--color-ash);
}

.game-form__input {
  width: 100%;
  padding: 0.55rem 0.75rem;
  font-family: inherit;
  font-size: 0.92rem;
  color: var(--color-chalk);
  background-color: color-mix(in oklab, var(--color-chalk) 7%, transparent);
  border: 0;
}

.game-form__input::placeholder {
  color: var(--color-ash);
}

.game-form__input--tall {
  resize: vertical;
}

.game-form__input:focus-visible {
  outline: 2px solid var(--edit-accent);
  outline-offset: 1px;
}

.game-form__pictures {
  display: flex;
  flex-wrap: wrap;
  gap: 1.1rem;
  align-items: flex-end;
}

.game-form__failure {
  margin: 0;
  font-size: 0.85rem;
  color: var(--color-danger);
}

.game-form__previews {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.game-form__cell {
  max-width: 16rem;
}

.game-form__cell :deep(.art-cells) {
  grid-template-columns: minmax(0, 1fr);
}

.game-form__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
  justify-content: flex-end;
}

.game-form__button {
  padding: 0.45rem 0.9rem;
  font-family: inherit;
  font-size: 0.85rem;
  color: var(--color-chalk);
  cursor: pointer;
  border: 1px solid color-mix(in oklab, var(--color-chalk) 16%, transparent);
}

.game-form__button--drop {
  color: var(--color-danger-ink);
  background: color-mix(in oklab, var(--color-danger-tint) 18%, transparent);
  border-color: transparent;
}

.game-form__button--drop:first-child {
  margin-right: auto;
}

.game-form__button--ghost {
  background: transparent;
}

.game-form__button--go {
  color: var(--color-void);
  background: var(--edit-accent);
  border-color: transparent;
}

.game-form__button--go:disabled {
  cursor: default;
  opacity: 0.5;
}
</style>
