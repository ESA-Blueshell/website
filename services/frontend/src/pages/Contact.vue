<template>
  <v-main>
    <top-banner title="Contact" />
    <div class="mx-3">
      <div
        class="mx-auto my-10"
        style="max-width: 800px"
      >
        <p class="text-body-1">
          If you want to become a member see the
          <router-link
            class="contact__link"
            to="/membership"
          >
            Join us!
          </router-link>
          page. If you are just looking for
          more information, you can contact the board at
          <a
            class="text-decoration-none"
            href="mailto:board@blueshell.utwente.nl"
            target="_blank"
          >board@blueshell.utwente.nl</a>
          or join the
          <a
            class="text-decoration-none"
            :href="DISCORD_INVITE"
            target="_blank"
          >Discord</a> and send a PM to
          the
          board members.
        </p>
        <p class="text-body-1">
          Do you want to ask your question in person? Usually, you can find a board member in the
          Esports Lounge Twente during the break.
        </p>
        <div class="contact__where">
          <div>
            <p class="text-body-1">
              The post address for Blueshell is as follows:
              <br> Blueshell Esports
              <br> Postbus 217 (Bastille 49)
              <br> 7500 AE Enschede
              <br> Netherlands
            </p>
          </div>
          <div>
            <iframe
              allowfullscreen
              height="450"
              loading="lazy"
              referrerpolicy="no-referrer-when-downgrade"
              src="https://www.google.com/maps/embed?pb=!1m18!1m12!1m3!1d2443.009787256024!2d6.849677851362431!3d52.24320587966339!2m3!1f0!2f0!3f0!3m2!1i1024!2i768!4f13.1!3m3!1m2!1s0x47b8133b297e5b15%3A0x918c3341c5447636!2sEsports%20Lounge%20Twente!5e0!3m2!1sen!2snl!4v1669661043450!5m2!1sen!2snl"
              style="border:0;max-width: 100%"
              title="Map"
              width="600"
            />
          </div>
        </div>
      </div>
    </div>
  </v-main>
</template>

<script lang="ts" setup>
import TopBanner from "@/components/common/banners/TopBanner.vue"
import {DISCORD_INVITE} from "@/components/island/socialGlyphs"
</script>

<style lang="scss" scoped>
@use '../styles/settings';

iframe {
  border-radius: settings.$border-radius-root;
}

.contact__link {
  color: var(--color-brand);
}

.contact__where {
  display: grid;
  grid-template-columns: 1fr;
  gap: 1rem;
  margin-top: 2rem;
}

@media (width >= 600px) {
  .contact__where {
    grid-template-columns: 1fr 2fr;
  }
}
</style>
