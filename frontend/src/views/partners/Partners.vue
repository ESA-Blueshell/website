<template>
  <v-main>
    <top-banner title="Partners" />
    <div class="mx-3">
      <div
        class="mx-auto my-10"
        style="max-width: 800px"
      >
        <p class="text-body-1">
          E-sports association Blueshell is one of the fastest-growing associations at the University of Twente. In
          addition to the providing a social platform and a community to hundreds of gaming students, we also aim to
          provide our members with professional contacts and tools. Acting as the middle man between our members and
          local companies to help our students get their perfect internship spot or final assignment is one of the
          services and tasks of our association.
        </p>
        <p class="text-body-1">
          In her first year our association grew had over 120 interested people, and to provide each of these members
          with a potential contact with a company close to their interest, we are currently looking for more
          collaborations with local companies. As our member base is getting bigger, so is the number of study
          backgrounds. While a large part of the members (60%) has an IT-background, all studies are currently
          represented within our community of over 200 members.
        </p>
        <p class="text-body-1">
          As an association we are open for a collaboration with any kind of Twente-based company. We offer a variety of
          possible collaboration and sponsorship packages so that both the company and our members receive the best
          results. If you have any questions or see a potential collaboration, feel free to contact our commissioner of
          External affairs to discuss the possibilities or to plan a meeting!
        </p>
        <p class="text-body-1 font-weight-bold">
          Mail: <a
            href="mailto:external-affairs@blueshell.utwente.nl"
            target="_blank"
            class="text-decoration-none"
          >external-affairs@blueshell.utwente.nl</a>
        </p>
      </div>
    </div>
  </v-main>
</template>

<script>
import TopBanner from "@/components/banners/TopBanner.vue";

export default {
  name: "Partners",
  components: {TopBanner: TopBanner}
}
</script>

<style scoped>

</style>
