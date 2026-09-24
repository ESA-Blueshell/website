<script lang="ts" setup>
/**
 * The footer under every page, island or not, so it carries its own `.island` root the way the
 * bar does: one theme change moves both edges of the page together.
 */
import SocialMark from "@/components/island/SocialMark.vue"
import {DISCORD_INVITE, opensTab, SOCIAL_ROW} from "@/components/island/socialGlyphs"


type Link = {text: string, to?: string, href?: string}

const COLUMNS: {title: string, links: Link[]}[] = [
  {
    title: "The association",
    links: [
      {text: "About us", to: "/aboutus"},
      {text: "Competition", to: "/competition"},
      {text: "Events", to: "/events"},
    ],
  },
  {
    title: "Contact",
    links: [
      {text: "board@blueshell.utwente.nl", href: "mailto:board@blueshell.utwente.nl"},
      {text: "Ask us on Discord", href: DISCORD_INVITE},
    ],
  },
  {
    title: "Our partners",
    links: [
      {text: "El Niño", to: "/partners/el-nino"},
      {text: "Marketing Maatwerk", to: "/partners/marketing-maatwerk"},
      {text: "Esports Team Twente", href: "https://esportsteamtwente.nl/"},
    ],
  },
]

const year = new Date().getFullYear()
</script>

<template>
  <footer
    class="island site-footer"
    data-testid="site-footer"
  >
    <div class="site-footer__ground">
      <div class="site-footer__wrap site-footer__main">
        <div class="site-footer__lead">
          <p class="site-footer__name">
            E-Sports Association Blueshell · Enschede
          </p>
          <div class="site-footer__socials">
            <a
              v-for="social in SOCIAL_ROW"
              :key="social.href"
              :aria-label="social.label"
              class="site-footer__social"
              :href="social.href"
              :rel="opensTab(social.href) ? 'noopener' : undefined"
              :target="opensTab(social.href) ? '_blank' : undefined"
            >
              <social-mark
                :glyph="social"
                :size="17"
              />
            </a>
          </div>
        </div>

        <nav
          aria-label="Footer"
          class="site-footer__columns"
        >
          <div
            v-for="column in COLUMNS"
            :key="column.title"
            class="site-footer__column"
          >
            <p class="site-footer__label">
              {{ column.title }}
            </p>
            <template
              v-for="link in column.links"
              :key="link.text"
            >
              <router-link
                v-if="link.to"
                class="site-footer__link"
                :to="link.to"
              >
                {{ link.text }}
              </router-link>
              <a
                v-else
                class="site-footer__link"
                :href="link.href"
                :rel="opensTab(link.href!) ? 'noopener' : undefined"
                :target="opensTab(link.href!) ? '_blank' : undefined"
              >
                <!-- An address breaks after its @ on a phone, where a third of the width is all it gets. -->
                <template v-if="link.text.includes('@')">{{ link.text.split("@")[0] }}@<wbr>{{ link.text.split("@")[1] }}</template>
                <template v-else>{{ link.text }}</template>
              </a>
            </template>
          </div>
        </nav>
      </div>

      <div class="site-footer__wrap site-footer__credits">
        <p class="site-footer__label">
          SITECIE GANG &copy; {{ year }}
        </p>
        <p class="site-footer__label">
          Built by
          <a
            class="site-footer__credit"
            href="https://jorisjonkers.dev"
            rel="noopener"
            target="_blank"
          >JorisJonkers.dev</a>
        </p>
      </div>
    </div>
  </footer>
</template>

<style lang="scss" scoped>
/* The island root sets min-height: 100% for a page; a footer is only as tall as it is. */
.site-footer {
  min-height: 0;
  flex-shrink: 0;
}

.site-footer__ground {
  background-color: var(--band-ground);
  border-top: 1px solid var(--color-hairline);
  padding: 1.25rem 0 1rem;
}

.site-footer__wrap {
  width: 100%;
  max-width: 72rem;
  margin: 0 auto;
  padding: 0 2rem;
}

.site-footer__main {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  justify-content: space-between;
  gap: 2rem 3rem;
}

.site-footer__lead {
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
}

.site-footer__name,
.site-footer__link {
  font-size: 0.8rem;
  line-height: 1.6;
  color: var(--color-ash);
}

.site-footer__link:hover,
.site-footer__link:focus-visible,
.site-footer__credit:hover,
.site-footer__credit:focus-visible {
  color: var(--color-chalk);
}

.site-footer__socials {
  display: flex;
  flex-wrap: wrap;
  gap: 0.4rem;
}

/* The house cut, and a brand fill rising from the bottom on hover or focus. */
.site-footer__social {
  position: relative;
  display: grid;
  place-items: center;
  width: 2.2rem;
  height: 2.2rem;
  overflow: hidden;
  color: var(--color-ash);
  background: color-mix(in oklab, var(--color-chalk) 6%, transparent);
  clip-path: polygon(0.45rem 0, 100% 0, calc(100% - 0.45rem) 100%, 0 100%);
  transition: color 220ms var(--ease-out-quint);
}

.site-footer__social::before {
  content: "";
  position: absolute;
  inset: 0;
  background: var(--color-brand);
  transform-origin: bottom center;
  scale: 1 0;
  transition: scale 260ms var(--ease-out-quint);
}

.site-footer__social svg {
  position: relative;
}

.site-footer__social:hover,
.site-footer__social:focus-visible {
  color: var(--color-void);
}

.site-footer__social:hover::before,
.site-footer__social:focus-visible::before {
  scale: 1 1;
}

/* The clip would cut the focus ring off, so the fill is the focus mark here. */
.site-footer__social:focus-visible {
  outline: none;
}

.site-footer__columns {
  display: flex;
  flex-wrap: wrap;
  gap: 3rem;
}

.site-footer__column {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
  min-width: 0;
}

.site-footer__label {
  font-size: 0.6rem;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: var(--color-ash);
}

.site-footer__credit {
  color: var(--color-ash);
}

.site-footer__credits {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 1.5rem;
  margin-top: 1rem;
  padding-top: 0.7rem;
  border-top: 1px solid var(--color-hairline);
}

@media (prefers-reduced-motion: reduce) {
  .site-footer__social,
  .site-footer__social::before {
    transition: none;
  }
}

/* On a phone the three link columns share one row under the lead column. */
@media (max-width: 767px) {
  .site-footer__wrap {
    padding: 0 1.25rem;
  }

  .site-footer__columns {
    display: grid;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    gap: 0.75rem;
    width: 100%;
  }

  .site-footer__link {
    overflow-wrap: anywhere;
  }
}
</style>
