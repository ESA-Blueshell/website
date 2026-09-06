import js from '@eslint/js'
import pluginVue from 'eslint-plugin-vue'
import * as parserVue from 'vue-eslint-parser'
import configTypeScript from '@typescript-eslint/eslint-plugin'
import parserTypeScript from '@typescript-eslint/parser'
import pluginVuetify from 'eslint-plugin-vuetify'
import globals from 'globals'


/**
 * The api boundary, from `docs/adr/frontend/ADR-001`:
 *
 *   - `pages/**` and `components/**` may not import `@/services/api`.
 *   - anything outside a domain reaches it through its `index.ts`.
 *
 * The ADR asked for this rule to land **before** the migration, "because an unenforced rule is
 * what produced the 63". It did not, and the count has moved by one or two per ticket ever
 * since — #1139 added one while #946 was removing three.
 *
 * So the rule is on, and the files that predate it are listed rather than waved through by a
 * warning threshold. A list cannot drift: a file leaves it when somebody moves that file behind
 * a domain, and nothing can join it without saying so in a review. New code has no exception at
 * all. #1167 is the epic that empties the list.
 *
 * One list rather than two, because both halves are one `no-restricted-imports` rule and a
 * second block naming it would replace the first rather than add to it — which is how the first
 * draft of this let a client import through.
 */
const CROSSES_THE_BOUNDARY = [
    'src/components/base/ContributionPeriodComponent.vue',
    'src/components/base/DiscordBanner.vue',
    'src/components/base/DiscordUser.vue',
    'src/components/base/EventCalendar.vue',
    'src/components/base/EventDetails.vue',
    'src/components/base/PastEventsPane.vue',
    'src/components/common/SiteBar.vue',
    'src/components/common/cards/CommitteeCard.vue',
    'src/components/common/cards/EventCard.vue',
    'src/components/common/lists/AddressUserList.vue',
    'src/components/common/lists/ContributionPeriodList.vue',
    'src/components/common/lists/EventList.vue',
    'src/components/common/lists/RecoveryUserList.vue',
    'src/components/common/modals/ContributionPeriodDialog.vue',
    'src/components/common/modals/JobTriggerDialog.vue',
    'src/components/common/modals/ManageMembershipDialog.vue',
    'src/components/common/modals/StartMembershipDialog.vue',
    'src/components/common/rows/AddressUserRow.vue',
    'src/components/common/rows/RecoveryUserRow.vue',
    'src/components/form/AddressForm.vue',
    'src/components/form/AnswersForm.vue',
    'src/components/form/CommitteeForm.vue',
    'src/components/form/EmailConfirmationPanel.vue',
    'src/components/form/EventForm.vue',
    'src/components/form/EventSignUpForm.vue',
    'src/components/form/GuestForm.vue',
    'src/components/form/MembershipForm.vue',
    'src/components/form/SurveyForm.vue',
    'src/components/form/UserForm.vue',
    'src/components/form/fields/AnswerField.vue',
    'src/components/form/fields/CohortPicker.vue',
    'src/components/form/fields/ContributionPeriodPicker.vue',
    'src/components/form/fields/EventPicker.vue',
    'src/components/form/fields/MemberTypeSelect.vue',
    'src/components/form/fields/QuestionField.vue',
    'src/components/form/fields/UserPicker.vue',
    'src/components/form/fields/UserSelect.vue',
    'src/pages/AboutUs.vue',
    'src/pages/Board.vue',
    'src/pages/Committees.vue',
    'src/pages/Esports.vue',
    'src/pages/Events.vue',
    'src/pages/Home.vue',
    'src/pages/activate/ActivateMember.vue',
    'src/pages/activate/ActivateUser.vue',
    'src/pages/blogs/BlogView.vue',
    'src/pages/blogs/BlogsView.vue',
    'src/pages/esports/GameBySlug.vue',
    'src/pages/events/EditEvent.vue',
    'src/pages/events/EventSignUps.vue',
    'src/pages/login/Account.vue',
    'src/pages/login/Address.vue',
    'src/pages/login/CreateAccount.vue',
    'src/pages/login/ForgotPassword.vue',
    'src/pages/login/Login.vue',
    'src/pages/login/ResendConfirmation.vue',
    'src/pages/login/ResetPassword.vue',
    'src/pages/management/AddressManager.vue',
    'src/pages/management/CohortCategory.vue',
    'src/pages/management/CohortDashboard.vue',
    'src/pages/management/CohortSubjectDetail.vue',
    'src/pages/management/CohortTargets.vue',
    'src/pages/management/CommitteeManager.vue',
    'src/pages/management/EmailManager.vue',
    'src/pages/management/JobManager.vue',
    'src/pages/management/RecoveryManager.vue',
    'src/pages/management/UserManager.vue',
    'src/pages/membership/Membership.vue',
    'src/pages/membership/MembershipSignUp.vue',
    'src/pages/partners/Partners.vue',
]

export default [
    {
        ignores: [
            '**/.yarn/**',
            '.pnp*',
            'node_modules/**',
            'dist/**',
            'src/assets/**',
            'src/services/api/blueshell/**',
            'src/services/api/discord/**'
        ]
    },
    {
        files: ['**/*.{js,cjs,mjs,ts,cts,mts,jsx,tsx,vue}'],
    },
    js.configs.recommended,
    ...pluginVue.configs['flat/recommended'],
    {
        plugins: {
            '@typescript-eslint': configTypeScript,
            vuetify: pluginVuetify,
        },

        languageOptions: {
            parser: parserVue,
            parserOptions: {
                parser: parserTypeScript,
                sourceType: 'module',
            },
            globals: {
                ...globals.browser,
                defineProps: 'readonly',
                defineEmits: 'readonly',
                defineExpose: 'readonly',
                withDefaults: 'readonly',
            },
        },

        rules: {
            ...configTypeScript.configs.recommended.rules,
            ...pluginVuetify.configs.base.rules,

            'vue/multi-word-component-names': 'off',
            'vue/no-v-html': 'off',
            '@typescript-eslint/no-unused-vars': ['error', {
                argsIgnorePattern: '^_',
                varsIgnorePattern: '^_',
                caughtErrorsIgnorePattern: '^_',
            }],
            '@typescript-eslint/no-explicit-any': 'error',
        },
    },
    {
        files: ['tests/**/*.{ts,vue}'],
        rules: {
            '@typescript-eslint/no-explicit-any': 'off',
            'vue/one-component-per-file': 'off',
        },
    },
    {
        files: ['vite.config.mjs', 'playwright.config.ts'],
        languageOptions: {
            globals: {
                ...globals.node,
            },
        },
    },
    {
        files: ['src/pages/**/*.{ts,vue}', 'src/components/**/*.{ts,vue}'],
        ignores: CROSSES_THE_BOUNDARY,
        rules: {
            'no-restricted-imports': ['error', {
                patterns: [
                    {
                        group: ['@/services/api', '@/services/api/**'],
                        message:
                            'A page or a component may not reach the generated client (frontend ADR-001). '
                            + 'Put the call in the domain that owns it — src/domains/<domain>/adapters — and '
                            + 'import it from that domain\'s index.ts. An enum or a response type is '
                            + 're-exported there too, as domains/contribution does for ContributionEmailKind.',
                    },
                    {
                        group: ['@/domains/*/*'],
                        message:
                            'A domain is entered through its index.ts (frontend ADR-001), so its own files '
                            + 'stay free to move. Add what you need to that domain\'s index.ts and import '
                            + 'it from there.',
                    },
                ],
            }],
        },
    },
]
