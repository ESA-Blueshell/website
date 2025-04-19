<template>
  <!--
    The code of this component is divided into 2 parts. One for larger screen sizes, and another for smaller ones.
  -->
  <div
    :style="{ 'background-image': `url(${team.bg})`}"
    style="background-size: cover;background-position: center"
    class="team-wrapper"
  >
    <!--
      Here starts the team view on size medium and above
    -->
    <v-container
      v-if="$vuetify.display.mdAndUp"
      class="py-16"
      fill-height
      fluid
      :style="{background: $vuetify.theme.global.current.dark ? 'rgba(0,0,0,0.6)' : 'rgba(255,255,255,0.8)'}"
    >
      <v-row
        align="center"
        align-content="center"
        justify="center"
      >
        <v-col
          v-if="!nameRight"
          lg="4"
          md="5"
          xl="4"
        >
          <p class="text-h2 font-italic">
            {{ team.name }}
          </p>
        </v-col>

        <v-col
          lg="6"
          md="7"
          xl="5"
        >
          <v-row
            v-for="(player,i) in team.players"
            :key="player.ign"
          >
            <v-col
              class="font-italic text-h6"
              cols="3"
            >
              {{ i !== 0 ? '' : (team.players.length > 1 ? 'Players' : 'Player') }}
            </v-col>
            <v-col
              class="text-h6"
              cols="4"
            >
              {{ player.name }}
            </v-col>
            <v-col
              class="text-right text-h6"
              cols="5"
            >
              {{ player.ign }}
            </v-col>
          </v-row>
          <v-row v-if="team.coaches || team.substitutes">
            <v-divider class="my-2" />
          </v-row>
          <v-row
            v-for="(coach,i) in team.coaches"
            :key="coach.ign"
          >
            <v-col
              class="font-italic text-h6"
              cols="3"
            >
              {{ i !== 0 ? '' : (team.coaches.length > 1 ? 'Coaches' : 'Coach') }}
            </v-col>
            <v-col
              class="text-h6"
              cols="4"
            >
              {{ coach.name }}
            </v-col>
            <v-col
              class="text-right text-h6"
              cols="5"
            >
              {{ coach.ign }}
            </v-col>
          </v-row>
          <v-row
            v-for="(substitute,i) in team.substitutes"
            :key="substitute.ign"
          >
            <v-col
              class="font-italic text-h6"
              cols="3"
            >
              {{
                i !== 0 ? '' : (team.substitutes.length > 1 ? 'Substitutes' : 'Substitute')
              }}
            </v-col>
            <v-col
              class="text-h6"
              cols="4"
            >
              {{ substitute.name }}
            </v-col>
            <v-col
              class="text-right text-h6"
              cols="5"
            >
              {{ substitute.ign }}
            </v-col>
          </v-row>
        </v-col>

        <v-col
          v-if="nameRight"
          cols="4"
        >
          <p class="text-h2 font-italic text-right">
            {{ team.name }}
          </p>
        </v-col>
      </v-row>
    </v-container>


    <!--
      Here starts the team view for smaller sizes
    -->
    <v-container
      v-else
      class="py-16"
      fill-height
      fluid
      :style="{background: $vuetify.theme.global.current.dark ? 'rgba(0,0,0,0.6)' : 'rgba(255,255,255,0.8)'}"
    >
      <v-row justify="center">
        <p class="text-h2 font-italic text-center">
          {{ team.name }}
        </p>
      </v-row>
      <v-row
        v-if="team.players"
        class="mt-n6 mb-n8"
        justify="center"
      >
        <v-col class="font-italic text-h6 text-center">
          {{ team.players.length > 1 ? 'Players' : 'Player' }}
        </v-col>
      </v-row>
      <v-row
        v-for="player in team.players"
        :key="player.ign"
        justify="center"
      >
        <v-col
          class="text-h6"
          sm="5"
          xs="6"
        >
          {{ player.name }}
        </v-col>
        <v-col
          class="text-right text-h6"
          sm="5"
          xs="6"
        >
          {{ player.ign }}
        </v-col>
      </v-row>


      <v-row v-if="team.coaches || team.substitutes">
        <v-divider class="my-2" />
      </v-row>

      <v-row
        v-if="team.coaches"
        class="mb-n8"
        justify="center"
      >
        <p class="font-italic text-h6 text-center">
          {{ team.coaches.length > 1 ? 'Coaches' : 'Coach' }}
        </p>
      </v-row>
      <v-row
        v-for="coach in team.coaches"
        :key="coach.ign"
        justify="center"
      >
        <v-col
          class="text-h6"
          sm="5"
          xs="6"
        >
          {{ coach.name }}
        </v-col>
        <v-col
          class="text-right text-h6"
          sm="5"
          xs="6"
        >
          {{ coach.ign }}
        </v-col>
      </v-row>


      <v-row
        v-if="team.substitutes"
        class="mb-n8"
        justify="center"
      >
        <p class="font-italic text-h6 text-center">
          {{ team.substitutes.length > 1 ? 'Substitutes' : 'Substitute' }}
        </p>
      </v-row>
      <v-row
        v-for="substitute in team.substitutes"
        :key="substitute.ign"
        justify="center"
      >
        <v-col
          class="text-h6"
          sm="5"
          xs="6"
        >
          {{ substitute.name }}
        </v-col>
        <v-col
          class="text-right text-h6"
          sm="5"
          xs="6"
        >
          {{ substitute.ign }}
        </v-col>
      </v-row>
    </v-container>
  </div>
</template>

<script>
export default {
  name: "Team",
  props: ["team", "nameRight"],
  // The team prop should be an Object with the following structure:
  //  {
  //   name: 'Team name',
  //   bg: $require('@/assets/backgroundImage.jpg'),
  //   players: [
  //     {
  //       name: 'Player name',
  //       ign: 'xXx_gamertag69_xXx'
  //     },
  //   ],
  //   Similarly, there are fields for coaches and substitutes. These should have the same format as the players attribute.
  // }
}
</script>
<style scoped>
.team-wrapper + .team-wrapper {
  border-top: rgb(var(--v-theme-accent)) 1px solid;
}
</style>
