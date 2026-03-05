# Blueshell Website

A full-stack web application for managing student association activities, events, members, and communications. Built
with Spring Boot backend and Vue.js frontend, containerized with Docker.

## 🏗️ Architecture

y aFull-stack web application following **Domain-Driven Design (DDD)** with clean architecture principles:

- **Backend**: Spring Boot 4.0.3 REST API (Kotlin)
- **Frontend**: Vue.js 3 Single Page Application (TypeScript)
- **Database**: MariaDB 10.11.10
- **Job Dispatch**: @Async thread pool + RetryTemplate
- **Infrastructure**: Docker, Nginx, Certbot (production SSL)

**For detailed architecture patterns, layer structure, and design decisions, see:**
- **[CLAUDE.md](CLAUDE.md)** - Developer guide with quick start commands and architecture overview
- **[docs/adr/ADR-INDEX.md](docs/adr/ADR-INDEX.md)** - All architecture decision records

## 🚀 Technologies Used

### Backend (API)

- **Language**: Kotlin 2.3.10 with Java 24 toolchain
- **Framework**: Spring Boot 4.0.3 (Web, Security, Data JPA)
- **Database**: MariaDB 10.11.10 with Flyway migrations
- **Security**: Spring Security with JWT authentication
- **API Docs**: SpringDoc OpenAPI 3 (Swagger UI)
- **Testing**: JUnit 5, Testcontainers, ArchUnit, Playwright

### Frontend

- **Framework**: Vue.js 3.5.28 with TypeScript 5.9.3
- **UI**: Vuetify 3.12.0 (Material Design)
- **Build**: Vite 7.3.1
- **State**: Vuex 4.1.0
- **Routing**: Vue Router 5.0.3
- **HTTP**: Axios 1.13.5 with OpenAPI-generated client
- **Validation**: VeeValidate 4.15.1

### Infrastructure

- **Containerization**: Docker & Docker Compose
- **Web Server**: Nginx with SSL/TLS (Let's Encrypt via Certbot)
- **Database**: MariaDB 10.11.10 (utf8mb4, Europe/Amsterdam timezone)
## 📋 Prerequisites

### Required

- **Docker** (v20.10 or higher)
- **Docker Compose** (v2.0 or higher)

### Optional (for local development)

- **Java 24** (for running API without Docker)
- **Node.js** (for running frontend without Docker)
- **Yarn** (Berry/v2+)

## 🔧 Configuration

### Environment Files

Create the following environment files in the `env/` directory based on the examples:

#### 1. Database Configuration (`env/.db.env`)

```shell script
# Copy from example
cp env/.db.env.example env/.db.env
```

Required variables:

- `MYSQL_HOST` - Database host (default: `db`)
- `MYSQL_PORT` - Database port (default: `3306`)
- `MYSQL_DATABASE` - Database name
- `MYSQL_USER` - Database user
- `MYSQL_PASSWORD` - Database password
- `MYSQL_ROOT_PASSWORD` - Root password

#### 2. Application Configuration (`env/.app.env`)

```shell script
# Copy from example
cp env/.app.env.example env/.app.env
```

Required variables:

- `APP_URL` - Backend API URL
- `FRONTEND_URL` - Frontend URL
- `JWT_SECRET` - Secret key for JWT token generation
- `STORAGE_LOCATION` - File storage path

**Email Configuration:**

- `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`
- `SMTP_USE_SSL`, `SMTP_USE_TLS`

**Brevo (Email Marketing):**

- `BREVO_API_KEY`
- `BREVO_TEMPLATE_*` - Email template IDs
- `BREVO_FOLDER_*` - Folder IDs

**Google Calendar:**

- `GOOGLE_CALENDAR_ID`
- `GOOGLE_CALENDAR_CLIENT_ID`
- `GOOGLE_CALENDAR_CLIENT_EMAIL`
- `GOOGLE_CALENDAR_PRIVATE_KEY_PKCS8`
- `GOOGLE_CALENDAR_PRIVATE_KEY_ID`

**Payment Provider (Mollie):**

- `MOLLIE_API_KEY`

**Social Media:**

- `FACEBOOK_PAGE_ID`, `FACEBOOK_ACCESS_TOKEN`
- `X_API_KEY`, `X_API_SECRET`, `X_ACCESS_TOKEN`, `X_ACCESS_SECRET`

### Database Initialization

Database initialization scripts are located in `env/`:

- `0_init.sql` - Initial database setup
- `1_dump.sql` - Database seed data (optional)

## 🐳 Docker Setup

### Production Deployment

1. **Configure environment files** (see Configuration section above)

2. **Start the application**:

```shell script
./run.sh
```

Or manually:

```shell script
docker compose -f docker-compose.yml up --build -d
```

3. **Access the application**:
    - Frontend: `https://esa-blueshell.nl`
    - API: `https://esa-blueshell.nl/api`
    - Swagger UI: `https://esa-blueshell.nl/api/swagger-ui`

#### Production Services

- **db**: MariaDB database
- **api**: Spring Boot backend (port 8080 internal)
- **frontend**: Vue.js frontend (port 3000 internal)
- **nginx**: Reverse proxy (ports 80, 443)
- **certbot**: SSL certificate initialization
- **certbot-renew**: Automatic certificate renewal (runs monthly)

### Development Environment

1. **Start development containers**:

```shell script
./run-dev.sh
```

Or manually:

```shell script
docker compose -f docker-compose.dev.yml up --build -d
```

2. **Access the application**:
    - Frontend: `https://localhost`
    - API: `https://localhost/api`
    - Database: `localhost:3307`
    - Swagger UI: `https://localhost/api/swagger-ui`

#### Development Features

- **Hot Reload**: Both frontend and backend support hot reloading
- **Volume Mounts**: Source code is mounted for live changes
- **Debug Ports**: Available for remote debugging
- **OpenAPI Generation**: Automatic API client generation

### OpenAPI Client Generation

The application uses OpenAPI for API documentation and TypeScript client generation:

```shell script
./generate_openapi.sh
```

This script:

1. Generates OpenAPI spec from Spring Boot backend (`openapi/blueshell.json`)
2. Generates TypeScript client for frontend (`frontend/src/lib/`)
3. Updates Discord and other external API clients

## 📦 Docker Compose Services

### Development (`docker-compose.dev.yml`)

| Service  | Container    | Ports     | Description       |
|----------|--------------|-----------|-------------------|
| db       | db           | 3307:3306 | MariaDB database  |
| api      | api-dev      | 8081:8080 | Spring Boot API   |
| frontend | frontend-dev | 3000:3000 | Vue.js dev server |
| nginx    | nginx        | 80, 443   | Reverse proxy     |

### Production (`docker-compose.yml`)

| Service       | Container     | Ports   | Description                 |
|---------------|---------------|---------|-----------------------------|
| db            | db            | -       | MariaDB database (internal) |
| api           | api           | -       | Spring Boot API (internal)  |
| frontend      | frontend      | -       | Vue.js production build     |
| nginx         | nginx         | 80, 443 | Reverse proxy with SSL      |
| certbot       | certbot       | -       | SSL certificate setup       |
| certbot-renew | certbot-renew | -       | SSL certificate renewal     |

## 🛠️ Development

### Running Without Docker

#### Backend (API)

```shell script
./gradlew :api:bootRun
```

#### Frontend

```shell script
cd frontend
yarn install
yarn dev
```

### Debugging

Remote debugging is available in development mode:

1. Configure IntelliJ Remote JVM Debug
2. Set host: `localhost`
3. Set port: `5005` (API)
4. Attach debugger to running container

### Database Management

Connect to the database:

```shell script
docker compose exec db mysql -u <username> -p<password> blueshell
```

### Full Test + Coverage (Docker, API-driven system tests)

Run all API tests, API-owned frontend system tests, and generate merged coverage:

```shell script
./scripts/test-all-compose-coverage.sh
```

What this does:
1. Starts `db` and an instrumented frontend (`VITE_COVERAGE=true`) with Docker Compose.
2. Runs API non-system tests and API system tests (`@Tag("system")`) from Gradle.
   The script forces `-Dsystem.frontend.url=http://frontend:3000` so API-driven system tests target the compose frontend service.
3. Captures frontend coverage from Playwright browser sessions executed by API system tests.
4. Converts frontend raw coverage to LCOV.
5. Merges frontend LCOV + backend JaCoCo into a combined report (local Node/Bash merge, no Docker image for merge).

Coverage outputs:
- API JaCoCo test XML: `api/build/reports/jacoco/jacocoTestReport/jacocoTestReport.xml`
- API JaCoCo system XML: `api/build/reports/jacoco/jacocoSystemTestReport/jacocoSystemTestReport.xml`
- Backend coverage scope in merged outputs: `api/src/main/kotlin/net/blueshell/api/**`
- Frontend LCOV from API system tests: `api/build/coverage/frontend-system/lcov.info`
- Merged HTML + Cobertura: `coverage/merged/`
- Merged Cobertura XML: `coverage/merged/Cobertura.xml`
- Merged LCOV: `coverage/merged/lcov.info`
- IntelliJ: import JaCoCo XML suites for backend coverage and open `coverage/merged/index.html` for combined cross-stack coverage.

Troubleshooting:
- If system tests fail with missing frontend coverage, ensure frontend is running with `VITE_COVERAGE=true`.
- If frontend is not reachable, verify `system.frontend.url` resolves from the API test runtime.
- If Playwright fails to launch browsers in Docker, rebuild API dev image to reinstall browser dependencies.
- Do not run API system tests with `docker compose run --service-ports`; it can cause port collisions with this workflow.

## 📁 Project Structure

```
website/
├── build.gradle.kts       # Gradle configuration
├── settings.gradle.kts    # Gradle settings
├── api/                    # Spring Boot backend
│   ├── src/               # Java source code
│   ├── Dockerfile         # Production build
│   ├── Dockerfile-dev     # Development build
├── frontend/              # Vue.js frontend
│   ├── src/              # TypeScript/Vue source
│   ├── public/           # Static assets
│   ├── Dockerfile        # Production build
│   ├── Dockerfile-dev    # Development build
│   └── package.json      # NPM dependencies
├── nginx/                # Nginx configuration
│   ├── nginx.conf        # Reverse proxy config
│   └── Dockerfile        # Nginx container
├── env/                  # Environment configuration
│   ├── .app.env.example  # Application env template
│   ├── .db.env.example   # Database env template
│   ├── 0_init.sql        # Database initialization
│   └── 1_dump.sql        # Database seed data
├── openapi/              # OpenAPI specifications
│   ├── blueshell.json    # Generated API spec
│   └── discord.json      # Discord API spec
├── docker-compose.yml    # Production compose
├── docker-compose.dev.yml # Development compose
└── generate_openapi.sh   # API client generator
```

## 🔒 Security Features

- JWT-based authentication
- Spring Security integration
- XSS protection (frontend)
- SQL injection prevention (JPA/Hibernate)
- CORS configuration
- SSL/TLS encryption (production)
- Input validation and sanitization

## 📝 API Documentation

Interactive API documentation is available via Swagger UI:

- Development: `https://localhost/api/swagger-ui`
- Production: `https://esa-blueshell.nl/api/swagger-ui`

OpenAPI specification: `/api/v3/api-docs`

## 🗄️ Database

- **Engine**: MariaDB 10.11.10
- **Charset**: UTF-8 (utf8mb4)
- **Collation**: utf8mb4_unicode_ci
- **Timezone**: Europe/Amsterdam
- **Migrations**: Managed by Flyway

## 🚀 Deployment

### SSL Certificate Setup

The production deployment automatically handles Let's Encrypt SSL certificates:

1. Initial certificate generation (via `certbot` service)
2. Automatic renewal every 30 days (via `certbot-renew` service)
3. Certificates stored in `letsencrypt` volume

Update the domain in `docker-compose.yml`:

```yaml
-d esa-blueshell.nl
```

### Health Checks

All services include health checks:

- Database: `mysqladmin ping`
- API: `GET /health`
- Frontend: HTTP request to root
- Nginx: Service status check

## 📄 License

This project is developed for ESA Blueshell.

## 🤝 Contributing

1. Create feature branch
2. Make changes with hot reload
3. Run tests
4. Generate OpenAPI clients if API changed
5. Submit pull request

## 📞 Support

For issues or questions, please contact the board of the association board@blueshell.utwente.nl

---

**Note**: Make sure to properly configure all environment variables before deployment, especially security-sensitive
values like JWT secrets, API keys, and database passwords.
