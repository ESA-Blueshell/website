<script lang="ts" setup>
/**
 * The footer under every page, island or not, so it carries its own `.island` root the way the
 * bar does: one theme change moves both edges of the page together.
 */
type Glyph = {label: string, href: string, viewBox: string, paths: string[], evenOdd?: boolean}

// Filled in currentColor with the details cut out, never outlined and never brand-coloured.
const SOCIALS: Glyph[] = [
  {
    label: "Discord",
    href: "https://discord.gg/23YMFQy",
    viewBox: "0 0 24 24",
    paths: ["M20.317 4.3698a19.7913 19.7913 0 0 0-4.8851-1.5152.0741.0741 0 0 0-.0785.0371c-.211.3753-.4447.8648-.6083 1.2495-1.8447-.2762-3.68-.2762-5.4868 0-.1636-.3933-.4058-.8742-.6177-1.2495a.077.077 0 0 0-.0785-.037 19.7363 19.7363 0 0 0-4.8852 1.515.0699.0699 0 0 0-.0321.0277C.5334 9.0458-.319 13.5799.0992 18.0578a.0824.0824 0 0 0 .0312.0561c2.0528 1.5076 4.0413 2.4228 5.9929 3.0294a.0777.0777 0 0 0 .0842-.0276c.4616-.6304.8731-1.2952 1.226-1.9942a.076.076 0 0 0-.0416-.1057c-.6528-.2476-1.2743-.5495-1.8722-.8923a.077.077 0 0 1-.0076-.1277c.1258-.0943.2517-.1923.3718-.2914a.0743.0743 0 0 1 .0776-.0105c3.9278 1.7933 8.18 1.7933 12.0614 0a.0739.0739 0 0 1 .0785.0095c.1202.099.246.1981.3728.2924a.077.077 0 0 1-.0066.1276 12.2986 12.2986 0 0 1-1.873.8914.0766.0766 0 0 0-.0407.1067c.3604.698.7719 1.3628 1.225 1.9932a.076.076 0 0 0 .0842.0286c1.961-.6067 3.9495-1.5219 6.0023-3.0294a.077.077 0 0 0 .0313-.0552c.5004-5.177-.8382-9.6739-3.5485-13.6604a.061.061 0 0 0-.0312-.0286zM8.02 15.3312c-1.1825 0-2.1569-1.0857-2.1569-2.419 0-1.3332.9555-2.4189 2.157-2.4189 1.2108 0 2.1757 1.0952 2.1568 2.419 0 1.3332-.9555 2.4189-2.1569 2.4189zm7.9748 0c-1.1825 0-2.1569-1.0857-2.1569-2.419 0-1.3332.9554-2.4189 2.1569-2.4189 1.2108 0 2.1757 1.0952 2.1568 2.419 0 1.3332-.946 2.4189-2.1568 2.4189Z"],
  },
  {
    label: "Instagram",
    href: "https://www.instagram.com/esablueshell/",
    viewBox: "0 0 20 20",
    evenOdd: true,
    paths: ["M6.2 2h7.6A4.2 4.2 0 0 1 18 6.2v7.6a4.2 4.2 0 0 1-4.2 4.2H6.2A4.2 4.2 0 0 1 2 13.8V6.2A4.2 4.2 0 0 1 6.2 2Zm3.8 4a4 4 0 1 0 0 8 4 4 0 0 0 0-8Zm0 1.7a2.3 2.3 0 1 1 0 4.6 2.3 2.3 0 0 1 0-4.6Zm4.4-3.2a1.1 1.1 0 1 0 0 2.2 1.1 1.1 0 0 0 0-2.2Z"],
  },
  {
    label: "Twitch",
    href: "https://www.twitch.tv/blueshellesports",
    viewBox: "0 0 20 20",
    evenOdd: true,
    paths: [
      "M4.3 2 2.5 5.6V16h4v2.5h2.4l2.5-2.5h3.3l3.8-3.8V2Zm1.6 1.6h10.5v7.9l-2.4 2.4h-3.4l-2.3 2.3v-2.3H5.9Z",
      "M8.7 6.3h1.7v4.5H8.7Zm4.2 0h1.7v4.5h-1.7Z",
    ],
  },
  {
    label: "LinkedIn",
    href: "https://www.linkedin.com/company/blueshell-esports",
    viewBox: "0 0 20 20",
    evenOdd: true,
    paths: ["M3.3 2h13.4c.7 0 1.3.6 1.3 1.3v13.4c0 .7-.6 1.3-1.3 1.3H3.3c-.7 0-1.3-.6-1.3-1.3V3.3C2 2.6 2.6 2 3.3 2ZM5 8.2v6.8h2.2V8.2Zm1.1-3.6a1.3 1.3 0 1 0 0 2.6 1.3 1.3 0 0 0 0-2.6Zm2.8 3.6V15h2.2v-3.6c0-1 .4-1.6 1.2-1.6s1.1.6 1.1 1.6V15h2.2v-4.2c0-1.9-.9-2.8-2.4-2.8-1 0-1.7.5-2.1 1.1v-.9Z"],
  },
  {
    label: "Facebook",
    href: "https://www.facebook.com/BlueshellEsports/",
    viewBox: "0 0 20 20",
    paths: ["M18 10a8 8 0 1 0-9.25 7.9v-5.59H6.72V10h2.03V8.24c0-2 1.2-3.1 3.02-3.1.87 0 1.79.15 1.79.15v1.97h-1.01c-.99 0-1.3.62-1.3 1.25V10h2.22l-.36 2.31h-1.86v5.59A8 8 0 0 0 18 10Z"],
  },
  {
    label: "X",
    href: "https://twitter.com/BlueshellESA",
    viewBox: "0 0 24 24",
    paths: ["M18.9 1.2h3.7l-8.04 9.19L24 22.85h-7.4l-5.8-7.58-6.64 7.58H.47l8.6-9.83L0 1.15h7.6l5.24 6.93Zm-1.3 19.44h2.04L6.49 3.24H4.3Z"],
  },
  {
    label: "Email the board",
    href: "mailto:board@blueshell.utwente.nl",
    viewBox: "0 0 20 20",
    evenOdd: true,
    paths: ["M2 4h16v12H2Zm1.4 1.3v.3L10 10.7l6.6-5.1v-.3Z"],
  },
]

type Link = {text: string, to?: string, href?: string}

const COLUMNS: {title: string, links: Link[]}[] = [
  {
    title: "The association",
    links: [
      {text: "About us", to: "/aboutus"},
      {text: "Esports", to: "/esports"},
      {text: "Events", to: "/events"},
    ],
  },
  {
    title: "Contact",
    links: [
      {text: "board@blueshell.utwente.nl", href: "mailto:board@blueshell.utwente.nl"},
      {text: "Ask us on Discord", href: "https://discord.gg/23YMFQy"},
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

// A mail link opens the mail app, not a tab.
const opensTab = (href: string) => !href.startsWith("mailto:")

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
              v-for="social in SOCIALS"
              :key="social.href"
              :aria-label="social.label"
              class="site-footer__social"
              :href="social.href"
              :rel="opensTab(social.href) ? 'noopener' : undefined"
              :target="opensTab(social.href) ? '_blank' : undefined"
            >
              <svg
                aria-hidden="true"
                fill="currentColor"
                :fill-rule="social.evenOdd ? 'evenodd' : undefined"
                height="17"
                :viewBox="social.viewBox"
                width="17"
              >
                <path
                  v-for="path in social.paths"
                  :key="path"
                  :d="path"
                />
              </svg>
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
