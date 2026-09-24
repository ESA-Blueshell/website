<script lang="ts" setup>
import {computed, ref, watch} from "vue"
import ImagePicker from "@/components/island/ImagePicker.vue"
import ModalDialog from "@/components/island/ModalDialog.vue"
import type {Picture} from "@/components/island/pictures"
import {FileType} from "@/services/api"
import {addCasualGame, saveCasualGame, storeGamePicture, type CasualGame, type CasualGameDraft} from "../adapters/games"

/**
 * A game added or corrected by the board from the casual pages: what it is called, the address
 * its page answers to, what it says about itself, its colour and its two pictures.
 *
 * A refusal keeps what was typed. The pictures are stored when chosen and put on the game only
 * by Save, like every other field here.
 */
defineOptions({name: "CasualGameDialog"})

const props = defineProps<{
  open: boolean
  /** The game being corrected, or nothing where one is being added. */
  game: CasualGame | null
}>()

const emit = defineEmits<{
  (event: "update:open", open: boolean): void
  (event: "saved", game: CasualGame): void
}>()

const adding = computed(() => props.game == null)

const name = ref("")
const slug = ref("")
const intro = ref("")
const colour = ref("")
const banner = ref<Picture | null>(null)
const icon = ref<Picture | null>(null)
const failure = ref<string | null>(null)
const saving = ref(false)

const storeBanner = (file: File) => storeGamePicture(file, FileType.GAME_BANNER)
const storeIcon = (file: File) => storeGamePicture(file, FileType.GAME_ICON)

// Opening fills the form from the game as it stands; a reopen after a refusal starts clean.
watch(
  () => [props.open, props.game] as const,
  ([open]) => {
    if (!open) return
    const game = props.game
    name.value = game?.name ?? ""
    slug.value = game?.slug ?? ""
    intro.value = game?.intro ?? ""
    colour.value = game?.accent ?? ""
    banner.value = (game?.banner as Picture | null | undefined) ?? null
    icon.value = (game?.icon as Picture | null | undefined) ?? null
    failure.value = null
  },
  {immediate: true},
)

// A new game's address follows its name until somebody types one of their own.
const slugTouched = ref(false)
watch(name, typed => {
  if (adding.value && !slugTouched.value) slug.value = typed.trim().toLowerCase().replace(/[^\p{L}\p{N}]+/gu, "-").replace(/^-+|-+$/g, "")
})

const complete = computed(() => name.value.trim() !== "" && slug.value.trim() !== "")

const draft = (): CasualGameDraft => ({
  name: name.value.trim(),
  slug: slug.value.trim(),
  intro: intro.value.trim() || null,
  accent: colour.value.trim() || null,
  banner: banner.value?.path ?? null,
  icon: icon.value?.path ?? null,
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
    emit("saved", result.game)
    emit("update:open", false)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <modal-dialog
    :accent="colour || undefined"
    :open="open"
    testid="casual-game-dialog"
    :title="adding ? 'Add a game' : `Edit ${game?.name}`"
    @update:open="emit('update:open', $event)"
  >
    <form
      id="casual-game-dialog-form"
      class="casual-form"
      @submit.prevent="submit"
    >
      <div class="casual-form__row">
        <label class="casual-form__field">
          <span class="casual-form__label">Name</span>
          <input
            v-model="name"
            class="casual-form__input"
            data-testid="casual-game-dialog-name"
            maxlength="64"
            required
            type="text"
          >
        </label>
        <label class="casual-form__field">
          <span class="casual-form__label">Address</span>
          <input
            v-model="slug"
            class="casual-form__input"
            data-testid="casual-game-dialog-slug"
            maxlength="64"
            required
            type="text"
            @input="slugTouched = true"
          >
        </label>
      </div>

      <label class="casual-form__field">
        <span class="casual-form__label">Intro</span>
        <textarea
          v-model="intro"
          class="casual-form__input casual-form__input--tall"
          data-testid="casual-game-dialog-intro"
          maxlength="4000"
          rows="3"
        />
      </label>

      <label class="casual-form__field casual-form__field--narrow">
        <span class="casual-form__label">Colour</span>
        <input
          v-model="colour"
          class="casual-form__input"
          data-testid="casual-game-dialog-accent"
          maxlength="32"
          placeholder="#ff4655"
          type="text"
        >
      </label>

      <div class="casual-form__pictures">
        <image-picker
          label="Banner"
          :picture="banner"
          :store="storeBanner"
          testid="casual-game-dialog-banner"
          @update:picture="banner = $event"
        />
        <image-picker
          label="Icon"
          may-be-vector
          :picture="icon"
          shape="icon"
          :store="storeIcon"
          testid="casual-game-dialog-icon"
          @update:picture="icon = $event"
        />
      </div>

      <slot />

      <p
        v-if="failure"
        class="casual-form__failure"
        data-testid="casual-game-dialog-failure"
        role="alert"
      >
        {{ failure }}
      </p>
    </form>

    <template #footer>
      <div class="casual-form__actions">
        <button
          class="casual-form__button casual-form__button--ghost"
          data-testid="casual-game-dialog-cancel"
          type="button"
          @click="emit('update:open', false)"
        >
          Cancel
        </button>
        <button
          class="casual-form__button casual-form__button--go"
          data-testid="casual-game-dialog-save"
          :disabled="!complete || saving"
          form="casual-game-dialog-form"
          type="submit"
        >
          {{ saving ? "Saving" : adding ? "Add the game" : "Save" }}
        </button>
      </div>
    </template>
  </modal-dialog>
</template>

<style scoped>
.casual-form {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  padding-bottom: 0.35rem;
}

.casual-form__row {
  display: flex;
  flex-wrap: wrap;
  gap: 0.9rem;
}

.casual-form__field {
  display: flex;
  flex: 1;
  flex-direction: column;
  gap: 0.3rem;
  min-width: 10rem;
}

.casual-form__field--narrow {
  flex: 0 0 9rem;
}

.casual-form__label {
  font-family: var(--font-display);
  font-size: 0.62rem;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--color-ash);
}

.casual-form__input {
  width: 100%;
  padding: 0.55rem 0.75rem;
  font-family: inherit;
  font-size: 0.92rem;
  color: var(--color-chalk);
  background-color: color-mix(in oklab, var(--color-chalk) 7%, transparent);
  border: 0;
}

.casual-form__input::placeholder {
  color: var(--color-ash);
}

.casual-form__input--tall {
  resize: vertical;
}

.casual-form__input:focus-visible {
  outline: 2px solid var(--dialog-accent, var(--color-brand));
  outline-offset: 1px;
}

.casual-form__pictures {
  display: flex;
  flex-wrap: wrap;
  gap: 1.1rem;
  align-items: flex-end;
}

.casual-form__failure {
  margin: 0;
  font-size: 0.85rem;
  color: var(--color-danger);
}

.casual-form__actions {
  display: flex;
  gap: 0.5rem;
  justify-content: flex-end;
  margin-top: 1rem;
  padding-top: 0.85rem;
  border-top: 1px solid color-mix(in oklab, var(--color-chalk) 12%, transparent);
}

.casual-form__button {
  padding: 0.45rem 0.9rem;
  font-family: inherit;
  font-size: 0.85rem;
  color: var(--color-chalk);
  cursor: pointer;
  border: 1px solid color-mix(in oklab, var(--color-chalk) 16%, transparent);
}

.casual-form__button--ghost {
  background: transparent;
}

.casual-form__button--go {
  color: var(--color-void);
  background: var(--dialog-accent, var(--color-brand));
  border-color: transparent;
}

.casual-form__button--go:disabled {
  cursor: default;
  opacity: 0.5;
}
</style>
