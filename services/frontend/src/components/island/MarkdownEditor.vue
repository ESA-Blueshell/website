<script lang="ts" setup>
/* The document is the markdown: nothing is serialised either way, only what is drawn changes. */
import {computed, onBeforeUnmount, onMounted, ref, watch} from "vue"
import {autocompletion} from "@codemirror/autocomplete"
import {Compartment, EditorState} from "@codemirror/state"
import {drawSelection, EditorView, placeholder as showPlaceholder} from "@codemirror/view"
import {markdownEditing, replaceFromOutside} from "@/components/island/markdownEditing"
import {CODE_FONT} from "@/components/island/markdownLive"
import {loadDiscordEmoji, loadServerEmoji} from "@/components/island/discordEmoji"
import DateTimeInput from "@/components/island/DateTimeInput.vue"
import {emojiCompletion, emojiOption} from "@/components/island/markdownEmoji"
import {channelCompletion, mentionCompletion, mentionOption, mentionOptionClass} from "@/components/island/markdownMentions"
import {DESCRIPTION_CAP} from "@/plugins/descriptions"
import {TIME_STYLES, timestampText, type TimeStyle} from "@/plugins/discordTime"
import {BRAND_ACCENT} from "@/utils/brand"

defineOptions({name: "MarkdownEditor"})

const {
  placeholder = "Write in markdown.",
  disabled = false,
  minHeight = "12rem",
  labelledBy = undefined,
  describedBy = undefined,
  invalid = false,
  label = undefined,
  maxLength = DESCRIPTION_CAP,
  counted = true,
  testid = undefined,
} = defineProps<{
  placeholder?: string
  disabled?: boolean
  labelledBy?: string
  describedBy?: string
  invalid?: boolean
  /** The name read out where no element on the page names the field. */
  label?: string
  maxLength?: number
  /** Off where the field counts its characters itself. */
  counted?: boolean
  minHeight?: string
  testid?: string
}>()

const text = defineModel<string>({default: ""})

const emit = defineEmits<{blur: []}>()

/* Counted as stored, which is what the cap and Discord hold it to, and shown only near the cap:
   a server emoji takes some thirty characters for one picture. */
const NEAR = 0.9
const count = computed(() => text.value.length)
const nearCap = computed(() => counted && count.value >= maxLength * NEAR)

/* `Mod` is command on a Mac and control elsewhere, so only the wording changes. */
const onMac = typeof navigator !== "undefined" && /Mac|iPhone|iPad/.test(navigator.platform)
const mod = onMac ? "\u2318" : "Ctrl"

const said = computed(() => [
  {does: "Bold", how: `${mod} B`, looks: "**bold**"},
  {does: "Italic", how: `${mod} I`, looks: "*italic*"},
  {does: "Underline", how: "", looks: "__underline__"},
  {does: "Strike", how: "", looks: "~~struck~~"},
  {does: "Spoiler", how: "", looks: "||hidden until pressed||"},
  {does: "Heading", how: "", looks: "## Heading"},
  {does: "Small print", how: "", looks: "-# small print"},
  {does: "Link", how: "", looks: "[what it says](https://…)"},
  {does: "List", how: "", looks: "- one per line"},
  {does: "Quote", how: "", looks: "> quoted"},
  {does: "Code", how: "", looks: "`code`"},
  {does: "Emoji", how: "", looks: ":fire: becomes the emoji"},
  {does: "Mention", how: "", looks: "@name, or #channel mid-line"},
])

const helping = ref(false)

/* A moment written as Discord writes one, `<t:unix:style>`, which every reader sees in their own
   time zone. The moment is chosen first, then the style, each shown as it will read. */
const timing = ref(false)
const moment = ref("")
const unixOf = (local: string): number => Math.floor(new Date(local).getTime() / 1000)
const styled = computed(() => (moment.value === "" ? [] : TIME_STYLES.map(style => ({
  style,
  reads: timestampText(unixOf(moment.value), style),
}))))

const insertMoment = (style: TimeStyle) => {
  const at = view as EditorView
  at.dispatch(at.state.replaceSelection(`<t:${unixOf(moment.value)}:${style}>`))
  timing.value = false
  moment.value = ""
  at.focus()
}

const elsewhere = (event: Event) => {
  if (!(event.target as Element | null)?.closest?.(".island-markdown__help, .island-markdown__ask")) {
    helping.value = false
  }
}

const onEscape = (event: Event) => {
  if ((event as KeyboardEvent).key !== "Escape") return
  helping.value = false
  timing.value = false
}

// Called through `document`, or the methods lose the `this` a browser insists on.
watch(() => helping.value || timing.value, (up) => {
  if (up) document.addEventListener("keydown", onEscape)
  else document.removeEventListener("keydown", onEscape)
})

// Only the help: the picker's own calendar opens outside it, and a press there is not elsewhere.
watch(helping, (up) => {
  if (up) document.addEventListener("pointerdown", elsewhere)
  else document.removeEventListener("pointerdown", elsewhere)
})

const host = ref<HTMLElement | null>(null)
let view: EditorView | undefined
const editable = new Compartment()
const described = new Compartment()

const describing = () => EditorView.contentAttributes.of({
  ...(describedBy === undefined ? {} : {"aria-describedby": describedBy}),
  ...(invalid ? {"aria-invalid": "true"} : {}),
})

const dress = EditorView.theme({
  "&": {
    backgroundColor: "color-mix(in oklab, var(--color-chalk) 7%, transparent)",
    borderBottom: "1px solid var(--color-hairline)",
    color: "var(--color-chalk)",
    fontFamily: "var(--font-prose)",
    fontSize: "0.9rem",
  },
  "&.cm-focused": {
    outline: "none",
    borderBottomColor: BRAND_ACCENT,
    backgroundColor: "color-mix(in oklab, var(--color-chalk) 10%, transparent)",
  },
  // A variable, so a field whose label rests inside the box can make room for it.
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
  ".cm-mention": {
    padding: "0 0.2em",
    borderRadius: "3px",
    backgroundColor: "color-mix(in oklab, var(--mention, var(--color-brand)) 22%, transparent)",
    color: "color-mix(in oklab, var(--mention, var(--color-brand-lit)) 70%, var(--color-chalk))",
    fontWeight: "600",
  },
  ".cm-timestamp": {
    padding: "0 0.2em",
    borderRadius: "3px",
    backgroundColor: "color-mix(in oklab, var(--color-chalk) 10%, transparent)",
  },
  // Drawn at the size the page draws an emoji in a line of text.
  ".cm-emoji": {
    display: "inline-block",
    width: "1.375em",
    height: "1.375em",
    margin: "0 0.05em",
    verticalAlign: "-0.3em",
  },
  ".cm-quote": {
    borderLeft: "4px solid color-mix(in oklab, var(--color-chalk) 30%, transparent)",
    paddingLeft: "0.75rem",
  },
  ".cm-code": {
    padding: "0.1em 0.25em",
    borderRadius: "4px",
    backgroundColor: "color-mix(in oklab, var(--color-void) 45%, transparent)",
  },
  ".cm-codeblock": {
    padding: "0 0.6rem",
    fontFamily: CODE_FONT,
    fontSize: "0.85em",
    backgroundColor: "color-mix(in oklab, var(--color-void) 45%, transparent)",
  },
  // On the text's baseline, as typed text sits, so the caret measured off it stands where typing puts it.
  ".cm-placeholder": {display: "inline", verticalAlign: "baseline", color: "var(--color-ash)"},
  "&.cm-editor .cm-selectionBackground, ::selection": {
    backgroundColor: "color-mix(in oklab, var(--color-brand) 35%, transparent)",
  },
  ".cm-scroller": {fontFamily: "inherit"},
})

/* Held at the cap while it is typed, as a textarea's maxlength holds it: a paste keeps what fits.
   A change that does not lengthen the text always goes through, so text over the cap can be
   cut down. */
const capped = (cap: number) => EditorState.transactionFilter.of((tr) => {
  if (!tr.docChanged || tr.newDoc.length <= Math.max(cap, tr.startState.doc.length)) return tr
  const spans: {from: number, to: number, insert: string}[] = []
  tr.changes.iterChanges((from, to, _fromB, _toB, inserted) => {
    spans.push({from, to, insert: inserted.toString()})
  })
  const [only] = spans
  if (spans.length !== 1 || !only) return []
  const {from, to, insert} = only
  const room = cap - (tr.startState.doc.length - (to - from))
  if (room <= 0) return []
  return {changes: {from, to, insert: insert.slice(0, room)}, selection: {anchor: from + room}}
})

onMounted(() => {
  void loadDiscordEmoji()
  void loadServerEmoji()
  view = new EditorView({
    parent: host.value as HTMLElement,
    state: EditorState.create({
      doc: text.value,
      extensions: [
        ...markdownEditing,
        autocompletion({
          override: [emojiCompletion, mentionCompletion, channelCompletion],
          icons: false,
          activateOnTyping: true,
          addToOptions: [emojiOption, mentionOption],
          optionClass: mentionOptionClass,
        }),
        // The browser's own caret sits off the line beside the placeholder in some browsers; this one is measured off the text.
        drawSelection(),
        // An empty editor measures its caret off the placeholder; with none it falls back to the line's
        // break, which Firefox sizes to the whole line height. A zero-width one keeps it off the text.
        showPlaceholder(placeholder || "\u200b"),
        EditorView.lineWrapping,
        // Without this the editor is a div to a screen reader, not a textbox with a label.
        EditorView.contentAttributes.of({
          role: "textbox",
          "aria-multiline": "true",
          ...(labelledBy === undefined ? {} : {"aria-labelledby": labelledBy}),
          ...(label === undefined ? {} : {"aria-label": label}),
        }),
        described.of(describing()),
        capped(maxLength),
        dress,
        editable.of(EditorView.editable.of(!disabled)),
        EditorView.updateListener.of((update) => {
          if (update.docChanged) text.value = update.state.doc.toString()
        }),
        EditorView.domEventHandlers({blur: () => emit("blur")}),
      ],
    }),
  })
})

/* A value set from outside replaces the document; one typed here is already in it. */
watch(text, (said) => {
  // The editor is built on mount, so it is there for as long as this watcher can run.
  const at = view as EditorView
  if (said === at.state.doc.toString()) return
  at.dispatch(replaceFromOutside(at.state, said))
})

watch(() => [describedBy, invalid], () => {
  view?.dispatch({effects: described.reconfigure(describing())})
})

watch(() => disabled, (off) => {
  view?.dispatch({effects: editable.reconfigure(EditorView.editable.of(!off))})
})

onBeforeUnmount(() => {
  document.removeEventListener("pointerdown", elsewhere)
  document.removeEventListener("keydown", onEscape)
  view?.destroy()
  view = undefined
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

    <!-- The handful of marks a writer needs
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

    <button
      :aria-expanded="timing"
      aria-label="Write a moment each reader sees in their own time"
      class="island-markdown__ask island-markdown__ask--time"
      data-testid="markdown-time"
      type="button"
      @click="timing = !timing"
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
        <path d="M12 7.5V12l3 2" />
      </svg>
    </button>

    <div
      v-if="timing"
      class="island-markdown__help island-markdown__time"
      data-testid="markdown-time-picker"
    >
      <date-time-input
        v-model="moment"
        testid="markdown-time-when"
      />
      <button
        v-for="one in styled"
        :key="one.style"
        class="island-markdown__style"
        :data-testid="`markdown-time-${one.style}`"
        type="button"
        @click="insertMoment(one.style)"
      >
        {{ one.reads }}
      </button>
    </div>

    <div ref="host" />

    <span
      v-if="nearCap"
      class="island-markdown__count"
      :class="{'island-markdown__count--over': count > maxLength}"
      :data-testid="testid ? `${testid}-count` : undefined"
    >{{ count }}/{{ maxLength }}</span>
  </div>
</template>

<style scoped>
.island-markdown {
  position: relative;
}

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

.island-markdown__count {
  position: absolute;
  right: 0.6rem;
  bottom: 0.3rem;
  font-family: var(--font-bitmap);
  font-size: 0.68rem;
  color: var(--color-ash);
  pointer-events: none;
}

.island-markdown__count--over {
  color: var(--color-danger);
}

.island-markdown__ask--time {
  right: 2rem;
}

.island-markdown__time {
  grid-template-columns: 1fr;
  min-width: 16rem;
}

.island-markdown__style {
  padding: 0.3rem 0.4rem;
  border: 0;
  background: none;
  color: var(--color-chalk);
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.island-markdown__style:hover,
.island-markdown__style:focus-visible {
  background: color-mix(in oklab, var(--color-brand) 26%, transparent);
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

.island-markdown :deep(.cm-tooltip-autocomplete .cm-emoji) {
  width: 1.3rem;
  height: 1.3rem;
}

.island-markdown :deep(.cm-tooltip-autocomplete .cm-avatar) {
  width: 1.4rem;
  height: 1.4rem;
  border-radius: 50%;
}

/* The colour the role's pill takes, so the row and what it writes look alike. */
.island-markdown :deep(.cm-tooltip-autocomplete .cm-role) {
  color: color-mix(in oklab, var(--mention, var(--color-brand-lit)) 70%, var(--color-chalk));
  font-weight: 600;
}

.island-markdown :deep(.cm-tooltip-autocomplete .cm-option-role .cm-completionLabel) {
  display: none;
}

.island-markdown :deep(.cm-tooltip-autocomplete .cm-completionDetail) {
  margin-left: auto;
  padding-left: 1rem;
  font-style: normal;
  color: var(--color-ash);
}

.island-markdown :deep(.cm-editor) {
  min-height: var(--md-min, 12rem);
}

/* The typing fills the box, so a press anywhere in it lands in the text rather than on nothing. */
.island-markdown :deep(.cm-content) {
  min-height: var(--md-min, 12rem);
}

.island-markdown :deep(.cm-scroller) {
  overflow: auto;
}
</style>
