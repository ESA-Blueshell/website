import js from '@eslint/js'
import pluginVue from 'eslint-plugin-vue'
import * as parserVue from 'vue-eslint-parser'
import configTypeScript from '@typescript-eslint/eslint-plugin'
import parserTypeScript from '@typescript-eslint/parser'
import pluginVuetify from 'eslint-plugin-vuetify'
import globals from 'globals'

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
]
