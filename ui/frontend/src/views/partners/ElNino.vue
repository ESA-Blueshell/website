<template>
  <v-main>
    <top-banner title="El Niño – Digital Development" />
    <div class="mx-3">
      <div
        class="mx-auto my-10"
        style="max-width: 800px"
      >
        <v-container class="pa-0 mb-3">
          <v-row>
            <v-col>
              <v-img
                :src="$require('@/assets/elnino.png')"
                style="max-width: 450px"
                class="mb-6"
              />
            </v-col>
            <v-spacer />
            <v-col>
              <p class="text-h4 font-weight-light">
                Locations
              </p>
              <ul>
                <li>Kuipersdijk 6C, Enschede</li>
                <li>Waldorpstraat 17Q, The Hague</li>
              </ul>
            </v-col>
          </v-row>
        </v-container>

        <p class="text-h4 font-weight-light">
          Something about us
        </p>
        <p class="text-body-1">
          We’re a group of technology enthusiasts working on challenging projects for customers all around the world and
          active in different sectors. We’re dedicated to building custom web applications, e-commerce platforms, mobile
          apps and connected hardware devices. We have an international team of professionals (partly still students)
          eager to learn new things and love the challenge on working on projects that haven’t been done before.
        </p>

        <p class="text-h4 font-weight-light">
          What we offer
        </p>
        <p class="text-body-1">
          We offer everybody flexible working hours, a gym you can use for free at the office in Enschede, your own
          MacBook Pro and desk, yearly learning budget, takeout meals, movie nights at the office and many more
          activities we organise for you and sometimes for your partner. If you’re a student, you can easily combine
          work with your studies due to our flexible working hours and allowing you to work remotely when necessary.
        </p>

        <p class="text-h4 font-weight-light">
          What we’re looking for
        </p>
        <p class="text-body-1">
          We’re looking for talented people in many areas: web development, hardware development, design (UX + UI),
          testing, devops and data science. If you’ve experience in any of these areas and are interested to join our
          team, let us know!
        </p>

        <p class="text-h4 font-weight-light">
          Join us!
        </p>
        <p class="text-body-1">
          We are always in the look out of new talent to help us build cool stuff! Whether you’re a student who just
          started, a master student or a starter, we have always a special place for you.
        </p>
        <p class="text-body-1">
          See for more information:
          <a
            href="https://www.elnino.tech/vacatures"
            class="text-decoration-none"
            target="_blank"
          >www.elnino.tech/vacatures</a>
          (Dutch) or
          <a
            href="https://www.elnino.tech/getajob"
            class="text-decoration-none"
            target="_blank"
          >www.elnino.tech/getajob</a> (English).
          You can always contact Michael on WhatsApp:
          <a
            href="https://wa.me/31626978392"
            class="text-decoration-none"
            target="_blank"
          >+31626978392</a>.
          Don’t worry he won’t bite!
        </p>
      </div>
    </div>
  </v-main>
</template>

<script>
import TopBanner from "@/components/banners/TopBanner.vue";
import {$require} from "@/plugins/require";

export default {
  name: "ElNino",
  components: {TopBanner: TopBanner},
  methods: {$require}
}
</script>

<style scoped>

</style>
