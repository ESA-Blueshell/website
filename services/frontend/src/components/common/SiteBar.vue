<template>
  <header class="island site-bar">
    <button
      v-if="narrow"
      aria-label="Open the navigation menu"
      class="site-bar__icon site-bar__burger"
      data-testid="nav-menu-toggle"
      type="button"
      @click="drawer = !drawer"
    >
      <nav-mark
        mark="menu"
        :size="26"
      />
    </button>

    <router-link
      aria-label="Blueshell home"
      class="site-bar__logo"
      to="/"
    >
      <img
        alt="Blueshell logo"
        :src="logo"
      >
    </router-link>

    <nav
      v-if="!narrow"
      aria-label="Main"
      class="site-bar__nav"
    >
      <template
        v-for="section in sections"
        :key="section.to"
      >
        <router-link
          v-if="!section.entries"
          class="bar-button"
          :class="{'bar-button--here': covers(route.path, section)}"
          :data-testid="`nav-${section.label.toLowerCase()}`"
          :to="section.to"
        >
          {{ section.label }}
        </router-link>

        <!--
          A section with pages under it opens on hover the way it always has, and on a press or
          a keystroke for everybody the pointer leaves out. The panel is exactly as wide as the
          entry it drops from: one that grows past its trigger points at nothing.
        -->
        <!--
          Not modal: a menu in the bar is navigation, not a dialog. Modal is reka's default and
          it takes pointer events off the document while a menu is open, so the bar beside it
          stops answering the pointer that is already moving along it.
        -->
        <dropdown-menu-root
          v-else
          v-model:open="opened[section.to]"
          :modal="false"
        >
          <!--
            The pair is the trigger, so the panel hangs off the whole entry: anchored to the
            caret alone it starts halfway along the label and runs out past the right edge of
            the thing it belongs to, which is the behaviour this bar was rebuilt to stop.

            The label inside it stays a link of its own, so a press or an Enter on the label
            follows the section's page while the caret and the keyboard open the pages under it.
          -->
          <dropdown-menu-trigger
            :aria-label="`Pages under ${section.label}`"
            class="site-bar__section"
            :data-testid="`nav-${section.label.toLowerCase()}-more`"
            @click="openByHand"
            @keydown.enter="openByHand"
            @keydown.space="openByHand"
            @mouseenter="openByPointer(section.to)"
            @mouseleave="closeSection(section.to)"
          >
            <router-link
              class="bar-button"
              :class="{'bar-button--here': covers(route.path, section)}"
              :data-testid="`nav-${section.label.toLowerCase()}`"
              :to="section.to"
              @click="closeSection(section.to)"
              @keydown.enter.stop
            >
              {{ section.label }}
              <nav-mark
                class="bar-button__caret"
                mark="caret"
                :size="14"
              />
            </router-link>

            <dropdown-menu-content
              align="start"
              class="site-bar__menu"
              :side-offset="0"
              @close-auto-focus="pointerOpened && $event.preventDefault()"
              @open-auto-focus="pointerOpened && $event.preventDefault()"
            >
              <dropdown-menu-item
                v-for="entry in section.entries"
                :key="entry.to"
                as-child
              >
                <router-link
                  class="site-bar__entry"
                  :to="entry.to"
                >
                  {{ entry.label }}
                </router-link>
              </dropdown-menu-item>
            </dropdown-menu-content>
          </dropdown-menu-trigger>
        </dropdown-menu-root>
      </template>
    </nav>

    <div class="site-bar__end">
      <button
        :aria-label="darkMode ? 'Switch to the light theme' : 'Switch to the dark theme'"
        class="site-bar__icon site-bar__icon--accent"
        :class="{'roll-on': darkMode, 'roll-off': !darkMode}"
        type="button"
        @click="emit('toggleDarkMode')"
      >
        <nav-mark
          :mark="darkMode ? 'moon' : 'sun'"
          :size="24"
        />
      </button>

      <!--
        On a narrow screen one icon stands for both menus, and the panel it opens folds out of
        the right edge as the drawer's counterpart. Only somebody logged in has either.
      -->
      <button
        v-if="narrow && reader.loggedIn"
        aria-label="Your account and management"
        :aria-expanded="side"
        class="site-bar__icon"
        :data-state="side ? 'open' : 'closed'"
        data-testid="nav-account"
        type="button"
        @click="side = !side"
      >
        <nav-mark
          mark="account"
          :size="26"
        />
      </button>

      <dropdown-menu-root
        v-if="!narrow && management.length > 0"
        :modal="false"
      >
        <dropdown-menu-trigger
          aria-label="Management"
          class="site-bar__icon"
          data-testid="nav-management"
        >
          <nav-mark
            mark="management"
            :size="26"
          />
        </dropdown-menu-trigger>
        <dropdown-menu-content
          align="end"
          class="site-bar__menu site-bar__menu--wide"
          :side-offset="0"
        >
          <dropdown-menu-item
            v-for="entry in management"
            :key="entry.to"
            as-child
          >
            <router-link
              class="site-bar__entry"
              :to="entry.to"
            >
              {{ entry.label }}
            </router-link>
          </dropdown-menu-item>
        </dropdown-menu-content>
      </dropdown-menu-root>

      <dropdown-menu-root
        v-if="!narrow && reader.loggedIn"
        :modal="false"
      >
        <dropdown-menu-trigger
          aria-label="Your account"
          class="site-bar__icon"
          data-testid="nav-account"
        >
          <nav-mark
            mark="account"
            :size="26"
          />
        </dropdown-menu-trigger>
        <dropdown-menu-content
          align="end"
          class="site-bar__menu site-bar__menu--wide"
          :side-offset="0"
        >
          <dropdown-menu-item
            v-for="entry in account"
            :key="entry.to"
            as-child
          >
            <router-link
              class="site-bar__entry"
              :to="entry.to"
            >
              {{ entry.label }}
            </router-link>
          </dropdown-menu-item>
          <dropdown-menu-item
            class="site-bar__entry"
            data-testid="nav-log-out"
            @select="emit('logOut')"
          >
            Log out
          </dropdown-menu-item>
        </dropdown-menu-content>
      </dropdown-menu-root>

      <router-link
        v-if="!reader.loggedIn"
        class="bar-button bar-button--solid"
        :class="{'bar-button--here': covers(route.path, {label: 'Log in', to: '/login'})}"
        to="/login"
      >
        Log in
      </router-link>
    </div>
  </header>

  <!--
    The drawer renders the same declaration the bar does, so a reader on a phone reaches every
    page the bar offers, including the ones it used to leave out entirely.

    It is drawn only while it is open. Kept in the document and merely hidden, every label in
    the bar would have a second copy nobody can see, and anything that looks the page up by its
    text finds the copy first.
  -->
  <div
    v-if="drawer || side"
    class="site-bar-scrim"
    data-testid="nav-drawer-scrim"
    @click="closePanels"
  />
  <nav
    v-if="drawer"
    ref="drawerPanel"
    aria-label="Main, on a narrow screen"
    class="site-bar-drawer"
    data-testid="nav-drawer"
    tabindex="-1"
    @keydown.esc="drawer = false"
  >
    <template
      v-for="section in sections"
      :key="section.to"
    >
      <router-link
        v-if="!section.entries"
        class="site-bar-drawer__entry"
        :class="{'site-bar-drawer__entry--here': covers(route.path, section)}"
        :to="section.to"
        @click="drawer = false"
      >
        {{ section.label }}
      </router-link>

      <!--
        A section with pages under it starts folded, and the whole row unfolds it: a caret alone
        is too small to hit on a phone. The section's own page is the first of the pages under it.
      -->
      <template v-else>
        <button
          :aria-expanded="Boolean(unfolded[section.to])"
          class="site-bar-drawer__entry site-bar-drawer__fold"
          :class="{'site-bar-drawer__entry--here': covers(route.path, section)}"
          :data-state="unfolded[section.to] ? 'open' : 'closed'"
          :data-testid="`nav-drawer-${section.label.toLowerCase()}-more`"
          type="button"
          @click="unfolded[section.to] = !unfolded[section.to]"
        >
          {{ section.label }}
          <nav-mark
            mark="caret"
            :size="16"
          />
        </button>
        <template v-if="unfolded[section.to]">
          <router-link
            v-for="entry in section.entries"
            :key="entry.to"
            class="site-bar-drawer__entry site-bar-drawer__entry--under"
            :to="entry.to"
            @click="drawer = false"
          >
            {{ entry.label }}
          </router-link>
        </template>
      </template>
    </template>

    <div class="site-bar-drawer__social">
      <a
        v-for="social in SOCIALS"
        :key="social.href"
        :aria-label="social.label"
        class="site-bar__icon"
        :href="social.href"
        rel="noopener"
        :target="social.href.startsWith('mailto:') ? undefined : '_blank'"
      >
        <nav-mark :mark="social.mark" />
      </a>
    </div>
  </nav>

  <nav
    v-if="side"
    ref="sidePanel"
    aria-label="Your account and management"
    class="site-bar-drawer site-bar-drawer--end"
    data-testid="nav-side-panel"
    tabindex="-1"
    @keydown.esc="side = false"
  >
    <p class="site-bar-drawer__label">
      Your account
    </p>
    <router-link
      v-for="entry in account"
      :key="entry.to"
      class="site-bar-drawer__entry"
      :to="entry.to"
      @click="side = false"
    >
      {{ entry.label }}
    </router-link>
    <button
      class="site-bar-drawer__entry"
      data-testid="nav-log-out"
      type="button"
      @click="side = false; emit('logOut')"
    >
      Log out
    </button>

    <template v-if="management.length > 0">
      <p class="site-bar-drawer__label">
        Management
      </p>
      <router-link
        v-for="entry in management"
        :key="entry.to"
        class="site-bar-drawer__entry"
        :to="entry.to"
        @click="side = false"
      >
        {{ entry.label }}
      </router-link>
    </template>
  </nav>
</template>

<script lang="ts" setup>
import {computed, nextTick, reactive, ref, watch} from "vue"
import {useStore} from "vuex"
import {useRoute} from "vue-router"
import {DropdownMenuContent, DropdownMenuItem, DropdownMenuRoot, DropdownMenuTrigger} from "reka-ui"
import {useGames} from "@/domains/esports"
import {useMotionAllowed} from "@/components/island/useMotionAllowed"
import {useNarrow} from "@/components/common/useNarrow"
import NavMark from "@/components/common/NavMark.vue"
import {
  accountFor,
  covers,
  managementFor,
  sectionsFor,
  SOCIALS,
  type NavReader,
} from "@/components/common/nav"
import logo from "@/assets/topbarlogo.png"

// The theme is marked on the document and the session is ended app-wide, both of which outlive
// this bar, so the shell owns them and the bar only carries the buttons.
const {darkMode} = defineProps<{darkMode: boolean}>()

const emit = defineEmits<{
  toggleDarkMode: []
  logOut: []
}>()

/** Narrow enough that the entries belong to the drawer rather than to the bar. */
const narrow = useNarrow()

const drawer = ref<boolean>(false)
const drawerPanel = ref<HTMLElement | null>(null)

/** The account and management panel folding out of the right edge on a narrow screen. */
const side = ref<boolean>(false)
const sidePanel = ref<HTMLElement | null>(null)

/** The drawer's sections that stand unfolded, keyed by the page the section itself addresses. */
const unfolded = reactive<Record<string, boolean>>({})

const closePanels = () => {
  drawer.value = false
  side.value = false
}

/** A panel is an overlay, so it takes the keyboard when it opens and gives it back on Escape. */
watch(drawer, async (open) => {
  if (!open) return
  side.value = false
  // The section the reader is in opens unfolded, so where they are is visible without a press.
  for (const section of sections.value) unfolded[section.to] = covers(route.path, section)
  await nextTick()
  drawerPanel.value?.focus()
})

watch(side, async (open) => {
  if (!open) return
  drawer.value = false
  await nextTick()
  sidePanel.value?.focus()
})

// Widening the window past the drawer takes the side panel with it, since its triggers go.
watch(narrow, () => closePanels())

/** Which section menus are open, keyed by the page the section itself addresses. */
const opened = reactive<Record<string, boolean>>({})

/**
 * Whether the menu standing open was opened by a pointer moving along the bar.
 *
 * A menu takes the keyboard when it opens, which is right for somebody who pressed it and wrong
 * for somebody whose mouse passed over it: the focus would leave whatever they were typing in.
 */
const pointerOpened = ref<boolean>(false)

const openByPointer = (section: string) => {
  pointerOpened.value = true
  opened[section] = true
}

const closeSection = (section: string) => {
  opened[section] = false
}

/** A press or a keystroke says the reader meant it, so the menu takes the keyboard after all. */
const openByHand = () => {
  pointerOpened.value = false
}

/**
 * The island reduces motion rather than removing it, so the bar asks the same policy every other
 * band asks instead of switching its transitions off behind a media query of its own.
 */
const motion = useMotionAllowed()
const caretTravel = computed<string>(() => `${motion.duration(0.22)}s`)
const rollTravel = computed<string>(() => `${motion.duration(0.42)}s`)

/** The esports menu lists the games the association fields, as their records report them. */
const {current: currentGames} = useGames()

const store = useStore()
const route = useRoute()

const reader = computed<NavReader>(() => ({
  loggedIn: Boolean(store.getters.isLoggedIn),
  board: Boolean(store.getters.isBoard),
  admin: Boolean(store.getters.isAdmin),
  addressId: store.getters.getLogin?.addressId ?? null,
}))

const sections = computed(() => sectionsFor(currentGames.value))
const management = computed(() => managementFor(reader.value))
const account = computed(() => accountFor(reader.value))
</script>

<style lang="scss" scoped>
/*
 * The bar is the island's top edge, so everything visible here is a token island.css already
 * sets and the light half already overrides: one theme change moves the bar and the page under
 * it together. The `island` class on the root is what puts those overrides in reach.
 */
.site-bar {
  position: sticky;
  top: 0;
  z-index: 1005;
  display: flex;
  align-items: center;
  gap: 1.75rem;
  width: 100%;
  min-height: 56px;
  padding: 0 1.25rem 0 1.75rem;
  background: color-mix(in oklab, var(--color-pit) 88%, transparent);
  backdrop-filter: blur(14px);
  border-bottom: 1px solid var(--color-hairline);
  color: var(--color-chalk);
  font-family: var(--font-body);
}

.site-bar__logo img {
  display: block;
  height: 38px;
  width: auto;
}

.site-bar__nav {
  display: flex;
  align-items: center;
  gap: 2px;
}

.site-bar__section {
  position: relative;
  display: flex;
  background: none;
  border: 0;
  padding: 0;
  cursor: pointer;
}

/* Reka's panel is sized from the trigger, which is the whole entry, so it needs no width here. */
.site-bar__section .bar-button {
  cursor: pointer;
}

.site-bar__end {
  display: flex;
  align-items: center;
  gap: 2px;
  margin-left: auto;
}

/* The entry, and the rule under the section being read. Underline, never a filled pill. */
.bar-button {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  padding: 1.1rem 0.9rem;
  font-size: 0.875rem;
  font-weight: 600;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  color: var(--color-ash);
  box-shadow: inset 0 -2px 0 transparent;
  transition: color 180ms var(--ease-out-quint), box-shadow 180ms var(--ease-out-quint);
}

.bar-button:hover,
.bar-button:focus-visible,
.bar-button[data-state="open"] {
  color: var(--color-chalk);
  box-shadow: inset 0 -2px 0 color-mix(in oklab, var(--color-chalk) 32%, transparent);
}

.bar-button--here {
  color: var(--color-chalk);
  box-shadow: inset 0 -2px 0 var(--color-eyebrow);
}

/* Drawn at the weight of the rest of the chrome, and it turns over when the panel is open. */
.bar-button__caret {
  opacity: 0.8;
  transition: rotate v-bind(caretTravel) var(--ease-out-quint);
}

.bar-button[data-state="open"] .bar-button__caret {
  rotate: 180deg;
}

/* The caret is its own control, so it carries none of the label's padding on the label's side. */
.bar-button--caret {
  padding-inline: 0.35rem 0.75rem;
  margin-left: -0.75rem;
  cursor: pointer;
  background: none;
  border: 0;
}

.bar-button--solid {
  margin-left: 0.5rem;
  padding: 0.6rem 1.4rem;
  background: var(--color-brand);
  color: var(--color-void);
  box-shadow: none;
  clip-path: polygon(0.7rem 0, 100% 0, calc(100% - 0.7rem) 100%, 0 100%);
}

.bar-button--solid:hover,
.bar-button--solid:focus-visible {
  background: var(--color-brand-lit);
  color: var(--color-void);
  box-shadow: none;
}

.site-bar__icon {
  display: grid;
  place-items: center;
  width: 2.5rem;
  height: 2.5rem;
  flex: none;
  background: none;
  border: 0;
  color: var(--color-ash);
  cursor: pointer;
  transition: color 180ms var(--ease-out-quint);
}

.site-bar__icon:hover,
.site-bar__icon:focus-visible,
.site-bar__icon[data-state="open"] {
  color: var(--color-chalk);
}

.site-bar__icon--accent:hover {
  color: var(--color-eyebrow);
}

/*
 * A section's panel hangs off the entry itself rather than being placed beside it.
 *
 * Reka places a panel with a transform rounded to whole pixels, and an entry's own box is
 * fractional — the label is text — so the panel stood a third of a pixel clear of the entry on
 * one side and a third short on the other, which is visible as a seam against the bar. Pinned
 * to the entry's own box it cannot drift: it starts where the entry starts and ends where it
 * ends. It costs the collision handling, which this panel never needed, since it is never wider
 * than the entry it belongs to.
 */
.site-bar__section :deep([data-reka-popper-content-wrapper]) {
  position: absolute !important;
  inset: calc(100% + 1px) auto auto 0 !important;
  width: 100% !important;
  min-width: 0 !important;
  transform: none !important;
}

:deep(.site-bar__menu) {
  z-index: 1010;
  width: 100%;
  padding: 0;
  background: var(--color-surface);
  /*
   * Sides and top left off on purpose. A border there insets every entry by its own width, and
   * the rule marking the page you are on then stands a pixel clear of the panel's edge while
   * the entry itself stops a pixel short of the edge the bar above it keeps.
   */
  border-bottom: 1px solid var(--color-hairline);
  color: var(--color-chalk);
  font-family: var(--font-body);
  box-shadow: 0 18px 40px color-mix(in oklab, var(--color-void) 45%, transparent);
}

/*
 * The two icon menus are the exception to the trigger-width rule, and they have to be: their
 * trigger is a mark barely wider than it is tall, and a panel that narrow could not hold
 * "Manage account recovery". The rule is about a menu under a labelled entry, where a panel
 * wider than its label points at nothing. These are placed by reka as usual.
 */
:deep(.site-bar__menu--wide) {
  width: max-content;
  min-width: 12rem;
}

:deep(.site-bar__entry) {
  display: block;
  width: 100%;
  padding: 0.45rem 0.85rem;
  text-align: left;
  font-size: 0.875rem;
  letter-spacing: 0.02em;
  color: var(--color-ash);
  cursor: pointer;
  box-shadow: inset 2px 0 0 transparent;
  transition: background-color 160ms var(--ease-out-quint), color 160ms var(--ease-out-quint);
}

:deep(.site-bar__entry:hover),
:deep(.site-bar__entry:focus-visible),
:deep(.site-bar__entry[data-highlighted]) {
  background: color-mix(in oklab, var(--color-chalk) 8%, transparent);
  color: var(--color-chalk);
}

:deep(.site-bar__entry:focus-visible) {
  outline: 2px solid var(--color-brand);
  outline-offset: -2px;
}

:deep(.site-bar__entry.router-link-active) {
  color: var(--color-chalk);
  box-shadow: inset 2px 0 0 var(--color-eyebrow);
}

.site-bar-scrim {
  position: fixed;
  inset: 0;
  z-index: 1006;
  background: color-mix(in oklab, var(--color-void) 62%, transparent);
}

.site-bar-drawer {
  position: fixed;
  inset: 0 auto 0 0;
  z-index: 1007;
  display: flex;
  width: min(22rem, 86vw);
  flex-direction: column;
  gap: 0.1rem;
  overflow-y: auto;
  padding: 1rem 0.75rem 1.25rem;
  background: var(--color-pit);
  border-right: 1px solid var(--color-hairline);
  color: var(--color-chalk);
  animation: fold-out-start v-bind(caretTravel) var(--ease-out-quint);
}

.site-bar-drawer--end {
  inset: 0 0 0 auto;
  border-right: 0;
  border-left: 1px solid var(--color-hairline);
  animation-name: fold-out-end;
}

@keyframes fold-out-start {
  from { translate: -100% 0; }
}

@keyframes fold-out-end {
  from { translate: 100% 0; }
}

/* Doubled so it outranks the entry's own display, wherever the two rules sit in the sheet. */
.site-bar-drawer__entry.site-bar-drawer__fold {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}

.site-bar-drawer__fold svg {
  transition: rotate v-bind(caretTravel) var(--ease-out-quint);
}

.site-bar-drawer__fold[data-state="open"] svg {
  rotate: 180deg;
}

.site-bar-drawer__entry {
  display: block;
  padding: 0.8rem 1rem;
  text-align: left;
  background: none;
  border: 0;
  font-family: var(--font-body);
  font-size: 0.95rem;
  color: var(--color-chalk);
  text-decoration: none;
  cursor: pointer;
  box-shadow: inset 2px 0 0 transparent;
}

.site-bar-drawer__entry--under {
  padding-block: 0.65rem;
  padding-left: 1.75rem;
  font-size: 0.875rem;
  color: var(--color-ash);
}

.site-bar-drawer__entry:hover,
.site-bar-drawer__entry:focus-visible {
  background: color-mix(in oklab, var(--color-chalk) 8%, transparent);
  color: var(--color-chalk);
}

.site-bar-drawer__entry--here {
  box-shadow: inset 2px 0 0 var(--color-eyebrow);
}

.site-bar-drawer__label {
  margin: 0.75rem 0 0.2rem;
  padding: 0 1rem;
  font-family: var(--font-body);
  font-size: 0.6rem;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: var(--color-ash);
}

.site-bar-drawer__social {
  display: flex;
  flex-wrap: wrap;
  gap: 0.25rem;
  margin-top: auto;
  padding-top: 1rem;
}

@media (max-width: 1279px) {
  .site-bar {
    gap: 0.75rem;
    padding-inline: 0.75rem;
  }
}

/* The theme mark turns over as it changes, and stands still for a reader who asked it to. */
@keyframes roll-on {
  from { rotate: -120deg; opacity: 0.2; }
  to { rotate: 0deg; opacity: 1; }
}

@keyframes roll-off {
  from { rotate: 120deg; opacity: 0.2; }
  to { rotate: 0deg; opacity: 1; }
}

.roll-on svg { animation: roll-on v-bind(rollTravel) var(--ease-out-quint); }
.roll-off svg { animation: roll-off v-bind(rollTravel) var(--ease-out-quint); }
</style>
