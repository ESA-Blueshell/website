import eslint from '@eslint/js'
import tseslint from 'typescript-eslint'
import vueParser from 'vue-eslint-parser'
import pluginVue from 'eslint-plugin-vue'
import pluginVuetify from 'eslint-plugin-vuetify'

export default tseslint.config(
    eslint.configs.recommended,
    ...tseslint.configs.recommended,
    ...pluginVue.configs['flat/recommended'],
    {
        plugins: {
            vue: pluginVue,
            vuetify: pluginVuetify
        },
        languageOptions: {
            parser: vueParser,
            parserOptions: {
                parser: tseslint.parser,
                project: './tsconfig.json',
                extraFileExtensions: ['.vue'],
            }
        },
        rules: {
            'vuetify/no-deprecated-classes': 'error',
            'vue/multi-word-component-names': 'off'
        }
    }
)