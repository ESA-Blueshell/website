import {fileURLToPath, URL} from "node:url"
import vue from "@vitejs/plugin-vue"
import {defineConfig} from "vitest/config"

export default defineConfig({
  plugins: [
    vue(),
  ],
  resolve: {
    alias: {
      "@": fileURLToPath(new URL("./src", import.meta.url)),
    },
  },
  test: {
    environment: "jsdom",
    globals: true,
    // jsdom.ts first: it answers what the browser would before Vuetify, imported by
    // setup.ts, reads those answers once and caches them.
    setupFiles: ["./tests/jsdom.ts", "./tests/setup.ts"],
    include: ["tests/unit/**/*.test.ts"],
    exclude: ["tests/e2e/**"],
    css: true,
    server: {
      deps: {
        inline: ["vuetify"],
      },
    },
    clearMocks: true,
    restoreMocks: true,
    mockReset: true,
    coverage: {
      // istanbul, not v8, per ADR-005: v8 credits SFC branches it has no evidence
      // for, and the gate that ADR binds is read off the branch counter.
      provider: "istanbul",
      reportsDirectory: "./coverage/unit",
      reporter: ["text", "html", "lcov", "json", "json-summary"],
      include: ["src/**/*.{ts,vue}"],
      exclude: [
        "src/services/api/**",
        "src/main.ts",
      ],
      // Per-file, because there is no global gate and a project-wide number could
      // not fail for one new page anyway. Never lower a floor to make a build pass.
      thresholds: {
        perFile: true,
        // Whole folders held at every line and branch: a new file in one needs no entry here,
        // and a deleted test fails its file's floor. The names left out are held below.
        "src/components/island/!(BandSwipe|ChipPicker|ColourControl|ConfirmDialog|CutRow|DriftRow|FlickReel|HeaderBand|ImagePicker|ModalDialog|PreviewFrame|SliceBand|TaskLayout|Timeline|reelMotion|stripAxis|useSwipeArrival).{ts,vue}": {100: true, perFile: true},
        "src/components/form/fields/!(AnswerField|QuestionEditor).{ts,vue}": {100: true, perFile: true},
        "src/domains/association/island/!(HeroBand|HistoryBand|JoinHero|PlacementBand).{ts,vue}": {100: true, perFile: true},
        "src/domains/events/island/**": {100: true, perFile: true},
        "src/{pages/events/EventPage,pages/partners/Partner,components/common/modals/{Edit,Remove}SignUpDialog,domains/discord/island/DiscordBand,domains/esports/island/LineupBand}.vue": {100: true, perFile: true},
        "src/{domains/association/{adapters/association,partnerPages},domains/discord/rooms,domains/esports/island/lineupSlice,plugins/{discordMarkdown,emojiArt}}.ts": {100: true, perFile: true},

        // Held at what they cover today, so a change cannot quietly take one backwards.
        "src/pages/activate/ActivateUser.vue": { lines: 90, branches: 90, functions: 100 },
        "src/pages/membership/MembershipSignUp.vue": { lines: 90, branches: 85, functions: 90 },
        "src/components/form/MembershipForm.vue": { lines: 90, branches: 85, functions: 90 },
        "src/components/form/AddressForm.vue": { lines: 79, branches: 45, functions: 60 },
        "src/components/form/EmailConfirmationPanel.vue": { lines: 90, branches: 90, functions: 90 },
        "src/pages/login/CreateAccount.vue": { lines: 90, branches: 85, functions: 85 },
        "src/domains/discord/island/VoicePeople.vue": { lines: 100, branches: 75, functions: 100, statements: 95 },
        "src/components/form/fields/QuestionEditor.vue": { lines: 100, branches: 85, functions: 100, statements: 98 },
        "src/components/form/SurveyForm.vue": { lines: 100, branches: 90, functions: 100, statements: 100 },
        "src/components/form/EventForm.vue": { lines: 50, branches: 42, functions: 52 },
        "src/components/form/GuestForm.vue": { lines: 77, branches: 100, functions: 66 },
        "src/components/form/UserForm.vue": { lines: 81, branches: 84, functions: 65 },
        "src/components/island/Timeline.vue": { lines: 64, branches: 41, functions: 50 },
      },
    },
  },
})
