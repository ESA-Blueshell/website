# Frontend - Vue.js Application

A modern, feature-rich Vue.js 3 frontend for the Blueshell student association management system. Built with TypeScript, Vuetify, and following domain-driven design principles.

## Quick Start

### Prerequisites

- **Node.js** (LTS recommended)
- **Yarn** (Berry/v2+)
- **Docker** (for containerized development)

### Development Server

```bash
cd frontend
yarn install
yarn dev
```

Access at: `http://localhost:3000`

### Building for Production

```bash
yarn build       # Creates optimized dist/ directory
yarn preview     # Preview production build locally
```

### Code Quality

```bash
yarn lint           # Check and fix linting issues
yarn typecheck      # Run TypeScript type checking
```

## Architecture Overview

The frontend follows **domain-feature architecture** with clean separation between API boundaries and domain logic.

```
src/
├── domains/          # Feature/domain modules
│   ├── auth/        # Authentication
│   ├── user/        # User management
│   ├── event/       # Event management
│   └── ...
├── components/       # Reusable Vue components
│   ├── base/        # Primitive UI components
│   ├── form/        # Form-specific components
│   └── common/      # Shared business components
├── composables/      # Vue 3 Composition API logic
├── pages/            # Route-level page components
├── services/         # API service layer (auto-generated)
├── plugins/          # Vue plugins & configuration
├── styles/           # Global styles & themes
├── types/            # TypeScript definitions
└── utils/            # Utility functions
```

**For comprehensive architecture guidance, see:**
- **[CLAUDE.md](../CLAUDE.md)** - Complete developer guide
- **[docs/adr/frontend/ADR-INDEX.md](../docs/adr/frontend/ADR-INDEX.md)** - All architecture decisions
- **Key ADRs**:
  - [ADR-001: Domain Feature Architecture](../docs/adr/frontend/ADR-001-domain-feature-architecture.md)
  - [ADR-002: API Client Boundary and Domain Mapping](../docs/adr/frontend/ADR-002-api-client-boundary-and-domain-mapping.md)
  - [ADR-003: State Management and Server Data Ownership](../docs/adr/frontend/ADR-003-state-management-and-server-data.md)
  - [ADR-004: Form Validation and Command Mapping](../docs/adr/frontend/ADR-004-form-validation-and-command-mapping.md)

## Technology Stack

### Core Framework
- **Vue.js 3.5.28** - Progressive JavaScript framework
- **TypeScript 5.9.3** - Type-safe JavaScript
- **Vite 7.3.1** - Lightning-fast build tool

### UI & Styling
- **Vuetify** - Material Design components, everywhere except the island
- **Sass** - CSS preprocessor
- **@mdi/font** - Material Design Icons
- **Flag Icons** - Country flags
- **Tailwind CSS** - the island only; see below
- **Reka UI** - headless component behaviour for the island
- **Motion for Vue** - the island's animation

## The island

The pages on the island are styled with Tailwind rather than Vuetify. They are the
association's shopfront rather than a screen somebody works in: full-bleed, dark
whatever the viewer's theme says, and animated throughout. The rest of the app is
Vuetify and stays that way.

The island itself is `src/components/island`: its root, its stylesheet, the
timeline and the strip arithmetic under it, the banner slices, the dialog shell and
the pickers. What stands on it is a page's own — `src/domains/esports/island` plus
the pages under `src/pages/esports` and `src/pages/Esports.vue`. Its root component
applies the `.island` class, and everything Tailwind-styled sits inside that
element.

Three things keep it from leaking, all enforced rather than agreed:

- **Preflight is never imported.** `src/styles/island.css` pulls Tailwind's theme
  and utilities but not its reset, because that reset restyles bare elements
  globally and would land on every Vuetify page at once. The island resets its own
  subtree instead.
- **Utilities live in a cascade layer.** Vuetify's stylesheet is unlayered, and
  unlayered rules beat layered ones regardless of order, so a Tailwind class
  cannot win against Vuetify outside the island. The island's own reset is in an
  earlier layer so utilities still beat *it*.
- **Sources are listed explicitly.** `island.css` names the directories Tailwind
  scans. A Tailwind class written anywhere else generates no CSS and fails
  visibly rather than quietly working.

### Motion

`useMotionAllowed` is the policy. The island reduces motion rather than removing
it: parallax, drift, tilt and counters are decorative and switch off for a visitor
who asks for reduced motion, while a crossfade or a sliding indicator stays,
because it explains what changed. Use `motion-safe:` for a Tailwind hover rather
than overriding with `motion-reduce:` — the scale utilities set the `scale`
property, which `transform-none` does not neutralise.

Every Playwright project emulates reduced motion except one named `motion`, which
matches `*.motion.spec.ts`. Assertions about the choreography go there; everything
else stays deterministic.

### State & Routing
- **Vuex 4.1.0** - Centralized state management
- **Vue Router 5.0.3** - Official routing library

### HTTP & API
- **Axios 1.13.5** - HTTP client
- **@hey-api/openapi-ts 0.92.4** - OpenAPI code generator
- **@hey-api/client-axios 0.9.1** - OpenAPI Axios client

### Form Handling & Validation
- **VeeValidate 4.15.1** - Form validation framework
- **Zod 4.3.6** - Schema validation
- **libphonenumber-js 1.12.37** - Phone validation
- **v-phone-input 6.0.1** - Phone input component

### Utilities
- **Luxon 3.7.2** - Date/time handling
- **Marked 17.0.3** - Markdown rendering
- **DOMPurify 3.3.1** - XSS protection

## Project Structure Details

### Components (`src/components/`)

Organized by responsibility:

```
components/
├── base/              # Primitive UI elements
│   ├── BaseButton.vue
│   ├── BaseCalendar.vue
│   └── ...
├── form/              # Form-specific components
│   ├── fields/        # Input field wrappers
│   │   ├── VvField.vue           # Validation wrapper
│   │   ├── CountrySelect.vue
│   │   └── ...
│   ├── AddressForm.vue
│   ├── EventSignUpForm.vue
│   └── ...
└── common/            # Business logic components
    ├── EventCard.vue
    ├── UserProfile.vue
    └── ...
```

**Naming Convention:**
- Components: PascalCase (`UserProfile.vue`)
- Base components: Prefix `Base` (`BaseButton.vue`)
- Validation wrapper: `VvField.vue`
- Template usage: kebab-case (`<user-profile>`)

### Composables (`src/composables/`)

Vue 3 Composition API for shared logic:

```typescript
// composables/useFormUtils.ts
export function useFormUtils() {
    const isFormValid = ref(false);
    const validateForm = () => { /* ... */ };
    return { isFormValid, validateForm };
}

// Usage in component
const { isFormValid, validateForm } = useFormUtils();
```

### Pages (`src/pages/`)

Route-level components organized by feature:

- `auth/` - Login, password reset, activation
- `account/` - User profile management
- `membership/` - Membership application
- `event/` - Event management and signup
- `management/` - Admin interfaces
- `...` - Other features

### Plugins (`src/plugins/`)

Vue plugin configuration:

- `router.ts` - Vue Router setup
- `store.ts` - Vuex store
- `vuetify.ts` - Vuetify theme & configuration
- `validation.ts` - VeeValidate rules & setup
- `markdownToHtml.ts` - Markdown rendering plugin
- `handleNetworkError.ts` - Global error handling
- `cookies.ts` - Cookie management

### Services (`src/services/`)

Auto-generated API client:

```typescript
// services/api/blueshell/ (auto-generated from OpenAPI)
import { UserService, EventService } from '@/services/api/blueshell';

// Type-safe API calls
const users = await UserService.getUsers();
const user = await UserService.getUserById({ id: 123 });
```

## Form Validation

### VvField Component

The custom `VvField` wrapper integrates VeeValidate with Vuetify for consistent validation:

```vue
<template>
  <VvField
      v-model="email"
      name="email"
      label="Email Address"
      :rules="'required|email'"
      type="email"
  />

  <VvField
      v-model="username"
      name="username"
      label="Username"
      :rules="'required|min:3|max:20'"
  />
</template>
```

### Validation Rules

Use pipe-separated rule syntax:

```typescript
:rules="'required|email'"           // Email required
:rules="'required|min:3|max:20'"   // Length constraints
:rules="'required|phone'"           // Phone validation
:rules="'required|numeric'"         // Numeric only
```

For custom validation rules, see `plugins/validation.ts`.

### Backend Validation

Frontend validation improves UX; backend validation ensures security. Server validates all requests with:
- Jakarta Bean Validation (JSR 380)
- Custom validators (`@ValidQuestion`, `@ValidAnswer`)
- Business rule validation

See [ADR-004: Form Validation and Command Mapping](../docs/adr/frontend/ADR-004-form-validation-and-command-mapping.md).

## OpenAPI Client Generation

Auto-generate TypeScript client from backend API specification:

```bash
yarn gen:all           # Generate all clients
yarn gen:blueshell     # Generate Blueshell API client
yarn gen:discord       # Generate Discord API client
```

Generated code is placed in `src/services/api/` and provides:
- Type-safe API calls
- IDE autocomplete support
- Request/response validation

## Security

### XSS Protection

```typescript
import DOMPurify from 'dompurify';
import { marked } from 'marked';

// Sanitize user-generated HTML
const cleanHtml = DOMPurify.sanitize(userInput);

// Render markdown safely
const html = DOMPurify.sanitize(marked.parse(markdownContent));
```

### Input Validation

Always use `VvField` component for validated inputs:

```vue
<!-- ✅ Good: Using VvField with validation -->
<VvField
    v-model="email"
    name="email"
    :rules="'required|email'"
/>

<!-- ❌ Avoid: Direct input without validation -->
<v-text-field v-model="email" />
```

### API Security

- Never commit API keys or secrets
- Use environment variables (`.env`)
- Implement proper CORS handling (configured in backend)
- Validate all server responses

## Development Commands

### Installation & Setup

```bash
cd frontend
yarn install
```

### Development Server

```bash
yarn dev                # Start dev server with HMR
yarn dev --host        # Listen on all interfaces
```

### Build & Preview

```bash
yarn build             # Production build
yarn preview           # Preview production build
```

### Code Quality

```bash
yarn lint              # Check and fix all issues
yarn lint --max-warnings 0  # Fail on any warning
yarn typecheck         # TypeScript type checking
```

### API Client Generation

```bash
yarn gen:blueshell     # From Blueshell API
yarn gen:discord       # From Discord API
yarn gen:all           # Both clients
```

## Docker Development

### Using Docker Compose

```bash
docker compose -f docker-compose.dev.yml up frontend
```

Features:
- **Hot Module Replacement (HMR)** - Instant updates
- **Volume Mounts** - Source code synced with container
- **Port Forwarding** - Access at `http://localhost:3000`

### Dockerfile

- **Dockerfile-dev**: Development with hot reload
- **Dockerfile**: Multi-stage production build with Nginx

## Environment Configuration

Create `.env` file for environment-specific settings:

```
VITE_API_URL=https://localhost/api
VITE_APP_TITLE=Blueshell
```

Access in code:

```typescript
const apiUrl = import.meta.env.VITE_API_URL;
```

## Policies & Compliance

User-facing policies are documented in `docs/policies/`:
- Cookie Policy
- Privacy Policy

Refer to these in signup flows, consent workflows, and user-facing documentation.

## Contributing

1. Follow the architecture in CLAUDE.md
2. Reference ADRs when making design decisions
3. Use TypeScript strict mode (no `any`)
4. Always use `VvField` for form inputs
5. Generate OpenAPI clients when backend changes
6. Run `yarn lint` before committing

---

**Note**: The frontend is part of the Blueshell website project. See the root [README.md](../README.md) for full project setup.
