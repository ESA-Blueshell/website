<script lang="ts" setup>
import {computed, onMounted, ref, watch} from "vue"
import ManagerCard from "@/components/common/cards/ManagerCard.vue"
import TopBanner from "@/components/common/banners/TopBanner.vue"
import UserPicker from "@/components/form/fields/UserPicker.vue"
import {$handleNetworkError} from "@/plugins/handleNetworkError"
import {
  addToRoster,
  dropRosterEntry,
  dropSeason,
  dropTeam,
  linkRosterMember,
  loadRoster,
  loadSeasons,
  loadTeams,
  saveRosterEntry,
  saveSeason,
  saveTeam,
  type Game,
  type RosterEntry,
  type Season,
  type Team,
  type TeamRole,
} from "@/domains/esports/adapters/esports"
import {Game as GameEnum, TeamRole as TeamRoleEnum} from "@/services/api"

defineOptions({name: "EsportsManagerPage"})

const GAMES = Object.values(GameEnum) as Game[]
const ROLES = Object.values(TeamRoleEnum) as TeamRole[]

const gameLabel = (game: Game) =>
  game.toLowerCase().split("_").map((word) => word.charAt(0).toUpperCase() + word.slice(1)).join(" ")

const seasons = ref<Season[]>([])
const teams = ref<Team[]>([])
const roster = ref<RosterEntry[]>([])

const game = ref<Game>(GameEnum.VALORANT)
const seasonId = ref<number | null>(null)
const teamId = ref<number | null>(null)

const seasonDialog = ref<boolean>(false)
const seasonDraft = ref<{id?: number; name: string; startDate: string; endDate: string}>({
  name: "", startDate: "", endDate: "",
})

const teamDialog = ref<boolean>(false)
const teamDraft = ref<{id?: number; name: string; image: string}>({name: "", image: ""})

const entryDialog = ref<boolean>(false)
const entryDraft = ref<{id?: number; handle: string; role: TeamRole; displayName: string; sortIndex: number}>({
  handle: "", role: "PLAYER" as TeamRole, displayName: "", sortIndex: 0,
})

const linkDialog = ref<boolean>(false)
const linkEntry = ref<RosterEntry | null>(null)
const linkUserId = ref<number | null>(null)

const selectedTeam = computed<Team | null>(
  () => teams.value.find((team) => team.id === teamId.value) ?? null,
)

const refreshSeasons = async () => {
  try {
    seasons.value = await loadSeasons()
    if (seasonId.value == null) seasonId.value = seasons.value[0]?.id ?? null
  } catch (error) {
    $handleNetworkError(error)
  }
}

const refreshTeams = async () => {
  try {
    teams.value = await loadTeams(game.value)
    if (!teams.value.some((team) => team.id === teamId.value)) teamId.value = teams.value[0]?.id ?? null
  } catch (error) {
    $handleNetworkError(error)
  }
}

const refreshRoster = async () => {
  if (teamId.value == null || seasonId.value == null) {
    roster.value = []
    return
  }
  try {
    roster.value = await loadRoster(teamId.value, seasonId.value)
  } catch (error) {
    $handleNetworkError(error)
  }
}

const openSeason = (season?: Season) => {
  seasonDraft.value = season
    ? {id: season.id, name: season.name, startDate: season.startDate, endDate: season.endDate}
    : {name: "", startDate: "", endDate: ""}
  seasonDialog.value = true
}

const submitSeason = async () => {
  try {
    await saveSeason(seasonDraft.value)
    seasonDialog.value = false
    await refreshSeasons()
  } catch (error) {
    $handleNetworkError(error)
  }
}

const removeSeason = async (season: Season) => {
  try {
    await dropSeason(season.id)
    if (seasonId.value === season.id) seasonId.value = null
    await refreshSeasons()
    await refreshRoster()
  } catch (error) {
    $handleNetworkError(error)
  }
}

const openTeam = (team?: Team) => {
  teamDraft.value = team
    ? {id: team.id, name: team.name, image: team.image ?? ""}
    : {name: "", image: ""}
  teamDialog.value = true
}

const submitTeam = async () => {
  try {
    await saveTeam({...teamDraft.value, game: game.value})
    teamDialog.value = false
    await refreshTeams()
  } catch (error) {
    $handleNetworkError(error)
  }
}

const removeTeam = async (team: Team) => {
  try {
    await dropTeam(team.id)
    await refreshTeams()
    await refreshRoster()
  } catch (error) {
    $handleNetworkError(error)
  }
}

const openEntry = (entry?: RosterEntry) => {
  entryDraft.value = entry
    ? {
      id: entry.id,
      handle: entry.handle,
      role: entry.role,
      displayName: entry.displayName ?? "",
      sortIndex: entry.sortIndex,
    }
    : {handle: "", role: "PLAYER" as TeamRole, displayName: "", sortIndex: roster.value.length}
  entryDialog.value = true
}

const submitEntry = async () => {
  if (teamId.value == null || seasonId.value == null) return
  try {
    const draft = entryDraft.value
    if (draft.id == null) {
      await addToRoster(teamId.value, {
        seasonId: seasonId.value,
        handle: draft.handle,
        role: draft.role,
        displayName: draft.displayName || null,
      })
    } else {
      await saveRosterEntry(draft.id, {
        handle: draft.handle,
        role: draft.role,
        displayName: draft.displayName || null,
        sortIndex: draft.sortIndex,
      })
    }
    entryDialog.value = false
    await refreshRoster()
  } catch (error) {
    $handleNetworkError(error)
  }
}

const removeEntry = async (entry: RosterEntry) => {
  try {
    await dropRosterEntry(entry.id)
    await refreshRoster()
  } catch (error) {
    $handleNetworkError(error)
  }
}

const openLink = (entry: RosterEntry) => {
  linkEntry.value = entry
  linkUserId.value = entry.userId ?? null
  linkDialog.value = true
}

const submitLink = async (userId: number | null) => {
  const entry = linkEntry.value
  if (entry == null) return
  try {
    await linkRosterMember(entry.id, userId)
    linkDialog.value = false
    await refreshRoster()
  } catch (error) {
    $handleNetworkError(error)
  }
}

watch(game, async () => {
  await refreshTeams()
  await refreshRoster()
})
watch([teamId, seasonId], refreshRoster)

onMounted(async () => {
  await refreshSeasons()
  await refreshTeams()
  await refreshRoster()
})
</script>

<template>
  <v-main>
    <top-banner title="Esports" />

    <v-container>
      <div class="mx-auto my-3 esports-manager">
        <manager-card
          eyebrow="Esports"
          spaced
          subtitle="Which game and season the teams below belong to"
          testid="esports-scope"
          title="Teams and rosters"
        >
          <div class="d-flex flex-wrap scope-row">
            <v-select
              v-model="game"
              data-testid="esports-game-select"
              density="compact"
              hide-details
              :item-title="gameLabel"
              :items="GAMES"
              label="Game"
            />
            <v-select
              v-model="seasonId"
              data-testid="esports-season-select"
              density="compact"
              hide-details
              item-title="name"
              item-value="id"
              :items="seasons"
              label="Season"
            />
          </div>
        </manager-card>

        <manager-card
          eyebrow="Seasons"
          flush
          spaced
          testid="esports-seasons"
        >
          <template #actions>
            <v-btn
              data-testid="esports-add-season"
              prepend-icon="mdi-plus"
              size="small"
              variant="text"
              @click="openSeason()"
            >
              Add season
            </v-btn>
          </template>

          <v-table
            class="manager-table"
            density="compact"
          >
            <thead>
              <tr>
                <th style="width: 46%">
                  Season
                </th>
                <th style="width: 22%">
                  Starts
                </th>
                <th style="width: 22%">
                  Ends
                </th>
                <th style="width: 10%" />
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="season in seasons"
                :key="season.id"
                :data-testid="`esports-season-row-${season.id}`"
              >
                <td class="font-weight-medium">
                  {{ season.name }}
                </td>
                <td class="text-medium-emphasis">
                  {{ season.startDate }}
                </td>
                <td class="text-medium-emphasis">
                  {{ season.endDate }}
                </td>
                <td class="text-right">
                  <v-menu location="bottom end">
                    <template #activator="{props: menuProps}">
                      <v-btn
                        v-bind="menuProps"
                        :aria-label="`${season.name} actions`"
                        icon="mdi-dots-vertical"
                        size="small"
                        variant="text"
                      />
                    </template>
                    <v-list density="compact">
                      <v-list-item
                        prepend-icon="mdi-pencil"
                        title="Edit"
                        @click="openSeason(season)"
                      />
                      <v-list-item
                        prepend-icon="mdi-delete"
                        title="Delete"
                        @click="removeSeason(season)"
                      />
                    </v-list>
                  </v-menu>
                </td>
              </tr>
            </tbody>
          </v-table>
        </manager-card>

        <manager-card
          :eyebrow="`${gameLabel(game)} teams`"
          flush
          spaced
          testid="esports-teams"
        >
          <template #actions>
            <v-btn
              data-testid="esports-add-team"
              prepend-icon="mdi-plus"
              size="small"
              variant="text"
              @click="openTeam()"
            >
              Add team
            </v-btn>
          </template>

          <v-table
            class="manager-table"
            density="compact"
          >
            <thead>
              <tr>
                <th style="width: 50%">
                  Team
                </th>
                <th style="width: 40%">
                  Image
                </th>
                <th style="width: 10%" />
              </tr>
            </thead>
            <tbody>
              <tr
                v-for="team in teams"
                :key="team.id"
                class="manager-table__row"
                :class="{'esports-team--selected': team.id === teamId}"
                :data-testid="`esports-team-row-${team.id}`"
                @click="teamId = team.id"
              >
                <td class="font-weight-medium">
                  {{ team.name }}
                </td>
                <td class="text-monospace text-medium-emphasis">
                  {{ team.image ?? "—" }}
                </td>
                <td class="text-right">
                  <v-menu location="bottom end">
                    <template #activator="{props: menuProps}">
                      <v-btn
                        v-bind="menuProps"
                        :aria-label="`${team.name} actions`"
                        icon="mdi-dots-vertical"
                        size="small"
                        variant="text"
                        @click.stop
                      />
                    </template>
                    <v-list density="compact">
                      <v-list-item
                        prepend-icon="mdi-pencil"
                        title="Edit"
                        @click="openTeam(team)"
                      />
                      <v-list-item
                        prepend-icon="mdi-delete"
                        title="Delete"
                        @click="removeTeam(team)"
                      />
                    </v-list>
                  </v-menu>
                </td>
              </tr>
            </tbody>
          </v-table>
        </manager-card>

        <manager-card
          :eyebrow="selectedTeam ? `${selectedTeam.name} roster` : 'Roster'"
          flush
          testid="esports-roster"
        >
          <template #actions>
            <v-btn
              :disabled="selectedTeam == null || seasonId == null"
              data-testid="esports-add-entry"
              prepend-icon="mdi-plus"
              size="small"
              variant="text"
              @click="openEntry()"
            >
              Add member
            </v-btn>
          </template>

          <p
            v-if="selectedTeam == null || seasonId == null"
            class="text-body-2 text-medium-emphasis pa-4 mb-0"
          >
            Pick a team and a season to edit a roster.
          </p>

          <v-table
            v-else
            class="manager-table"
            data-testid="esports-roster-table"
            density="compact"
          >
            <thead>
              <tr>
                <th style="width: 30%">
                  Handle
                </th>
                <th style="width: 18%">
                  Role
                </th>
                <th style="width: 26%">
                  Name
                </th>
                <th style="width: 16%">
                  Member
                </th>
                <th style="width: 10%" />
              </tr>
            </thead>
            <tbody>
              <tr v-if="roster.length === 0">
                <td
                  class="text-medium-emphasis"
                  colspan="5"
                >
                  Nobody on this roster yet.
                </td>
              </tr>
              <tr
                v-for="entry in roster"
                :key="entry.id"
                :data-testid="`esports-entry-row-${entry.id}`"
              >
                <td class="font-weight-medium">
                  {{ entry.handle }}
                </td>
                <td class="text-medium-emphasis">
                  {{ entry.role.toLowerCase() }}
                </td>
                <td class="text-medium-emphasis">
                  {{ entry.displayName ?? "—" }}
                </td>
                <td>
                  <v-chip
                    :color="entry.userId == null ? 'warning' : undefined"
                    size="small"
                    :variant="entry.userId == null ? 'flat' : 'tonal'"
                  >
                    {{ entry.userId == null ? "Unlinked" : `#${entry.userId}` }}
                  </v-chip>
                </td>
                <td class="text-right">
                  <v-menu location="bottom end">
                    <template #activator="{props: menuProps}">
                      <v-btn
                        v-bind="menuProps"
                        :aria-label="`${entry.handle} actions`"
                        :data-testid="`esports-entry-menu-${entry.id}`"
                        icon="mdi-dots-vertical"
                        size="small"
                        variant="text"
                      />
                    </template>
                    <v-list density="compact">
                      <v-list-item
                        prepend-icon="mdi-pencil"
                        title="Edit"
                        @click="openEntry(entry)"
                      />
                      <v-list-item
                        :data-testid="`esports-entry-link-${entry.id}`"
                        prepend-icon="mdi-account-search"
                        title="Link a member"
                        @click="openLink(entry)"
                      />
                      <v-list-item
                        v-if="entry.userId != null"
                        prepend-icon="mdi-account-off"
                        title="Unlink"
                        @click="submitLink(null)"
                      />
                      <v-list-item
                        prepend-icon="mdi-delete"
                        title="Remove"
                        @click="removeEntry(entry)"
                      />
                    </v-list>
                  </v-menu>
                </td>
              </tr>
            </tbody>
          </v-table>
        </manager-card>
      </div>
    </v-container>

    <v-dialog
      v-model="seasonDialog"
      max-width="480"
    >
      <v-card data-testid="esports-season-dialog">
        <v-card-title>{{ seasonDraft.id == null ? "Add season" : "Edit season" }}</v-card-title>
        <v-card-text>
          <v-text-field
            v-model="seasonDraft.name"
            data-testid="esports-season-name"
            label="Name"
          />
          <v-text-field
            v-model="seasonDraft.startDate"
            label="Starts"
            type="date"
          />
          <v-text-field
            v-model="seasonDraft.endDate"
            label="Ends"
            type="date"
          />
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn @click="seasonDialog = false">
            Cancel
          </v-btn>
          <v-btn
            color="primary"
            data-testid="esports-season-save"
            variant="flat"
            @click="submitSeason"
          >
            Save
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <v-dialog
      v-model="teamDialog"
      max-width="480"
    >
      <v-card data-testid="esports-team-dialog">
        <v-card-title>{{ teamDraft.id == null ? "Add team" : "Edit team" }}</v-card-title>
        <v-card-text>
          <v-text-field
            v-model="teamDraft.name"
            data-testid="esports-team-name"
            label="Name"
          />
          <v-text-field
            v-model="teamDraft.image"
            hint="Asset file name, e.g. valorantesports1.jpg"
            label="Image"
            persistent-hint
          />
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn @click="teamDialog = false">
            Cancel
          </v-btn>
          <v-btn
            color="primary"
            data-testid="esports-team-save"
            variant="flat"
            @click="submitTeam"
          >
            Save
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <v-dialog
      v-model="entryDialog"
      max-width="480"
    >
      <v-card data-testid="esports-entry-dialog">
        <v-card-title>{{ entryDraft.id == null ? "Add member" : "Edit member" }}</v-card-title>
        <v-card-text>
          <v-text-field
            v-model="entryDraft.handle"
            data-testid="esports-entry-handle"
            label="Handle"
          />
          <v-select
            v-model="entryDraft.role"
            :items="ROLES"
            label="Role"
          />
          <v-text-field
            v-model="entryDraft.displayName"
            hint="Held for identification; the public pages show the handle"
            label="Name"
            persistent-hint
          />
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn @click="entryDialog = false">
            Cancel
          </v-btn>
          <v-btn
            color="primary"
            data-testid="esports-entry-save"
            variant="flat"
            @click="submitEntry"
          >
            Save
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>

    <v-dialog
      v-model="linkDialog"
      max-width="520"
    >
      <v-card data-testid="esports-link-dialog">
        <v-card-title>Link a member</v-card-title>
        <v-card-text>
          <user-picker
            v-model="linkUserId"
            label="Member"
          />
        </v-card-text>
        <v-card-actions>
          <v-spacer />
          <v-btn @click="linkDialog = false">
            Cancel
          </v-btn>
          <v-btn
            color="primary"
            data-testid="esports-link-save"
            variant="flat"
            @click="submitLink(linkUserId)"
          >
            Link
          </v-btn>
        </v-card-actions>
      </v-card>
    </v-dialog>
  </v-main>
</template>

<style lang="scss" scoped>
.esports-manager {
  max-width: 980px;
}

.scope-row {
  gap: 12px;

  .v-select {
    flex: 1 1 220px;
  }
}

.esports-team--selected > td {
  background: rgba(var(--v-theme-primary), 0.08);
}
</style>
