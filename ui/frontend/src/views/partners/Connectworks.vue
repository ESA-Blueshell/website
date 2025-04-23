<template>
  <v-main>
    <top-banner title="Connectworks" />
    <div class="mx-3">
      <div
        class="mx-auto my-10"
        style="max-width: 800px"
      >
        <v-img
          :src="$vuetify.theme.global.current.dark?$require('@/assets/connectworksdark.png'):$require('@/assets/connectworks.png')"
          class="mb-6"
        />
        <p class="text-h4 font-weight-light">
          Who are we?
        </p>
        <p class="text-body-1">
          We love solutions that add value, so we are constantly looking for what is possible and what value this adds
          for our customers and our own organization. We are an enthusiastic group of people with a passion for IT.
          We choose colleagues not only for their knowledge of services, but also for their drive, ability to
          communicate and whether they fit in the group. This has created a group that is ready for each other,
          understands all aspects of our company and is enthusiastic about their work.
        </p>

        <p class="text-h4 font-weight-light">
          Our services
        </p>
        <p class="text-body-1 mb-0">
          What we exactly do is very broad. But luckily we can explain it well. We have divided our IT services into 6
          different solution areas.
        </p>
        <ul class="mb-4">
          <li>
            <b>Workplace:</b> From monitor or laptop, to virtual desktop and from productivity suite to automation
            tools for
            users.
          </li>
          <li>
            <b>Company network:</b> Think of WiFi, Servers, Backup, Private cloud, Azure, Direct connections, Disaster
            recovery and Network.
          </li>
          <li>
            <b>Security:</b> Securing your software with a good combination of training, tooling and awareness of the
            vulnerabilities of IT.
          </li>
          <li>
            <b>Consultancy:</b> Do you want to innovate or improve something in the IT field? Then Connectworks can
            help you
            with that.
          </li>
          <li>
            <b>Communication:</b> Landline, Mobile telephony, Teams telephony and Unified Communication are services
            that
            Connectworks offers in the field of communication.
          </li>
          <li>
            <b>Management:</b> With a focus on management, their people, tooling, documentation, customer satisfaction
            monitoring and the integration between these, Connectworks delivers the best service with a guaranteed
            customer satisfaction.
          </li>
        </ul>
        <p class="text-h4 font-weight-light">
          Our customers
        </p>
        <p class="text-body-1">
          Connectworks' customer base consists of between 400 and 450 companies in and outside the Twente region.
          More than 8,000 people use one of our services every day and we always personally speak to our customers from
          all kinds of sectors to help them with their IT.
        </p>
        <p class="text-h4 font-weight-light">
          Working at Connectworks?
        </p>
        <p class="text-body-1">
          We are always looking for new talent. We offer you the opportunity to work and develop yourself in an informal
          working environment where taking the initiative is valued. During lunch or the Friday afternoon snack (in good
          weather on our roof terrace) we mainly talk about football and formula 1. Various Netflix viewing tips are
          passed by and sandwiches are regularly bought.
          Interested? Send an e-mail with an extensive CV to <a href="mailto:sollicitatie@connectworks.nl">sollicitatie@connectworks.nl</a>
          , contact us via our general telephone number <a href="tel:085-7441430">085-7441430</a>, or check out our
          <a href="https://www.connectworks.nl">website</a>.
        </p>
        <p class="text-h4 font-weight-light">
          Location
        </p>
        <p class="text-body-1">
          Bedrijvenpark Twente 428 <br>
          7062KM Almelo, The Netherlands
        </p>
      </div>
    </div>
  </v-main>
</template>

<script>
import TopBanner from "@/components/banners/TopBanner.vue";
import {$require} from "@/plugins/require";

export default {
  name: "Connectworks",
  components: {TopBanner: TopBanner},
  methods: {$require}
}
</script>

<style scoped>

</style>
