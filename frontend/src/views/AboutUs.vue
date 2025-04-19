<template>
  <v-main>
    <top-banner title="Association" />
    <div class="mx-3">
      <div
        class="mx-auto my-10"
        style="max-width: 800px"
      >
        <p class="text-h4 font-weight-light">
          About us
        </p>
        <p class="text-body-1">
          We are ESA Blueshell, Twente's very own gaming and esports association, and the
          largest in the Netherlands! With 200+ members, 10+ member-run committees representing
          different games and activities, and weekly online and offline events, we aim to connect
          our members in any which way they want. Each event is organized in order to represent a
          different part of the association and the community. That means, whether you are burning
          with a competitive spirit, or looking for a chill night of couch games and drinks, there's
          enough space for everyone in Blueshell.
        </p>
        <p class="text-h4 font-weight-light">
          History
        </p>
        <p class="text-body-1">
          Since its foundation in 1961, the University of Twente has developed many associations,
          ranging from large study associations to small cultural ones. With such an enormous
          variety of content, almost any student would have no problem finding an association that
          matches their interests. However, until 2017, the university did not have an esports or
          gaming association.
        </p>
        <p class="text-body-1">
          One evening, over a couple of beers, two Civil Engineering students decided that it should
          certainly be possible for gamers, and those interested within Twente, to be able to get
          together regularly. It all began with some talks with the Student Union and the formation
          of the first board along with three other good friends. These preparations led to the
          Kick-in market of 2017, where they were overwhelmed with over 120 interested students,
          already topping that of other associations.
        </p>
        <p class="text-body-1">
          Fast forward a few months to December 12th, 2017, where the first board signed the
          official Statutes, inaugurating Blueshell Esports as an official association. From there,
          Blueshell has provided its members with regular activities, acted as a social platform to
          meet fellow members, and invites anyone interested in gaming within the Twente region to
          come and hang out.
        </p>
        <p class="text-body-1">
          Now in the {{ boardYearOrdinal }} year of Blueshell's existence, our member base and
          opportunities continue to grow. With the help from our enthusiastic members and board, we
          have become an integral part of the gaming community not only at the University of Twente
          and the Saxion but throughout The Netherlands!
        </p>
      </div>
    </div>
  </v-main>
</template>

<script>
import TopBanner from "@/components/banners/TopBanner.vue";
import {DateTime} from "luxon";

// Helper function to convert a number to its ordinal word
function getOrdinalWord(n) {
  const ordinals = [
    "first", "second", "third", "fourth", "fifth",
    "sixth", "seventh", "eighth", "ninth", "tenth",
    "eleventh", "twelfth"
  ];
  if (n <= ordinals.length) {
    return ordinals[n - 1];
  }
  // Fallback: append standard suffix if n is out of the predefined range
  let suffix = "th";
  if (n % 10 === 1 && n % 100 !== 11) suffix = "st";
  else if (n % 10 === 2 && n % 100 !== 12) suffix = "nd";
  else if (n % 10 === 3 && n % 100 !== 13) suffix = "rd";
  return n + suffix;
}

export default {
  name: "AboutUs",
  components: {TopBanner: TopBanner},
  computed: {
    boardYear() {
      return Math.floor(
        DateTime.now().diff(DateTime.fromISO("2017-12-12"), "years").years
      );
    },
    boardYearOrdinal() {
      return getOrdinalWord(this.boardYear);
    }
  }
};
</script>

<style scoped>
</style>
