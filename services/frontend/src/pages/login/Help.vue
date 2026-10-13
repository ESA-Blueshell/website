<script lang="ts" setup>
import {computed, nextTick, ref, useTemplateRef, watch} from "vue"
import {useStore} from "vuex"
import AccountFrame from "@/components/common/AccountFrame.vue"
import CutButton from "@/components/island/CutButton.vue"
import FormControl from "@/components/island/FormControl.vue"
import FormField from "@/components/island/FormField.vue"
import SearchPicker from "@/components/island/SearchPicker.vue"
import StateTag from "@/components/island/StateTag.vue"
import {discordChannel} from "@/domains/discord"
import {audiencesIn, HELP, helpAbout, helpFor, searchHelp, type Audience, type HelpReader} from "@/domains/help"

defineOptions({name: "HelpPage"})

const BOARD_EMAIL = "board@blueshell.utwente.nl"

/** Who a topic is for, as the filter and the mark on a topic say it. */
const FOR: Record<Audience, string> = {
  everyone: "Everyone",
  member: "Members",
  committee: "Committees",
  board: "Board",
  treasurer: "Treasurer",
  admin: "Admins",
}

const store = useStore()

/* A login carries the roles a higher one inherits; read as a chain all the same, so the page does
   not depend on that. The board may do a committee's and a member's work. */
const reader = computed<HelpReader>(() => {
  const admin = store.getters.isAdmin === true
  const board = admin || store.getters.isBoard === true
  return {
    member: board || store.getters.isMember === true,
    committee: board || store.getters.isActive === true,
    board,
    treasurer: board,
    admin,
  }
})

const mine = computed(() => helpFor(HELP, reader.value))

/** The roles the reader can narrow to. Somebody with one role is not asked to choose. */
const roles = computed(() => audiencesIn(mine.value))
const role = ref<Audience | null>(null)
const ALL = "all"
const roleOptions = computed(() => [{key: ALL, label: "All"}, ...roles.value.map(one => ({key: one as string, label: FOR[one]}))])
const pickRole = (key: string) => {
  role.value = roles.value.find(one => one === key) ?? null
}

const term = ref("")
const found = computed(() => searchHelp(helpAbout(mine.value, role.value), term.value))
const narrowed = computed<boolean>(() => term.value.trim() !== "" || role.value !== null)
const count = computed<number>(() => found.value.reduce((sum, section) => sum + section.topics.length, 0))

/*
 * A shorter list is read from its start. The search box follows the reader down the page, so
 * without this a search typed far down leaves them looking at the foot of the answers.
 */
const top = useTemplateRef<HTMLElement>("top")
watch([term, role], async () => {
  await nextTick()
  const start = top.value?.getBoundingClientRect().top ?? 0
  if (start < 0) top.value?.scrollIntoView({block: "start"})
})

/** The topics opened by hand. A search or a role opens every answer, since they are few and wanted. */
const opened = ref<Set<string>>(new Set())
const isOpen = (id: string): boolean => narrowed.value || opened.value.has(id)
function toggle(id: string): void {
  const next = new Set(opened.value)
  if (!next.delete(id)) next.add(id)
  opened.value = next
}

/** A paragraph that opens by naming what it is about, "Member: ...", has that name set apart. */
function parts(paragraph: string): {lead: string, rest: string} {
  const split = /^([A-Z][A-Za-z ]{2,28}):\s(.+)$/su.exec(paragraph)
  return split ? {lead: split[1]!, rest: split[2]!} : {lead: "", rest: paragraph}
}
</script>

<template>
  <account-frame
    heading="Help"
    island-content
  >
    <div
      ref="top"
      class="help"
    >
      <aside class="help__side">
        <form-control
          v-model="term"
          class="help__search"
          data-testid="help-search"
          label="Search help"
        />

        <form-field
          v-if="roles.length > 1"
          class="help__roles"
          label="Show help for"
          testid="help-role"
        >
          <template #default="{controlId, labelId}">
            <search-picker
              :control-id="controlId"
              :labelled-by="labelId"
              :options="roleOptions"
              :selected-key="role ?? ALL"
              testid-prefix="help-role-picker"
              @clear="role = null"
              @pick="pickRole"
            />
          </template>
        </form-field>

        <p
          v-if="narrowed"
          class="help__count"
          data-testid="help-count"
        >
          {{ count }} {{ count === 1 ? "result" : "results" }}
        </p>

        <nav
          aria-label="Help sections"
          class="help__index"
          :class="{'help__index--topics': narrowed}"
        >
          <template
            v-for="section in found"
            :key="section.id"
          >
            <a
              class="help__jump"
              :data-testid="`help-jump-${section.id}`"
              :href="`#help-${section.id}`"
            >
              {{ section.title }}
            </a>
            <template v-if="narrowed">
              <a
                v-for="topic in section.topics"
                :key="topic.id"
                class="help__jump help__jump--topic"
                :data-testid="`help-jump-${topic.id}`"
                :href="`#help-${topic.id}`"
              >
                {{ topic.title }}
              </a>
            </template>
          </template>
        </nav>
      </aside>

      <div class="help__main">
        <p
          v-if="found.length === 0"
          class="help__nothing"
          data-testid="help-nothing"
        >
          Nothing found. Try another word, or ask the board.
        </p>

        <section
          v-for="section in found"
          :id="`help-${section.id}`"
          :key="section.id"
          class="help__section"
          :data-testid="`help-section-${section.id}`"
        >
          <h2 class="help__section-title">
            {{ section.title }}
          </h2>
          <div class="help__rows">
            <article
              v-for="topic in section.topics"
              :id="`help-${topic.id}`"
              :key="topic.id"
              class="help__topic"
              :class="{'help__topic--open': isOpen(topic.id)}"
              :data-testid="`help-topic-${topic.id}`"
            >
              <h3 class="help__topic-head">
                <button
                  :aria-controls="`help-${topic.id}-text`"
                  :aria-expanded="isOpen(topic.id)"
                  class="help__topic-row"
                  :data-testid="`help-open-${topic.id}`"
                  type="button"
                  @click="toggle(topic.id)"
                >
                  <span class="help__topic-title">{{ topic.title }}</span>
                  <state-tag
                    v-if="topic.audience !== 'everyone'"
                    tone="quiet"
                  >
                    {{ FOR[topic.audience] }}
                  </state-tag>
                  <svg
                    aria-hidden="true"
                    class="help__topic-turn"
                    fill="none"
                    viewBox="0 0 12 8"
                  ><path
                    d="m1 1.5 5 5 5-5"
                    stroke="currentColor"
                    stroke-width="1.4"
                  /></svg>
                </button>
              </h3>
              <div
                v-show="isOpen(topic.id)"
                :id="`help-${topic.id}-text`"
                class="help__topic-text"
              >
                <p
                  v-for="(paragraph, index) in topic.body"
                  :key="index"
                  class="help__text"
                >
                  <strong v-if="parts(paragraph).lead">{{ parts(paragraph).lead }}.</strong>
                  {{ parts(paragraph).rest }}
                </p>
                <ol
                  v-if="topic.steps"
                  class="help__steps"
                >
                  <li
                    v-for="(step, index) in topic.steps"
                    :key="index"
                  >
                    {{ step }}
                  </li>
                </ol>
                <div
                  v-if="topic.links"
                  class="help__ways"
                >
                  <cut-button
                    v-for="link in topic.links"
                    :key="link.to"
                    :href="link.to"
                    small
                    tone="quiet"
                  >
                    {{ link.label }}
                  </cut-button>
                </div>
              </div>
            </article>
          </div>
        </section>

        <div
          class="help__ask"
          data-testid="help-ask"
        >
          <cut-button
            :href="`mailto:${BOARD_EMAIL}`"
            small
          >
            Email the board
          </cut-button>
          <cut-button
            away
            :href="discordChannel('board')"
            small
          >
            #board-questions
          </cut-button>
          <cut-button
            away
            :href="discordChannel('suggestions')"
            small
          >
            #sitecie
          </cut-button>
        </div>
      </div>
    </div>
  </account-frame>
</template>

<style scoped>
.help {
  display: grid;
  gap: 2rem 3rem;
  padding-top: 2rem;
  /* The search lands the answers under the site bar, not behind it. */
  scroll-margin-top: 6rem;
}

/* A grid item is as wide as its widest row unless told it may shrink; the rows scroll instead. */
.help__side {
  min-width: 0;
}

.help__search {
  width: 100%;
}

.help__count {
  margin-top: 0.75rem;
  font-size: 0.85rem;
  color: var(--color-ash);
}

/* One row that scrolls sideways on a phone, as the account tabs do; a column beside the text on a wide page. */
.help__index {
  display: flex;
  gap: 0.25rem 1.25rem;
  margin-top: 1rem;
  overflow-x: auto;
  scrollbar-width: none;
}

.help__roles {
  margin-top: 1rem;
}

.help__jump {
  flex: none;
  padding: 0.35rem 0;
  font-family: var(--font-display);
  font-size: 0.8rem;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--color-ash);
}

.help__jump:hover,
.help__jump:focus-visible {
  color: var(--color-chalk);
}

.help__jump--topic {
  font-family: var(--font-body);
  font-size: 0.85rem;
  letter-spacing: 0;
  text-transform: none;
}

.help__main {
  min-width: 0;
  max-width: 44rem;
}

/* A jump lands the heading under the site bar, not behind it. */
.help__section,
.help__topic {
  scroll-margin-top: 6rem;
}

.help__section + .help__section {
  margin-top: 2.5rem;
}

/* A section is named the way the security pages name theirs: small, spaced, in the eyebrow's colour. */
.help__section-title {
  font-family: var(--font-body);
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 0.3em;
  text-transform: uppercase;
  color: var(--color-eyebrow);
}

.help__rows {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin-top: 0.9rem;
}

.help__topic {
  background-color: var(--band-ground);
}

/* The row a topic opens by: the cut row's own shape, its edges leaning 12° as that row's do. */
.help__topic-row {
  --row-h: 3.9rem;
  --cut: calc(var(--row-h) * 0.2126);

  display: grid;
  grid-template-columns: minmax(0, 1fr) auto 0.9rem;
  align-items: center;
  gap: 0 1rem;
  width: 100%;
  min-height: var(--row-h);
  padding: 0.6rem calc(var(--cut) + 0.4rem) 0.6rem 1.2rem;
  text-align: left;
  color: var(--color-chalk);
  transition: background-color 220ms ease;
}

.help__topic-row:hover,
.help__topic-row:focus-visible {
  background-color: color-mix(in oklab, var(--color-surface) 94%, transparent);
}

.help__topic-title {
  font-family: var(--font-display);
  font-size: 1.02rem;
  line-height: 1.15;
  text-transform: uppercase;
}

@media (prefers-reduced-motion: reduce) {
  .help__topic-turn,
  .help__topic-row {
    transition: none;
  }
}

.help__topic-turn {
  width: 0.9rem;
  color: var(--color-ash);
  transition: rotate 220ms ease;
}

.help__topic--open .help__topic-turn {
  rotate: 180deg;
}

.help__topic-text {
  padding: 0.2rem 1.2rem 1.4rem;
}

.help__text,
.help__steps {
  margin-top: 0.75rem;
  line-height: 1.65;
  color: var(--color-ash);
}

.help__topic-text > :first-child {
  margin-top: 0;
}

.help__text strong {
  color: var(--color-chalk);
}

/* The island's reset takes the numbers off a list; steps are read in order, so they come back. */
.help__steps {
  padding-left: 1.4rem;
  list-style: decimal;
}

.help__steps li + li {
  margin-top: 0.35rem;
}

.help__ways,
.help__ask {
  display: flex;
  flex-wrap: wrap;
  gap: 0.6rem;
  margin-top: 1rem;
}

.help__ask {
  margin-top: 2.5rem;
  padding-top: 1.5rem;
  border-top: 1px solid var(--color-hairline);
}

.help__nothing {
  line-height: 1.65;
  color: var(--color-ash);
}

@media (min-width: 900px) {
  .help {
    grid-template-columns: 15rem minmax(0, 1fr);
  }

  .help__side {
    position: sticky;
    top: 6rem;
    align-self: start;
  }

  .help__index {
    flex-direction: column;
    overflow-x: visible;
  }

  /* The list of answers can be longer than the window; it scrolls by itself, the search stays. */
  .help__index--topics {
    max-height: calc(100vh - 20rem);
    overflow-y: auto;
  }

  .help__jump--topic {
    padding-left: 0.9rem;
  }
}
</style>
