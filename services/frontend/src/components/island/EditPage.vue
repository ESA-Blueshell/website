<script lang="ts" setup>
import {ref} from "vue"
import HeaderBand from "./HeaderBand.vue"
import Island from "./Island.vue"

/**
 * A page something is edited on: the way back, what is being edited, the form, and beside it a
 * live preview of what the form makes, drawn by the same parts the public page draws it with.
 *
 * The preview stays in view while the form scrolls. On a phone it sits above the form and folds
 * away, since there is no room beside it. The save bar is the form's own and rides at the foot of
 * the form's column.
 */
defineOptions({name: "EditPage"})

const {accent = "var(--color-brand)"} = defineProps<{
  testid: string
  eyebrow: string
  title: string
  back: {to: string; label: string}
  /** The colour of what is being edited, which the head, the focus rings and Save wear. */
  accent?: string
}>()

const previewShut = ref(false)
</script>

<template>
  <v-main>
    <island
      class="edit-page"
      :data-testid="testid"
      :style="{'--edit-accent': accent}"
    >
      <header-band
        :accent="accent"
        blob="tight"
      >
        <template #head>
          <router-link
            class="edit-page__back"
            :data-testid="`${testid}-back`"
            :to="back.to"
          >
            <svg
              aria-hidden="true"
              fill="none"
              viewBox="0 0 20 12"
            ><path
              d="M20 6H3M7 1.5 1.5 6 7 10.5"
              stroke="currentColor"
              stroke-width="1.4"
            /></svg>
            {{ back.label }}
          </router-link>
          <div class="edit-page__head">
            <div>
              <p class="edit-page__eyebrow">
                {{ eyebrow }}
              </p>
              <h1 class="edit-page__title">
                {{ title }}
              </h1>
            </div>
            <div
              v-if="$slots.actions"
              class="edit-page__actions"
            >
              <slot name="actions" />
            </div>
          </div>
        </template>
      </header-band>

      <div class="edit-page__body">
        <aside
          v-if="$slots.preview"
          class="edit-page__preview"
          :class="{'edit-page__preview--shut': previewShut}"
          :data-testid="`${testid}-preview`"
        >
          <button
            :aria-expanded="!previewShut"
            class="edit-page__preview-toggle"
            type="button"
            @click="previewShut = !previewShut"
          >
            Preview
            <span aria-hidden="true">{{ previewShut ? "+" : "−" }}</span>
          </button>
          <p class="edit-page__preview-label">
            Preview
          </p>
          <div class="edit-page__preview-body">
            <slot name="preview" />
          </div>
        </aside>

        <div class="edit-page__form">
          <slot />
          <div
            v-if="$slots.footer"
            class="edit-page__save"
          >
            <slot name="footer" />
          </div>
        </div>
      </div>
    </island>
  </v-main>
</template>

<style scoped>
/* The island root fills a page; the Vuetify main around it already does. */
.edit-page {
  min-height: 0;
}

.edit-page__back {
  display: inline-flex;
  gap: 0.6rem;
  align-items: center;
  padding: 1.1rem 0 0.4rem;
  font-size: 0.85rem;
  letter-spacing: 0.04em;
  color: var(--color-ash);
  text-decoration: none;
}

.edit-page__back:hover {
  color: var(--color-chalk);
}

.edit-page__back svg {
  width: 18px;
  height: 11px;
}

.edit-page__head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: 1.5rem 2rem;
  padding-top: 0.6rem;
}

.edit-page__eyebrow {
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 0.3em;
  text-transform: uppercase;
  color: var(--color-eyebrow);
}

.edit-page__title {
  margin-top: 0.7rem;
  font-family: var(--font-display);
  font-size: 3.5rem;
  line-height: 0.95;
  text-transform: uppercase;
}

.edit-page__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.6rem;
}

.edit-page__body {
  display: grid;
  grid-template-areas: "form preview";
  grid-template-columns: minmax(0, 1fr) minmax(0, 1.1fr);
  gap: 3rem;
  align-items: start;
  width: 100%;
  max-width: 88rem;
  margin: 0 auto;
  padding: 1.5rem 2rem 3rem;
}

.edit-page__form {
  grid-area: form;
  min-width: 0;
}

.edit-page__preview {
  position: sticky;
  top: 1rem;
  grid-area: preview;
  min-width: 0;
}

.edit-page__preview-label {
  margin-bottom: 0.6rem;
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 0.3em;
  text-transform: uppercase;
  color: var(--color-eyebrow);
}

.edit-page__preview-toggle {
  display: none;
}

/* Raised and narrower than the page, so it reads as the form's own rather than as the footer. */
.edit-page__save {
  position: sticky;
  bottom: 1rem;
  z-index: 3;
  margin-top: 1.5rem;
  padding: 1rem 1.25rem;
  background-color: var(--color-surface);
  border-top: 3px solid var(--edit-accent);
  box-shadow: 0 14px 34px rgb(0 0 0 / 30%);
}

@media (max-width: 1023px) {
  .edit-page__body {
    grid-template-areas: "preview" "form";
    grid-template-columns: minmax(0, 1fr);
    gap: 1.5rem;
  }

  .edit-page__preview {
    position: static;
  }

  .edit-page__preview-label {
    display: none;
  }

  .edit-page__preview-toggle {
    display: flex;
    gap: 0.5rem;
    align-items: center;
    justify-content: space-between;
    width: 100%;
    margin-bottom: 0.6rem;
    padding: 0;
    font-size: 11px;
    font-weight: 500;
    letter-spacing: 0.3em;
    text-transform: uppercase;
    color: var(--color-eyebrow);
    cursor: pointer;
    background: none;
    border: 0;
  }

  .edit-page__preview--shut .edit-page__preview-body {
    display: none;
  }
}

@media (max-width: 767px) {
  .edit-page__title {
    font-size: 2.4rem;
  }

  .edit-page__body {
    padding: 1rem 1.25rem 2.5rem;
  }
}
</style>
