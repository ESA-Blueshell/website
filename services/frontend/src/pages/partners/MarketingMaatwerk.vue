<template>
  <v-main>
    <top-banner title="Marketing Maatwerk" />

    <section class="partner">
      <v-container class="py-3 py-md-2">
        <v-row
          class="justify-center align-center text-center text-md-left"
          no-gutters
        >
          <v-col
            class="d-flex justify-center expand"
            cols="12"
            md="auto"
          >
            <a
              aria-label="Visit Marketing Maatwerk"
              class="d-inline-flex"
              href="https://marketingmaatwerk.nl/"
              rel="noopener"
              target="_blank"
            >
              <!-- The gap belongs beside the text, and there is no text beside it on a phone:
                   420 plus a 40px margin overruns a 390px screen. -->
              <v-img
                :src="logoUrl"
                alt="Marketing Maatwerk logo"
                class="rounded-lg mb-6 mb-md-0 mr-md-10"
                cover
                max-width="420"
                width="100%"
              />
            </a>
          </v-col>

          <v-col
            class="d-flex flex-column justify-center"
            cols="12"
            md="auto"
          >
            <h2 class="text-h5 text-md-h4 font-weight-light mb-3">
              Contact
            </h2>

            <ul class="pa-0 ma-0 list-unstyled">
              <li class="d-flex align-center my-1">
                <v-icon
                  class="mr-2"
                  icon="mdi-earth"
                  size="18"
                />
                <a
                  :href="contact.website"
                  class="text-decoration-none"
                  rel="noopener"
                  target="_blank"
                >{{ contact.website }}</a>
              </li>

              <li class="d-flex align-center my-1">
                <v-icon
                  class="mr-2"
                  icon="mdi-email-outline"
                  size="18"
                />
                <a
                  :href="`mailto:${contact.email}`"
                  class="text-decoration-none"
                >
                  {{ contact.email }}
                </a>
              </li>

              <li class="d-flex align-center my-1">
                <v-icon
                  class="mr-2"
                  icon="mdi-phone"
                  size="18"
                />
                <a
                  :href="phoneHref"
                  class="text-decoration-none"
                >
                  {{ contact.phone }}
                </a>
              </li>

              <li class="d-flex align-center my-1">
                <v-icon
                  class="mr-2"
                  icon="mdi-form-textbox"
                  size="18"
                />
                <a
                  :href="contact.form"
                  class="text-decoration-none"
                  rel="noopener"
                  target="_blank"
                >Contact form</a>
              </li>
            </ul>

            <div class="mt-4">
              <v-btn
                :href="contact.website"
                class="mr-3"
                elevation="2"
                rel="noopener"
                rounded="pill"
                target="_blank"
                variant="elevated"
              >
                Visit website
              </v-btn>

              <v-btn
                :href="contact.form"
                elevation="2"
                rel="noopener"
                rounded="pill"
                target="_blank"
                variant="elevated"
              >
                Go to contact form
              </v-btn>
            </div>
          </v-col>
        </v-row>
      </v-container>

      <v-container
        class="pa-0 mt-2"
        style="max-width: 900px;"
      >
        <h2 class="text-h5 text-md-h4 font-weight-light mb-3">
          About Marketing Maatwerk
        </h2>
        <p class="text-body-1">
          Marketing Maatwerk specializes in building and strengthening the online presence of entrepreneurs.
          With a deep passion for web technology, we translate complex digital challenges into clear, effective
          solutions. The goal is simple: ensure entrepreneurs get found online, make a professional impression,
          and achieve sustainable growth.
        </p>

        <h3 class="text-h6 font-weight-regular mt-8 mb-3">
          What Marketing Maatwerk Does
        </h3>
        <p class="text-body-1 mb-6">
          Our services focus on the three pillars of online success:
        </p>

        <v-row
          class="mt-2"
          dense
        >
          <v-col
            v-for="pillar in pillars"
            :key="pillar.title"
            cols="12"
            md="4"
          >
            <v-card
              :href="pillar.href"
              class="h-100 rounded-lg elevation-3 expand"
            >
              <v-card-item>
                <div class="d-flex align-center">
                  <v-icon
                    :icon="pillar.icon"
                    class="mr-3"
                    size="28"
                  />
                  <h4 class="text-subtitle-1 font-weight-semibold mb-0">
                    {{ pillar.title }}
                  </h4>
                </div>
              </v-card-item>
              <v-card-text class="text-body-2">
                {{ pillar.body }}
              </v-card-text>
            </v-card>
          </v-col>
        </v-row>

        <h3 class="text-h6 font-weight-regular mt-10 mb-3">
          Focus on a Strong Foundation
        </h3>
        <p class="text-body-1">
          The philosophy of Marketing Maatwerk is that true online success starts with a rock-solid technical
          foundation. Instead of quick fixes, we invest in a sustainable strategy. The approach is expert yet
          accessible: technical subjects are explained in understandable language, so the entrepreneur knows exactly
          what is happening and why it matters.
        </p>

        <h3 class="text-h6 font-weight-regular mt-10 mb-3">
          Who is Marketing Maatwerk for?
        </h3>
        <p class="text-body-1">
          Marketing Maatwerk helps ambitious entrepreneurs and SMEs who understand that a professional online presence
          is essential. We often work with business owners who want to focus on their own expertise and confidently
          leave the technical aspects to a proactive specialist.
        </p>
      </v-container>
    </section>
  </v-main>
</template>

<script lang="ts" setup>
import {computed} from "vue"
import TopBanner from "@/components/common/banners/TopBanner.vue"
import logoUrl from "@/assets/marketing_maatwerk_logo_big.png"

interface Contact {
  website: string
  email: string
  phone: string
  form: string
}

const contact: Contact = {
  website: "https://marketingmaatwerk.nl/",
  email: "info@marketingmaatwerk.nl",
  phone: "+31 6 34218964",
  form: "https://marketingmaatwerk.nl/contact/",
}

const phoneHref = computed(() => `tel:${contact.phone.replace(/\s+/g, "")}`)

type Pillar = { title: string; body: string; icon: string, href: string }

const pillars: Pillar[] = [
  {
    title: "Professional Websites",
    icon: "mdi-web",
    body: "From fully custom builds to the 'Website in 1 Day' concept—fast, user-friendly and conversion-focused websites.",
    href: "https://marketingmaatwerk.nl/website-maatwerk/",
  },
  {
    title: "Search Engine Optimization (SEO)",
    icon: "mdi-magnify",
    body: "Technical audits, continuous optimization and clear reporting to rank for the keywords that truly matter.",
    href: "https://marketingmaatwerk.nl/seo/",
  },
  {
    title: "Reliable Web Hosting",
    icon: "mdi-shield-check",
    body: "Optimized hosting with WordPress core and plugin maintenance included for a fast, secure foundation.",
    href: "https://marketingmaatwerk.nl/webhosting/",
  },
]
</script>

<style lang="scss" scoped>
.list-unstyled {
  list-style: none;
}

.expand {
  transition: transform .2s;
  cursor: pointer;
}

.expand:hover {
  transform: scale(1.05);
  z-index: 5;
}
</style>
