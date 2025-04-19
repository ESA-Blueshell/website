<script>
import TopBanner from "@/components/banners/TopBanner.vue";

export default {
  components: {TopBanner: TopBanner},
  data() {
    return {
      groupAScores: [
        {
          name: 'NyperS',
          score: '2-0-0',
        },
        {
          name: 'Scaling',
          score: '1-0-1',
        },
        {
          name: 'Turtles',
          score: '0-0-2',
        },
      ],
      groupBScores: [
        {
          name: 'Waterboarders',
          score: '1-1-0',
        },
        {
          name: 'Hatsune Miku Fanclub',
          score: '0-2-0',
        },
        {
          name: 'Happy Hour',
          score: '0-1-1',
        },
      ],
      playoffs: [
        {
          team1: 'NyperS',
          score: '-',
          team2: 'Hatsune Miku Fanclub'
        },
        {
          team1: 'Waterboarders',
          score: '-',
          team2: 'Scaling'
        },
        {
          team1: 'Happy Hour',
          score: '-',
          team2: 'Turtles'
        },
      ]
    }
  }
}
</script>

<template>
  <v-main>
    <top-banner title="Circuit Showdown" />
    <div
      class="mx-auto my-3"
      style="max-width: 800px"
    >
      <p class="text-h4 font-weight-light">
        What is the Blueshell Circuit Showdown?
      </p>
      <p class="text-body-1">
        This is a competition hosted within Blueshell where teams compete in the games that are currently competitively
        being played by the teams of Blueshell. Teams generally focus on their own game and it’s hard for them to
        compete
        among each other, this will be the opportunity to do so. All to prove what team is the best team in our
        association.
      </p>
      <div class="table-container">
        <v-table>
          <thead>
            <tr>
              <th>Group A</th>
              <th>Score</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="teamScore in groupAScores"
              :key="teamScore.name"
            >
              <td class="table-cell--name">
                {{ teamScore.name }}
              </td>
              <td class="table-cell--score">
                {{ teamScore.score }}
              </td>
            </tr>
          </tbody>
        </v-table>

        <v-table>
          <thead>
            <tr>
              <th>Group B</th>
              <th>Score</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="teamScore in groupBScores"
              :key="teamScore.name"
            >
              <td class="table-cell--name">
                {{ teamScore.name }}
              </td>
              <td class="table-cell--score">
                {{ teamScore.score }}
              </td>
            </tr>
          </tbody>
        </v-table>
      </div>

      <h2>Playoffs</h2>
      <h4>Week 1</h4>
      <v-table>
        <thead>
          <tr>
            <th>Team 1</th>
            <th>Score</th>
            <th>Team 2</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="match in playoffs"
            :key="match.team1 + match.team2"
          >
            <td class="table-cell--match-team-name">
              {{ match.team1 }}
            </td>
            <td>{{ match.score }}</td>
            <td class="table-cell--match-team-name">
              {{ match.team2 }}
            </td>
          </tr>
        </tbody>
      </v-table>
    </div>
  </v-main>
</template>

<style scoped>

.table-container {
  display: flex;
  gap: 32px;
}

.table-cell--name {
  min-width: 200px;
}

.table-cell--match-team-name {
  width: 300px;
}

.table-cell--score {
  min-width: 75px;
}

.round {
  margin: 24px 0;
}

h2 {
  margin-top: 32px;
}

h4 {
  margin-bottom: 16px;
}

</style>
