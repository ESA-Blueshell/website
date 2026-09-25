<script lang="ts" setup>
import {computed, ref, watch} from "vue"
import VvField from "@/components/form/fields/VvField.vue"
import ArtCells from "@/components/island/ArtCells.vue"
import ColourControl from "@/components/island/ColourControl.vue"
import {isHexColour} from "@/components/island/colour"
import CutButton from "@/components/island/CutButton.vue"
import EditPage from "@/components/island/EditPage.vue"
import FormFields from "@/components/island/FormFields.vue"
import FormSection from "@/components/island/FormSection.vue"
import ImagePicker from "@/components/island/ImagePicker.vue"
import NoticeBox from "@/components/island/NoticeBox.vue"
import PreviewFrame from "@/components/island/PreviewFrame.vue"
import type {Picture} from "@/components/island/pictures"
import {srcsetOf} from "@/components/island/pictures"
import RecordFact from "@/components/island/RecordFact.vue"
import MarkdownView from "@/components/island/MarkdownView.vue"
import RecordHead from "@/components/island/RecordHead.vue"
import SliceBand from "@/components/island/SliceBand.vue"
import {saveGameOrganisers, useCommittees} from "@/domains/committees"
import GameOrganisersPicker from "@/domains/committees/island/GameOrganisersPicker.vue"
import {GameChannelCategory} from "@/domains/discord"
import GameChannelPicker from "@/domains/discord/island/GameChannelPicker.vue"
import {enterGameInSeason, forgetCompetitionReads, useGames as useCompetitionGames} from "@/domains/esports"
import EsportsGameHead from "@/domains/esports/island/EsportsGameHead.vue"
import {addCasualGame, saveCasualGame, storeGameBanner, storeGameIcon, type CasualGame, type CasualGameDraft, type GameChannel} from "../adapters/games"
import ArchiveGameDialog from "../island/ArchiveGameDialog.vue"
import RemoveGameDialog from "../island/RemoveGameDialog.vue"
import {cellOf, initialsOf, useCasualGames} from "../useCasualGames"

/**
 * One game, added or corrected on one page for both areas it appears in. A game is one record, so
 * casual gaming and competition edit it here together, each with its own intro and its own Discord
 * channels, and the preview draws the game in both: the casual head and cell above, the
 * competition head and slice below.
 *
 * A refusal keeps what was typed. The pictures are stored when chosen and put on the game only by
 * Save, like every other field here.
 */
defineOptions({name: "GameEditor"})

const props = defineProps<{
  /** The game being corrected, or nothing where one is being added. */
  game: CasualGame | null
  /** Where the page was opened from, which the way back is named after. */
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
/** Empty means the competition pages say what the casual pages say. */
const competitionIntro = ref(props.game?.competitionIntro ?? "")
const colour = ref(props.game?.accent ?? "")
const sortIndex = ref<number | null>(props.game?.sortIndex ?? null)
const banner = ref<Picture | null>((props.game?.banner as Picture | null | undefined) ?? null)
const icon = ref<Picture | null>((props.game?.icon as Picture | null | undefined) ?? null)
const channels = ref<GameChannel[]>([...(props.game?.channels ?? [])])
const esportsChannels = ref<GameChannel[]>([...(props.game?.esportsChannels ?? [])])
const failure = ref<string | null>(null)
const saving = ref(false)

/** The committees naming the game when the page opened, so an unchanged list is not written. */
const organisersBefore = computed(() => props.game == null
  ? []
  : committees.value.filter(committee => committee.gameCodes.includes(props.game!.code)).map(committee => committee.id))
const organisers = ref<number[]>([])
// The committees may answer after the page opens; the list starts from them once they do.
watch(organisersBefore, before => { organisers.value = [...before] }, {immediate: true})
const organiserNames = computed(() => committees.value.filter(one => organisers.value.includes(one.id)))

// A new game's address follows its name until somebody types one of their own.
// TWIN: `GameService.addressFor` makes the address the api keeps.
const slugTouched = ref(false)
watch(name, typed => {
  if (adding.value && !slugTouched.value) slug.value = typed.trim().toLowerCase().replace(/[^\p{L}\p{N}]+/gu, "-").replace(/^-+|-+$/g, "")
})
const typeSlug = (value: string, handle: (value: string) => void) => {
  slugTouched.value = true
  handle(value)
}

const colourOk = computed(() => colour.value.trim() === "" || isHexColour(colour.value.trim()))
const complete = computed(() => name.value.trim() !== "" && slug.value.trim() !== "" && colourOk.value)

/** The game as it will be recorded, drawn by the preview before it is. */
const drafted = computed<CasualGame>(() => ({
  code: props.game?.code ?? "NEW",
  name: name.value.trim() || "New game",
  slug: slug.value,
  intro: intro.value,
  accent: colourOk.value ? colour.value.trim() || null : null,
  banner: banner.value,
  icon: icon.value,
  sortIndex: sortIndex.value ?? 0,
  archived: props.game?.archived ?? false,
  inCompetition: props.game?.inCompetition ?? false,
  channels: channels.value,
  competitionIntro: competitionIntro.value,
  esportsChannels: esportsChannels.value,
}))
const accent = computed(() => drafted.value.accent || "var(--color-brand)")
const competitionSays = computed(() => competitionIntro.value.trim() || intro.value.trim())
const esportsLine = computed(() => esportsChannels.value.map(one => `#${one.name}`).join(" · "))
const cells = computed(() => [cellOf(drafted.value, () => organiserNames.value.map(one => one.name))])
const slice = computed(() => [{
  id: drafted.value.code,
  title: drafted.value.name,
  meta: esportsLine.value,
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
  competitionIntro: competitionIntro.value.trim() || null,
  esportsChannels: esportsChannels.value,
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

const backLabel = computed(() => (props.area === "casual" ? "Casual" : "Competition"))
const count = (value: unknown) => (value == null ? "" : String(value))
const toCount = (raw: string, handle: (value: number | null) => void) => handle(raw === "" ? null : Number(raw))
</script>

<template>
  <edit-page
    :accent="accent"
    :back="{to: back, label: backLabel}"
    :eyebrow="game ? game.name : 'Casual and competition'"
    testid="game-edit"
    :title="game ? 'Edit game' : 'Add a game'"
  >
    <template
      v-if="game"
      #actions
    >
      <cut-button
        :href="`/casual/${game.slug}`"
        testid="game-edit-see"
      >
        See it in casual
      </cut-button>
      <cut-button
        v-if="game.inCompetition"
        :href="`/competition/${game.slug}`"
        testid="game-edit-see-competition"
      >
        See it in competition
      </cut-button>
    </template>

    <form
      id="game-edit-form"
      class="game-form"
      @submit.prevent="submit"
    >
      <form-section title="The game">
        <form-fields>
          <vv-field
            v-model="name"
            :component-props="{hint: game ? `Code ${game.code}, which never changes` : ''}"
            label="Name*"
            name="name"
            rules="required"
            test-id="game-edit-name"
          />
          <vv-field
            v-model="slug"
            label="Address*"
            name="slug"
            rules="required"
            test-id="game-edit-slug"
            :update="typeSlug"
          />
          <vv-field
            v-model="colour"
            :component="ColourControl"
            :component-props="{placeholder: '#1f6feb', testid: 'game-edit-accent-field'}"
            label="Highlight colour"
            name="accent"
            test-id="game-edit-accent"
          />
          <vv-field
            v-model="sortIndex"
            :component-props="{kind: 'count', empty: 'Last', min: 0}"
            :display="count"
            label="Order"
            name="sortIndex"
            test-id="game-edit-order"
            :update="toCount"
          />
          <image-picker
            class="form-span"
            label="Banner"
            :picture="banner"
            :store="storeGameBanner"
            testid="game-edit-banner"
            @update:picture="banner = $event"
          />
          <image-picker
            class="form-span"
            label="Icon"
            may-be-vector
            :picture="icon"
            shape="icon"
            :store="storeGameIcon"
            testid="game-edit-icon"
            @update:picture="icon = $event"
          />
        </form-fields>
      </form-section>

      <form-section
        testid="game-edit-casual"
        title="Casual gaming"
      >
        <vv-field
          v-model="intro"
          :component-props="{kind: 'markdown', maxLength: 4000}"
          label="Intro"
          name="intro"
          test-id="game-edit-intro"
        />
        <game-channel-picker
          v-model="channels"
          empty-note="The games category has no channels left to add."
          label="Games channels"
          testid="game-edit-channels"
        />
      </form-section>

      <form-section
        testid="game-edit-competition"
        title="Competition"
      >
        <vv-field
          v-model="competitionIntro"
          :component-props="{kind: 'markdown', maxLength: 4000, hint: 'Empty uses the casual intro'}"
          label="Intro"
          name="competitionIntro"
          test-id="game-edit-competition-intro"
        />
        <game-channel-picker
          v-model="esportsChannels"
          :category="GameChannelCategory.ESPORTS"
          empty-note="The esports category has no channels left to add."
          label="Esports channels"
          testid="game-edit-esports-channels"
        />
      </form-section>

      <form-section title="Committees">
        <game-organisers-picker
          v-model="organisers"
          testid="game-edit-organisers"
        />
      </form-section>

      <notice-box
        v-if="failure"
        testid="game-edit-failure"
        tone="danger"
      >
        {{ failure }}
      </notice-box>
    </form>

    <template #preview>
      <div class="game-previews">
        <section
          class="game-previews__area"
          data-testid="game-edit-preview-casual"
        >
          <h3 class="game-previews__label">
            Casual
          </h3>
          <preview-frame>
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
                <markdown-view :source="drafted.intro" />
              </template>
              <template #facts>
                <record-fact
                  v-if="channels.length > 0"
                  :label="channels.length === 1 ? 'Channel' : 'Channels'"
                >
                  {{ channels.map(one => `#${one.name}`).join(" · ") }}
                </record-fact>
                <record-fact
                  :label="organiserNames.length === 1 ? 'Committee' : 'Committees'"
                  :quiet="organiserNames.length === 0"
                >
                  {{ organiserNames.length === 0 ? "None yet" : organiserNames.map(one => one.name).join(" · ") }}
                </record-fact>
              </template>
            </record-head>
          </preview-frame>
          <div class="game-previews__cell">
            <art-cells
              :cells="cells"
              testid-prefix="game-edit-preview"
            />
          </div>
        </section>

        <section
          class="game-previews__area"
          data-testid="game-edit-preview-competition"
        >
          <h3 class="game-previews__label">
            Competition
          </h3>
          <preview-frame>
            <esports-game-head
              :accent="accent"
              :icon="icon?.url"
              :icon-srcset="srcsetOf(icon)"
              :channels="esportsChannels"
              :intro="competitionSays"
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
        </section>
      </div>
    </template>

    <template #footer>
      <div class="game-form__save">
        <div
          v-if="game"
          class="game-form__save-group"
        >
          <cut-button
            testid="game-edit-archive"
            tone="quiet"
            @click="archiving = true"
          >
            {{ game.archived ? "Bring back" : "Archive" }}
          </cut-button>
          <cut-button
            v-if="game.archived"
            testid="game-edit-remove"
            tone="danger"
            @click="removing = true"
          >
            Remove
          </cut-button>
        </div>
        <div class="game-form__save-group game-form__save-group--end">
          <cut-button
            testid="game-edit-cancel"
            tone="quiet"
            @click="emit('cancel')"
          >
            Cancel
          </cut-button>
          <cut-button
            :disabled="!complete || saving"
            testid="game-edit-save"
            tone="solid"
            @click="submit"
          >
            {{ saving ? "Saving" : adding ? "Add the game" : "Save" }}
          </cut-button>
        </div>
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
}

.game-form__save {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 0.9rem 2rem;
}

.game-form__save-group {
  display: flex;
  gap: 0.5rem;
}

.game-form__save-group--end {
  margin-left: auto;
}

.game-previews {
  display: flex;
  flex-direction: column;
  gap: 1.6rem;
}

.game-previews__area {
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
}

.game-previews__label {
  font-family: var(--font-body);
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 0.3em;
  text-transform: uppercase;
  color: var(--color-chalk);
}

.game-previews__cell {
  max-width: 16rem;
}

.game-previews__cell :deep(.art-cells) {
  grid-template-columns: minmax(0, 1fr);
}

</style>
