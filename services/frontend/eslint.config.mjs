import js from '@eslint/js'
import pluginVue from 'eslint-plugin-vue'
import * as parserVue from 'vue-eslint-parser'
import configTypeScript from '@typescript-eslint/eslint-plugin'
import parserTypeScript from '@typescript-eslint/parser'
import pluginVuetify from 'eslint-plugin-vuetify'
import globals from 'globals'
import { readdirSync } from 'node:fs'


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
    'src/components/base/EventCalendar.vue',
    'src/components/base/EventDetails.vue',
    'src/components/base/PastEventsPane.vue',
    'src/components/common/cards/EventCard.vue',
    'src/components/common/lists/ContributionPeriodList.vue',
    'src/components/common/lists/EventList.vue',
    'src/components/common/modals/ContributionPeriodDialog.vue',
    'src/components/form/AddressForm.vue',
    'src/components/form/EventForm.vue',
    'src/components/form/EventSignUpForm.vue',
    'src/components/form/MembershipForm.vue',
    'src/components/form/UserForm.vue',
    'src/components/form/fields/ContributionPeriodPicker.vue',
    'src/components/form/fields/EventPicker.vue',
    'src/pages/Events.vue',
    'src/pages/events/EventSignUps.vue',
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
                        // A component is imported where it is drawn. Routing components through a
                        // barrel loads a domain's whole surface into anything that renders one —
                        // which broke two unit suites and, measured, saved nothing in the bundle.
                        regex: '^@/domains/[a-z]+/(?!.*\\.vue$).+',
                        message:
                            'A domain is entered through its index.ts (frontend ADR-001), so its own files '
                            + 'stay free to move. Add what you need to that domain\'s index.ts and import '
                            + 'it from there. A .vue component is the exception: import it at its own path.',
                    },
                ],
            }],
        },
    },

    // The same rule, pointed the other way: a domain may not reach into another domain's
    // innards either. It cannot be one pattern, because "another domain" is relative to the
    // file doing the importing — so there is one block per domain, each naming only its own
    // files and excusing only its own name.
    //
    // Read off the directory rather than listed here, so a new domain is covered the day it
    // exists rather than the day somebody remembers this file.
    //
    // These blocks do not clobber the one above, or each other: a config block naming a rule
    // replaces an earlier one only for files that match both, and every `files` here is
    // disjoint from `src/pages/**`, `src/components/**` and from every other domain.
    ...readdirSync(new URL('./src/domains', import.meta.url), { withFileTypes: true })
        .filter(entry => entry.isDirectory())
        .map(entry => entry.name)
        .map(domain => ({
            files: [`src/domains/${domain}/**/*.{ts,vue}`],
            rules: {
                'no-restricted-imports': ['error', {
                    patterns: [
                        {
                            // `.vue` is exempt for the reason it is exempt above: a component is
                            // imported where it is drawn.
                            regex: `^@/domains/(?!${domain}/)[a-z]+/(?!.*\\.vue$).+`,
                            message:
                                'Another domain is entered through its index.ts (frontend ADR-001). '
                                + 'Add what you need to that domain\'s index.ts and import it from '
                                + `there. \`@/domains/${domain}/...\` is this domain's own business and `
                                + 'stays a direct import.',
                        },
                    ],
                }],
            },
        })),
]
