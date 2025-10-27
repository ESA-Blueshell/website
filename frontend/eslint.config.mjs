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
            'src/assets/**',
            'src/services/api/**',
        ]
    },
    js.configs.recommended,
    ...pluginVue.configs['flat/recommended'],
    {
        plugins: {
            '@typescript-eslint': configTypeScript,
            'vuetify': pluginVuetify,
        },

        languageOptions: {
            parser: parserVue,
            parserOptions: {
                parser: parserTypeScript,
                sourceType: 'module',
            },
            globals: {
                ...globals.browser,
                // Vue 3 globals
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
            '@typescript-eslint/no-unused-vars': 'warn',
            '@typescript-eslint/no-explicit-any': 'warn',
        },
    },
]