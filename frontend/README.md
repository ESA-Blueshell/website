# Blueshell Frontend

A modern, feature-rich frontend application for managing student association activities, events, members, and
communications. Built with Vue.js 3, TypeScript, and Vuetify.

## 🏗️ Architecture

The frontend follows a **component-based architecture** with clear separation of concerns:

```
src/
├── assets/          # Static assets (images, fonts)
├── components/      # Reusable Vue components
│   ├── base/       # Base/primitive components
│   ├── form/       # Form-specific components
│   │   └── fields/ # Specialized form field components
│   └── common/     # Common shared components
├── composables/     # Vue 3 Composition API composables
├── pages/          # Page-level components (routes)
├── plugins/        # Vue plugins and configurations
├── services/       # API service layer
├── styles/         # Global styles and themes
├── types/          # TypeScript type definitions
├── utils/          # Utility functions
├── App.vue         # Root component
└── main.ts         # Application entry point
```

## 🚀 Technologies

### Core Framework

- **Vue.js 3.5.21** - Progressive JavaScript framework with Composition API
- **TypeScript 5.7.2** - Type-safe JavaScript superset
- **Vite 6.2.0** - Lightning-fast build tool and dev server

### UI Framework & Styling

- **Vuetify 3.10.2** - Material Design component framework
- **Sass 1.92.1** - CSS preprocessor
- **PostCSS 8.5.3** - CSS transformation tool
- **@mdi/font 7.4.47** - Material Design Icons
- **Roboto Fontface 0.10.0** - Roboto font family
- **Webfontloader 1.6.28** - Font loading utility
- **Flag Icons 7.5.0** - Country flag icons

### State Management & Routing

- **Vuex 4.1.0** - Centralized state management
- **Vue Router 4.5.1** - Official routing library

### HTTP & API

- **Axios 1.8.4** - Promise-based HTTP client
- **Vue Axios 3.5.2** - Vue.js integration for Axios
- **@hey-api/openapi-ts 0.83.1** - OpenAPI TypeScript client generator
- **@hey-api/client-axios 0.9.1** - OpenAPI client with Axios

### Form Handling & Validation

- **VeeValidate 4.15.1** - Form validation library with Vue 3 integration
- **@vee-validate/rules 4.15.1** - Pre-built validation rules
- **Custom validation plugin** (`plugins/validation.ts`) - Project-specific validation rules
- **Zod 4.1.12** - TypeScript-first schema validation (used selectively)
- **libphonenumber-js 1.12.17** - Phone number validation
- **v-phone-input 5.1.0** - Phone number input component
- **vue-signature-pad 3.0.2** - Signature capture component

### Date & Time

- **Luxon 3.7.2** - Modern date/time library
- **vue-datepicker-next 1.0.3** - Date picker component
- **ics 3.8.1** - iCalendar (.ics) file generator

### Content Rendering & Security

- **Marked 16.3.0** - Markdown parser and compiler
- **DOMPurify 3.2.7** - XSS sanitizer for HTML
- **XSS 1.0.15** - Additional XSS protection
- **node-emoji 2.2.0** - Emoji support

### Development Tools

- **ESLint 9.21.0** - Linting utility
- **TypeScript ESLint 8.24.1** - TypeScript ESLint integration
- **vue-eslint-parser 10.2.0** - Vue template parser for ESLint
- **eslint-plugin-vuetify 2.5.3** - Vuetify-specific linting rules
- **Vue TSC 3.0.7** - TypeScript type checker for Vue
- **@vue/tsconfig 0.8.1** - Shared TypeScript config

### Additional Libraries

- **vue-marquee-text-component 2.0.1** - Marquee text component
- **serve 14.2.5** - Static file server
- **core-js 3.45.1** - JavaScript polyfills
- **globals 15.15.0** - Global identifiers

### Build Plugins

- **@vitejs/plugin-vue 6.0.1** - Official Vue plugin for Vite
- **@originjs/vite-plugin-commonjs 1.0.3** - CommonJS support
- **vite-plugin-vuetify 2.1.2** - Vuetify optimization
- **vite-tsconfig-paths 5.1.4** - TypeScript paths support

## 📐 Design Patterns

### 1. **Composition API Pattern**

The application uses Vue 3's Composition API for better code organization and reusability:

```typescript
// Composables for shared logic
export function useFormUtils() {
    const isFormValid = ref(false);
    const validateForm = () => { /* ... */
    };
    return {isFormValid, validateForm};
}
```

### 2. **Service Layer Pattern**

API calls are abstracted into a service layer using auto-generated OpenAPI clients:

```typescript
// services/api/
// Auto-generated from OpenAPI specification
import {type AdvancedUser, createUser, updateUser} from "@/services/api"
```

### 3. **Plugin Architecture**

Custom functionality is encapsulated in Vue plugins:

- `router.ts` - Routing configuration
- `store.ts` - Vuex state management
- `vuetify.ts` - UI framework setup
- **`validation.ts` - Custom VeeValidate rules and configuration**
- `markdownToHtml.ts` - Markdown rendering
- `handleNetworkError.ts` - Global error handling

### 4. **Component Composition**

Components are organized by responsibility:

- **Base components**: Primitive, reusable UI elements
- **Form components**: Form-specific inputs and controls
- **Common components**: Shared business logic components
- **Page components**: Route-level views

### 5. **Type-Safe API Clients**

OpenAPI specification drives type-safe API communication:

- Automatic TypeScript client generation
- Type checking for requests/responses
- IDE autocomplete support

### 6. **Form Validation Pattern**

The application uses a **custom validation wrapper** combining VeeValidate with Vuetify:

**Custom Validation Plugin** (`plugins/validation.ts`):

- Registers custom validation rules with VeeValidate
- Configures global validation behavior
- Provides consistent error messages

**VvField Component** (`components/form/fields/VvField.vue`):

- Custom wrapper integrating VeeValidate with Vuetify's `v-text-field`
- Automatic error message display
- Consistent field behavior across all forms

**Backend Validation**:

- Server-side validation uses Jakarta Bean Validation (JSR 380)
- Custom validators in Java (e.g., `@ValidQuestion`, `@ValidAnswer`)
- Frontend validation provides UX, backend ensures security

### 7. **Global State Management (Vuex)**

Centralized state with modules:

- User authentication state
- Application settings
- Shared data

### 8. **Dependency Injection**

Vue's provide/inject for cross-component communication without prop drilling.

## 🎯 Best Practices

### Code Quality

#### 1. **TypeScript Strict Mode**

- Enable strict type checking
- Avoid `any` types
- Use interfaces and types for all data structures

```typescript
// Good
interface User {
    id: number;
    name: string;
    email: string;
}

// Avoid
const user: any = { /* ... */};
```

#### 2. **ESLint & Code Formatting**

- Follow ESLint rules configured in `eslint.config.mjs`
- Use consistent code formatting
- Run linting before commits

```shell script
yarn lint          # Check for issues
yarn lint --fix    # Auto-fix issues
```

#### 3. **Component Naming**

- Use PascalCase for component files: `UserProfile.vue`
- Use kebab-case in templates: `<user-profile>`
- Prefix base components: `BaseButton.vue`, `BaseInput.vue`

#### 4. **Composables Naming**

- Use `use` prefix: `useFormUtils`, `useCountries`
- Keep composables focused and single-purpose
- Return reactive references and methods

### Security

#### 1. **XSS Protection**

```typescript
import DOMPurify from 'dompurify';
import xss from 'xss';

// Sanitize user-generated HTML
const cleanHtml = DOMPurify.sanitize(userInput);
const safeHtml = xss(userInput);
```

#### 2. **Markdown Rendering**

```typescript
import {marked} from 'marked';
import DOMPurify from 'dompurify';

const html = DOMPurify.sanitize(marked.parse(markdown));
```

#### 3. **Input Validation**

- Use VeeValidate with custom validation plugin for all forms
- Validate on both client and server
- Frontend validation improves UX; backend validation ensures security

```typescript
// Custom validation rules in plugins/validation.ts
import {defineRule} from 'vee-validate';

defineRule('customRule', (value: string) => {
    // Custom validation logic
    return isValid || 'Error message';
});
```

#### 4. **API Security**

- Never commit API keys or secrets
- Use environment variables
- Implement proper CORS handling

### Performance

#### 1. **Code Splitting**

```typescript
// Lazy load route components
const UserProfile = () => import('@/pages/UserProfile.vue');
```

#### 2. **Component Lazy Loading**

```typescript
// Conditional loading for heavy components
const HeavyChart = defineAsyncComponent(() =>
    import('@/components/HeavyChart.vue')
);
```

#### 3. **Image Optimization**

- Use appropriate image formats (WebP, SVG)
- Lazy load images below the fold
- Implement responsive images

#### 4. **Bundle Size**

```shell script
yarn build          # Check bundle size in output
```

### State Management

#### 1. **Vuex Modules**

- Organize state by feature
- Use modules for large applications
- Keep mutations simple

```typescript
// store/modules/user.ts
export default {
    namespaced: true,
    state: () => ({ /* ... */}),
    getters: { /* ... */},
    mutations: { /* ... */},
    actions: { /* ... */}
};
```

#### 2. **Computed Properties**

- Use for derived state
- Cache expensive operations
- Keep logic in getters, not components

### Error Handling

#### 1. **Global Error Handler**

```typescript
// plugins/handleNetworkError.ts
export function handleNetworkError(error: AxiosError) {
    // Log, notify user, redirect, etc.
}
```

#### 2. **Component Error Boundaries**

```vue template

<script setup lang="ts">
  import {onErrorCaptured} from 'vue';

  onErrorCaptured((error) => {
    console.error('Component error:', error);
    return false; // Prevent propagation
  });
</script>
```

### Form Validation

#### 1. **Use VvField Component**

Always use the custom `VvField` wrapper for consistent validation:

```vue template

<template>
  <!-- Good: Using VvField with validation -->
  <VvField
      v-model="email"
      name="email"
      label="Email Address"
      :rules="'required|email'"
  />

  <!-- Avoid: Direct v-text-field without validation wrapper -->
  <v-text-field v-model="email" label="Email" />
</template>
```

#### 2. **Validation Rules Syntax**

Use VeeValidate's pipe-separated rule syntax:

```vue template

<VvField
    name="username"
    :rules="'required|min:3|max:20'"
/>

<VvField
    name="phoneNumber"
    :rules="'required|phone'"
/>

<VvField
    name="age"
    :rules="'required|numeric|min_value:18'"
/>
```

**Common validation rules:**

- `required` - Field must have a value
- `email` - Must be valid email format
- `min:n` - Minimum string length
- `max:n` - Maximum string length
- `numeric` - Must be a number
- `min_value:n` - Minimum numeric value
- `max_value:n` - Maximum numeric value
- `phone` - Valid phone number (using libphonenumber-js)

#### 3. **Custom Validation Rules**

Define custom rules in `plugins/validation.ts`:

```typescript
import {defineRule} from 'vee-validate';

// Custom rule example
defineRule('dutchPostalCode', (value: string) => {
    const regex = /^[1-9][0-9]{3}\s?[A-Z]{2}$/i;
    return regex.test(value) || 'Must be a valid Dutch postal code';
});
```

#### 4. **Form Submission**

Use VeeValidate's form handling:

```vue template

<script setup lang="ts">
  import {useForm} from 'vee-validate';

  const {handleSubmit, isSubmitting} = useForm();

  const onSubmit = handleSubmit(async (values) => {
    try {
      await api.submitForm(values);
      // Handle success
    } catch (error) {
      // Handle error
    }
  });
</script>

<template>
  <v-form @submit.prevent="onSubmit">
    <VvField name="email" :rules="'required|email'" />
    <v-btn type="submit" :loading="isSubmitting">
      Submit
    </v-btn>
  </v-form>
</template>
```

#### 5. **Conditional Validation**

Apply rules dynamically based on conditions:

```vue template

<script setup lang="ts">
  import {computed} from 'vue';

  const requiresAddress = ref(false);
  const addressRules = computed(() =>
      requiresAddress.value ? 'required' : ''
  );
</script>

<template>
  <VvField
      name="address"
      :rules="addressRules"
  />
</template>
```

## 🛠️ Development

### Prerequisites

- **Node.js** (LTS version recommended)
- **Yarn** (Berry/v2+) - Package manager
- **Docker** (optional, for containerized development)

### Installation

```shell script
cd frontend
yarn install
```

### Development Server

```shell script
yarn dev
```

Access at: `http://localhost:3000`

### Building for Production

```shell script
yarn build
```

Output directory: `dist/`

### Preview Production Build

```shell script
yarn preview
```

### Linting

```shell script
yarn lint              # Check for issues
yarn lint --fix        # Auto-fix issues
```

### Type Checking

```shell script
yarn type-check        # Run TypeScript compiler check
```

### OpenAPI Client Generation

```shell script
yarn gen:all           # Generate all API clients
yarn gen:blueshell     # Generate Blueshell API client
yarn gen:discord       # Generate Discord API client
```

## 🐳 Docker Development

### Using Docker Compose

```shell script
# Start development environment
docker compose -f docker-compose.dev.yml up --build -d

# View logs
docker compose -f docker-compose.dev.yml logs -f frontend

# Stop environment
docker compose -f docker-compose.dev.yml down
```

### Features:

- **Hot Module Replacement (HMR)** - Instant updates on file changes
- **Volume Mounts** - Source code synced with container
- **Port Forwarding** - Access at `http://localhost:3000`

### Dockerfile Stages

- **Dockerfile-dev**: Development with hot reload
- **Dockerfile**: Multi-stage production build with Nginx

## 📁 Project Structure Details

### `src/assets/`

Static assets like images, fonts, and icons.

### `src/components/`

Reusable Vue components organized by type:

- **base/**: Primitive components (displays, calendars, details)
- **form/**: Form-specific components and validation wrappers
    - **fields/**: Specialized form field components
- **common/**: Shared business logic components

**Naming Convention:**

```
BaseButton.vue
BaseInput.vue
VvField.vue (validation wrapper)
CountrySelect.vue
```

### `src/composables/`

Vue 3 composables for shared logic:

```typescript
// composables/formUtils.ts
export function useFormUtils() {
    // Shared form logic
}

// composables/countries.ts
export function useCountries() {
    // Country data and utilities
}
```

### `src/pages/`

Page-level components mapped to routes. Each represents a distinct view organized by feature:

- `account/` - User account management
- `activate/` - User and member activation
- `blogs/` - Newsletter viewing
- `esports/` - Esports teams and information
- `events/` - Event management and sign-ups
- `login/` - Authentication flows
- `management/` - Admin interfaces
- `membership/` - Membership application
- `partners/` - Partner information

### `src/plugins/`

Vue plugins and configurations:

- `router.ts` - Vue Router configuration
- `store.ts` - Vuex store setup
- `vuetify.ts` - Vuetify theme and configuration
- **`validation.ts` - VeeValidate configuration and custom rules**
- `goto.ts` - Navigation utilities
- `cookies.ts` - Cookie management
- `markdownToHtml.ts` - Markdown rendering
- `handleNetworkError.ts` - Error handling

### `src/services/`

API service layer with auto-generated clients:

```typescript
// services/api/blueshell/
// Auto-generated from OpenAPI spec
import {UserService, EventService} from '@/services/api/blueshell';
```

### `src/styles/`

Global styles, themes, and SCSS modules:

- `colors.scss` - Color variables
- `fonts.scss` - Font definitions
- `forms.scss` - Form styling
- `housestyle.scss` - Brand styling
- `main.scss` - Main stylesheet
- `settings.scss` - Vuetify configuration

### `src/types/`

TypeScript type definitions and interfaces:

```typescript
// types/models.ts
export interface User {
    id: number;
    name: string;
    email: string;
}
```

### `src/utils/`

Utility functions and helpers:

- Date formatting
- String manipulation
- Data transformation
- Validation helpers

## 📚 Form Components

### Form Organization

```
components/form/
├── fields/              # Specialized input fields
│   ├── VvField.vue     # Core validation wrapper
│   ├── CountrySelect.vue
│   ├── NationalitySelect.vue
│   ├── MarkdownField.vue
│   ├── UserSelect.vue
│   ├── AnswerField.vue
│   └── QuestionField.vue
├── AddressForm.vue      # Address collection
├── EventSignUpForm.vue  # Event registration
├── SimpleUserForm.vue   # Basic user info
├── AdvancedUserForm.vue # Complete user profile
├── MembershipForm.vue   # Membership application
├── SurveyForm.vue       # Survey creation
├── AnswersForm.vue      # Survey responses
├── GuestForm.vue        # Guest registration
└── CommitteeForm.vue    # Committee management
```

### VvField Component

The `VvField.vue` component is the core validation wrapper that:

- Integrates VeeValidate with Vuetify's `v-text-field`
- Automatically displays validation errors
- Supports all Vuetify text field props
- Provides consistent validation behavior across the app
- Handles loading and disabled states

**Usage Example:**

```vue template

<script setup lang="ts">
  import VvField from '@/components/form/fields/VvField.vue';
</script>

<template>
  <VvField
      v-model="email"
      name="email"
      label="Email Address"
      :rules="'required|email'"
      type="email"
  />
</template>
```

### Specialized Form Fields

```vue template
<!-- Country Selection -->
<CountrySelect
    v-model="country"
    name="country"
    :rules="'required'"
/>

<!-- Phone Input -->
<v-phone-input
    v-model="phoneNumber"
    :rules="'required|phone'"
/>

<!-- Date Picker -->
<date-picker
    v-model="birthDate"
    :rules="'required'"
/>

<!-- Markdown Editor -->
<MarkdownField
    v-model="content"
    name="content"
    :rules="'required|min:10'"
/>
```

## 🔌 Configuration Files

### `vite.config.mjs`

Vite build configuration:

- Plugins
- Aliases
- Build optimization
- Dev server settings

### `tsconfig.json`

TypeScript configuration:

- Compiler options
- Path aliases
- Type checking strictness

### `eslint.config.mjs`

ESLint rules and settings:

- Code style enforcement
- Vue-specific rules
- TypeScript integration

### `.editorconfig`

Editor configuration for consistent formatting across IDEs.

### `package.json`

Project metadata and scripts:

```json
{
  "scripts": {
    "dev": "vite --host",
    "build": "vite build",
    "preview": "vite preview",
    "lint": "eslint --fix",
    "typecheck": "vue-tsc --noEmit",
    "gen:all": "yarn gen:blueshell && yarn gen:discord",
    "gen:blueshell": "openapi-ts --file ./openapi-ts.blueshell.config.ts",
    "gen:discord": "openapi-ts --file ./openapi-ts.discord.config.ts"
  }
}
```

## 🚀 Deployment

### Production Build

```shell script
yarn build
```

This creates an optimized production build in `dist/`:

- Minified JavaScript and CSS
- Tree-shaken dependencies
- Optimized assets
- Source maps (optional)

### Docker Production Build

```shell script
docker build -f Dockerfile -t blueshell-frontend .
```

Multi-stage build process:

1. **Stage 1**: Install dependencies and build
2. **Stage 2**: Serve with Nginx

### Environment Variables

Create `.env` file for environment-specific settings:

```
VITE_API_URL=https://api.example.com
VITE_APP_TITLE=Blueshell
```

Access in code:

```typescript
const apiUrl = import.meta.env.VITE_API_URL;
```

## 📚 Key Libraries Usage

### Vuetify Components

```vue template

<template>
  <v-app>
    <v-navigation-drawer />
    <v-app-bar />
    <v-main>
      <v-container>
        <v-row>
          <v-col>
            <v-btn color="primary">Click Me</v-btn>
          </v-col>
        </v-row>
      </v-container>
    </v-main>
  </v-app>
</template>
```

### Form Validation (VeeValidate + VvField)

```vue template

<script setup lang="ts">
  import {useForm} from 'vee-validate';
  import VvField from '@/components/form/fields/VvField.vue';

  const {handleSubmit, values} = useForm();

  const onSubmit = handleSubmit(async (formValues) => {
    await submitToApi(formValues);
  });
</script>

<template>
  <v-form @submit.prevent="onSubmit">
    <VvField
        v-model="values.email"
        name="email"
        label="Email"
        :rules="'required|email'"
    />

    <VvField
        v-model="values.password"
        name="password"
        label="Password"
        type="password"
        :rules="'required|min:8'"
    />

    <v-btn type="submit">Submit</v-btn>
  </v-form>
</template>
```

### Date Handling (Luxon)

```typescript
import {DateTime} from 'luxon';

const now = DateTime.now();
const formatted = now.toFormat('yyyy-MM-dd HH:mm:ss');
const relative = now.toRelative(); // "2 hours ago"
```

### API Calls (Axios with OpenAPI)

```typescript
import {UserService} from '@/services/api/blueshell';

// Auto-generated, type-safe API calls
const users = await UserService.getUsers();
const user = await UserService.getUserById({id: 1});
```

### Markdown Rendering

```typescript
import {marked} from 'marked';
import DOMPurify from 'dompurify';

const markdown = '# Hello World\nThis is **bold**';
const html = DOMPurify.sanitize(marked.parse(markdown));
```

## 🧪 Testing (Recommended Setup)

While not currently configured, recommended testing setup:

### Unit Testing

- **Vitest** - Fast unit test framework
- **@vue/test-utils** - Vue component testing

### E2E Testing

- **Cypress** or **Playwright** - End-to-end testing

## 📖 Additional Resources

- [Vue.js Documentation](https://vuejs.org/)
- [Vuetify Documentation](https://vuetifyjs.com/)
- [TypeScript Documentation](https://www.typescriptlang.org/)
- [Vite Documentation](https://vitejs.dev/)
- [VeeValidate Documentation](https://vee-validate.logaretm.com/)

## 🤝 Contributing

1. Follow the established project structure
2. Write TypeScript with proper typing
3. Follow ESLint rules
4. Use VvField for all form inputs
5. Test your changes thoroughly
6. Update documentation as needed
7. Submit pull request with clear description

## 📝 Common Commands Reference

```shell script
# Development
yarn dev                    # Start dev server
yarn build                  # Production build
yarn preview               # Preview production build

# Code Quality
yarn lint                  # Lint code
yarn lint --fix            # Fix linting issues
yarn typecheck             # TypeScript type checking

# API Clients
yarn gen:all               # Generate all API clients
yarn gen:blueshell         # Generate Blueshell API client
yarn gen:discord           # Generate Discord API client

# Docker
docker compose -f docker-compose.dev.yml up -d    # Start dev environment
docker compose -f docker-compose.dev.yml down     # Stop dev environment
docker compose logs -f frontend                    # View logs
```

## 🐛 Troubleshooting

### Port Already in Use

```shell script
# Kill process on port 3000
lsof -ti:3000 | xargs kill -9
```

### Yarn Cache Issues

```shell script
yarn cache clean
rm -rf node_modules .yarn/cache
yarn install
```

### TypeScript Errors

```shell script
yarn typecheck
# Fix reported issues
```

### Build Errors

```shell script
# Clear Vite cache
rm -rf node_modules/.vite
yarn dev
```

---

**Note**: This frontend is part of the Blueshell website project and communicates with the Spring Boot backend API.
Ensure the API is running and properly configured for full functionality.