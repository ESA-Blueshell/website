<template>
  <v-main>
    <top-banner title="Membership" />

    <div class="mx-3">
      <div
        class="mx-auto"
        style="max-width: 800px"
      >
        <div class="my-10">
          <p class="text-h4 font-weight-light">
            Interested in becoming a member?
          </p>

          <p class="text-body-1">
            You are definitely in the right place! If you are still unsure, be sure to check out the
            rest of our website, <a
              href="https://discord.gg/23YMFQy"
              target="_blank"
              class="text-decoration-none"
            >Discord server</a>,
            and social media accounts to find out more about Blueshell Esports! By obtaining
            Blueshell
            membership you get:
          </p>
          <ul class="text-body-1">
            <li>Exclusive access to our members-only online or offline events;</li>
            <li>Special discounts in our events;</li>
            <li>Full member access to our official Discord server.</li>
          </ul>
          <p class="text-body-1">
            <br>
            Regular membership lasts for a year, starting from September 1st till August 31st of the
            following year. Furthermore, it is <b>not</b> required to have a Union Card in order to
            become a member of Blueshell.
          </p>

          <v-btn
            class="mt-5"
            color="primary"
            rounded
            style="font-size: 30px;height: 50px;"
            size="x-large"
            @click="$goto('membership/signup')"
          >
            Become a member!
          </v-btn>
        </div>
        <div class="my-10">
          <contribution-period-component />
        </div>
      </div>
    </div>
  </v-main>
</template>

<script setup>
import TopBanner from "@/components/banners/TopBanner.vue";
import {$goto} from "@/plugins/goto";
import ContributionPeriodComponent from "@/components/ContributionPeriodComponent.vue";
</script>
