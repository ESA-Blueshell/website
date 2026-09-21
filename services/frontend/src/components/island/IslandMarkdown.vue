<script lang="ts" setup>
/**
 * The document is the markdown: nothing is serialised either way, so what is stored is what
 * was typed. Only what is drawn changes.
 */
import {computed, onBeforeUnmount, onMounted, ref, watch} from "vue"
import {acceptCompletion, autocompletion, completionKeymap} from "@codemirror/autocomplete"
import {defaultKeymap, history, historyKeymap} from "@codemirror/commands"
import {markdown, markdownLanguage} from "@codemirror/lang-markdown"
import {HighlightStyle, syntaxHighlighting} from "@codemirror/language"
import {Compartment, EditorState} from "@codemirror/state"
import {EditorView, keymap, placeholder as showPlaceholder} from "@codemirror/view"
import {tags} from "@lezer/highlight"
import {emojiCompletion} from "@/components/island/markdownEmoji"
import {markdownLive, wrapWith} from "@/components/island/markdownLive"

defineOptions({name: "IslandMarkdown"})

const {
  placeholder = "Write in markdown.",
  disabled = false,
  minHeight = "12rem",
  labelledBy = undefined,
  testid = undefined,
} = defineProps<{
  placeholder?: string
  disabled?: boolean
  /** The field's label, so what is written in here is named the way every other control is. */
  labelledBy?: string
  /** How tall the box starts; it grows with what is written. */
  minHeight?: string
  testid?: string
}>()

const text = defineModel<string>({default: ""})

/* `Mod` is command on a Mac and control elsewhere, so only the wording changes. */
const onMac = typeof navigator !== "undefined" && /Mac|iPhone|iPad/.test(navigator.platform)
const mod = onMac ? "\u2318" : "Ctrl"

const said = computed(() => [
  {does: "Bold", how: `${mod} B`, looks: "**bold**"},
  {does: "Italic", how: `${mod} I`, looks: "*italic*"},
  {does: "Heading", how: "", looks: "## Heading"},
  {does: "Link", how: "", looks: "[what it says](https://…)"},
  {does: "List", how: "", looks: "- one per line"},
  {does: "Quote", how: "", looks: "> quoted"},
  {does: "Code", how: "", looks: "`code`"},
  {does: "Emoji", how: "", looks: ":sparkles: while typing"},
])

const helping = ref(false)

/* A card that stays up after the writer has gone back to writing is a card in the way. */
const elsewhere = (event: Event) => {
  if (!(event.target as Element | null)?.closest?.(".island-markdown__help, .island-markdown__ask")) {
    helping.value = false
  }
}

const onEscape = (event: Event) => {
  if ((event as KeyboardEvent).key === "Escape") helping.value = false
}

watch(helping, (up) => {
  const how = up ? document.addEventListener : document.removeEventListener
  how("pointerdown", elsewhere)
  how("keydown", onEscape)
})

const host = ref<HTMLElement | null>(null)
let view: EditorView | undefined
const editable = new Compartment()

/* What markdown looks like once it is being read rather than typed. */
const look = HighlightStyle.define([
  {tag: tags.heading1, fontSize: "1.55rem", fontFamily: "var(--font-display)", lineHeight: "1.25"},
  {tag: tags.heading2, fontSize: "1.3rem", fontFamily: "var(--font-display)", lineHeight: "1.3"},
  {tag: tags.heading3, fontSize: "1.1rem", fontFamily: "var(--font-display)"},
  {tag: tags.strong, fontWeight: "700", color: "var(--color-chalk)"},
  {tag: tags.emphasis, fontStyle: "italic"},
  {tag: tags.strikethrough, textDecoration: "line-through", color: "var(--color-ash)"},
  {tag: tags.link, color: "var(--color-brand-lit)", textDecoration: "underline"},
  {tag: tags.url, color: "var(--color-ash)"},
  {tag: tags.monospace, fontFamily: "var(--font-bitmap)", color: "var(--color-eyebrow)"},
  {tag: tags.quote, color: "var(--color-ash)", fontStyle: "italic"},
  // Not blue: blue is what a link is, and a bullet that borrows it reads as one.
  {tag: tags.list, color: "var(--color-ash)"},
  {tag: tags.processingInstruction, color: "var(--color-ash)"},
])

const dress = EditorView.theme({
  "&": {
    backgroundColor: "color-mix(in oklab, var(--color-chalk) 7%, transparent)",
    borderBottom: "1px solid var(--color-hairline)",
    color: "var(--color-chalk)",
    fontFamily: "var(--font-body)",
    fontSize: "0.9rem",
  },
  "&.cm-focused": {
    outline: "none",
    borderBottomColor: "var(--color-brand)",
    backgroundColor: "color-mix(in oklab, var(--color-chalk) 10%, transparent)",
  },
  // The top is a variable, so a field whose label rests inside the box can make room for it.
  ".cm-content": {
    padding: "var(--md-top, 0.7rem) 1rem 0.9rem",
    lineHeight: "1.6",
    caretColor: "var(--color-chalk)",
  },
  ".cm-line": {padding: "0"},
  ".cm-bullet": {
    display: "inline-block",
    width: "1ch",
    textAlign: "center",
    color: "var(--color-ash)",
  },
  ".cm-cursor": {borderLeftColor: "var(--color-chalk)"},
  ".cm-placeholder": {color: "var(--color-ash)"},
  "&.cm-editor .cm-selectionBackground, ::selection": {
    backgroundColor: "color-mix(in oklab, var(--color-brand) 35%, transparent)",
  },
  ".cm-scroller": {fontFamily: "inherit"},
})

/** Bold and italic where a writer reaches for them, which is not by typing the stars. */
const marks = keymap.of([
  {key: "Mod-b", run: (at: EditorView) => wrapWith(at, "**")},
  {key: "Mod-i", run: (at: EditorView) => wrapWith(at, "*")},
])

onMounted(() => {
  if (!host.value) return
  view = new EditorView({
    parent: host.value,
    state: EditorState.create({
      doc: text.value,
      extensions: [
        history(),
        // Tab before everything else, and only while a list is open: it takes the emoji being
        // offered, and otherwise falls through to moving on out of the editor, which is what a
        // reader working by keyboard expects of it.
        keymap.of([{key: "Tab", run: acceptCompletion}]),
        keymap.of([...completionKeymap, ...defaultKeymap, ...historyKeymap]),
        marks,
        markdown({base: markdownLanguage}),
        syntaxHighlighting(look),
        markdownLive,
        autocompletion({override: [emojiCompletion], icons: false, activateOnTyping: true}),
        showPlaceholder(placeholder),
        EditorView.lineWrapping,
        // A box that is written in is a textbox, whatever it is built out of: without this it
        // is a div to everything that reads the page, and to anything that looks a field up by
        // the name beside it.
        EditorView.contentAttributes.of({
          role: "textbox",
          "aria-multiline": "true",
          ...(labelledBy === undefined ? {} : {"aria-labelledby": labelledBy}),
        }),
        dress,
        editable.of(EditorView.editable.of(!disabled)),
        EditorView.updateListener.of((update) => {
          if (update.docChanged) text.value = update.state.doc.toString()
        }),
      ],
    }),
  })
})

/* A value set from outside replaces the document; one typed here is already in it. */
watch(text, (said) => {
  if (!view || said === view.state.doc.toString()) return
  view.dispatch({changes: {from: 0, to: view.state.doc.length, insert: said}})
})

watch(() => disabled, (off) => {
  view?.dispatch({effects: editable.reconfigure(EditorView.editable.of(!off))})
})

onBeforeUnmount(() => {
  document.removeEventListener("pointerdown", elsewhere)
  document.removeEventListener("keydown", onEscape)
  view?.destroy()
})
</script>

<template>
  <div
    class="island-markdown"
    :data-testid="testid"
    :style="{'--md-min': minHeight}"
  >
    <button
      :aria-expanded="helping"
      aria-label="What markdown can do here"
      class="island-markdown__ask"
      type="button"
      @click="helping = !helping"
    >
      <svg
        aria-hidden="true"
        fill="none"
        stroke="currentColor"
        stroke-linecap="round"
        stroke-linejoin="round"
        stroke-width="1.6"
        viewBox="0 0 24 24"
      >
        <circle
          cx="12"
          cy="12"
          r="9"
        />
        <path d="M9.6 9.3a2.5 2.5 0 1 1 3 2.4v1.6" />
        <path d="M12.6 16.6h-1.2" />
      </svg>
    </button>

    <!-- A card rather than a page of documentation: what a writer needs is the handful of marks
         they will use and the two keys that are quicker than typing them. -->
    <dl
      v-if="helping"
      class="island-markdown__help"
    >
      <template
        v-for="one in said"
        :key="one.does"
      >
        <dt>
          {{ one.does }}<span
            v-if="one.how"
            class="island-markdown__key"
          >{{ one.how }}</span>
        </dt>
        <dd>{{ one.looks }}</dd>
      </template>
    </dl>

    <div ref="host" />
  </div>
</template>

<style scoped>
.island-markdown {
  position: relative;
}

/* Over the text rather than beside it: a toolbar row is a second thing to look at. */
.island-markdown__ask {
  position: absolute;
  top: 0.35rem;
  right: 0.4rem;
  z-index: 2;
  display: grid;
  place-items: center;
  padding: 0.25rem;
  border: 0;
  background: none;
  color: var(--color-ash);
  cursor: pointer;
}

.island-markdown__ask:hover,
.island-markdown__ask[aria-expanded="true"] {
  color: var(--color-chalk);
}

.island-markdown__ask svg {
  width: 17px;
  height: 17px;
}

.island-markdown__help {
  position: absolute;
  top: 2rem;
  right: 0.4rem;
  z-index: 3;
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 0.3rem 1rem;
  margin: 0;
  padding: 0.7rem 0.9rem;
  background-color: var(--color-surface);
  border: 1px solid var(--color-hairline);
  box-shadow: 0 18px 40px color-mix(in oklab, var(--color-void) 45%, transparent);
  font-family: var(--font-body);
  font-size: 0.78rem;
}

.island-markdown__help dt {
  display: flex;
  align-items: baseline;
  gap: 0.5rem;
  color: var(--color-chalk);
}

.island-markdown__help dd {
  margin: 0;
  font-family: var(--font-bitmap);
  font-size: 0.7rem;
  color: var(--color-ash);
}

.island-markdown__key {
  font-family: var(--font-bitmap);
  font-size: 0.68rem;
  color: var(--color-eyebrow);
}

/* The completion list is the island's list, not CodeMirror's. */
.island-markdown :deep(.cm-tooltip-autocomplete) {
  background-color: var(--color-surface);
  border: 1px solid var(--color-hairline);
  box-shadow: 0 18px 40px color-mix(in oklab, var(--color-void) 45%, transparent);
}

.island-markdown :deep(.cm-tooltip-autocomplete ul li) {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.35rem 0.6rem;
  font-family: var(--font-body);
  font-size: 0.82rem;
  color: var(--color-chalk);
}

.island-markdown :deep(.cm-tooltip-autocomplete ul li[aria-selected]) {
  background-color: color-mix(in oklab, var(--color-brand) 26%, transparent);
  color: var(--color-chalk);
}

.island-markdown :deep(.cm-completionDetail) {
  margin-left: auto;
  font-style: normal;
  font-size: 1rem;
}

.island-markdown :deep(.cm-editor) {
  min-height: var(--md-min, 12rem);
}

.island-markdown :deep(.cm-scroller) {
  overflow: auto;
}
</style>
