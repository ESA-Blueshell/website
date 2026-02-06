# API - Guide

## 📖 Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Core Design Patterns](#core-design-patterns)
3. [Project Structure](#project-structure)
4. [Base Architecture](#base-architecture)
5. [Security Implementation](#security-implementation)
6. [Database & Persistence](#database--persistence)
7. [Key Implementations](#key-implementations)
8. [Testing Strategy](#testing-strategy)
9. [API Documentation](#api-documentation)
10. [Development Guidelines](#development-guidelines)
11. [Dependency Graph](#dependency-graph)

---

## 🏗️ Architecture Overview

The backend is built using **Spring Boot 3.x** with **Java 25**, following a layered architecture with a clear separation
of concerns:

```
┌─────────────────────────────────────────────────┐
│           Presentation Layer                     │
│         (Controllers + DTOs)                     │
├─────────────────────────────────────────────────┤
│           Security Layer                         │
│      (JWT Auth + Permissions)                   │
├─────────────────────────────────────────────────┤
│           Service Layer                          │
│     (Business Logic + Validation)               │
├─────────────────────────────────────────────────┤
│           Persistence Layer                      │
│      (Repositories + JPA Entities)              │
├─────────────────────────────────────────────────┤
│           External Integration Layer             │
│  (Google Calendar, Mollie, Brevo, Social Media) │
└─────────────────────────────────────────────────┘
```

### Technology Stack

- **Framework**: Spring Boot 3.x (Spring MVC, Spring Data JPA, Spring Security)
- **Language**: Java 25
- **Database**: MariaDB 10.11.10
- **ORM**: Hibernate with Flyway migrations
- **Authentication**: JWT (JSON Web Tokens)
- **API Documentation**: SpringDoc OpenAPI 3
- **Build Tool**: Gradle 9.x

---

## 🎨 Core Design Patterns

### 1. **Generic Base Pattern**

The application uses an extensive generic base class hierarchy to reduce code duplication and ensure consistency.

#### BaseModel (Domain Entities)

All JPA entities extend from `BaseModel`:

```java

@MappedSuperclass
public abstract class BaseModel {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    private LocalDateTime deletedAt; // Soft delete support
}
```

**Benefits:**

- Automatic timestamp management
- Soft delete pattern implementation
- Consistent ID strategy across all entities

#### BaseRepository (Data Access Layer)

Custom repository interface extending JpaRepository:

```java

@NoRepositoryBean
public interface BaseRepository<T extends BaseModel>
        extends JpaRepository<T, Long>, JpaSpecificationExecutor<T> {

    // Find all non-deleted entities
    List<T> findAllByDeletedAtIsNull();

    // Find by ID excluding soft-deleted
    Optional<T> findByIdAndDeletedAtIsNull(Long id);
}
```

**Benefits:**

- Soft delete queries by default
- Specification support for complex queries
- Type-safe repository operations

#### BaseModelService (Business Logic Layer)

Generic service implementation:

```java

@Service
public abstract class BaseModelService<
        T extends BaseModel,
        D extends BaseDTO,
        R extends BaseRepository<T>,
        M extends BaseMapper<T, D>
        > {
    protected R repository;
    protected M mapper;

    public List<D> findAll() {
        return repository.findAllByDeletedAtIsNull()
                .stream()
                .map(mapper::toDTO)
                .toList();
    }

    public D findById(Long id) {
        return repository.findByIdAndDeletedAtIsNull(id)
                .map(mapper::toDTO)
                .orElseThrow(() -> new EntityNotFoundException(...));
    }

    @Transactional
    public D create(D dto) {
        T entity = mapper.toEntity(dto);
        return mapper.toDTO(repository.save(entity));
    }

    @Transactional
    public void softDelete(Long id) {
        T entity = repository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(...));
        entity.setDeletedAt(LocalDateTime.now());
        repository.save(entity);
    }
}
```

#### BaseController (REST API Layer)

Generic REST controller:

```java

@RestController
public abstract class BaseController<
        D extends BaseDTO,
        S extends BaseModelService<?, D, ?, ?>
        > {
    protected S service;

    @GetMapping
    public ResponseEntity<List<D>> getAll() {
        return ResponseEntity.ok(service.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<D> getById(@PathVariable Long id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CREATE')")
    public ResponseEntity<D> create(@Valid @RequestBody D dto) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.create(dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('DELETE')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.softDelete(id);
        return ResponseEntity.noContent().build();
    }
}
```

**Benefits:**

- CRUD operations automatically available
- Consistent API patterns
- Built-in validation and authorization
- OpenAPI documentation generated automatically

### 2. **Mapper Pattern (DTO ↔ Entity)**

Uses MapStruct-like manual mapping for object transformation:

```java
public interface BaseMapper<T extends BaseModel, D extends BaseDTO> {
    D toDTO(T entity);

    T toEntity(D dto);

    List<D> toDTOs(List<T> entities);
}
```

**Benefits:**

- Separation between internal domain model and API contracts
- Prevents over-fetching and circular reference issues
- Allows different representations for different use cases

### 3. **Repository Pattern with Specifications**

Complex queries use JPA Specifications for type-safe, composable queries:

```java
public class EventSpecifications {
    public static Specification<Event> hasStartTimeBetween(
            LocalDateTime start, LocalDateTime end
    ) {
        return (root, query, cb) -> cb.between(
                root.get("startTime"), start, end
        );
    }

    public static Specification<Event> isPublished() {
        return (root, query, cb) -> cb.equal(
                root.get("published"), true
        );
    }

    public static Specification<Event> isNotDeleted() {
        return (root, query, cb) -> cb.isNull(
                root.get("deletedAt")
        );
    }
}

// Usage
List<Event> events = eventRepository.findAll(
        EventSpecifications.hasStartTimeBetween(start, end)
                .and(EventSpecifications.isPublished())
                .and(EventSpecifications.isNotDeleted())
);
```

### 4. **Service Facade Pattern**

Complex operations use service facades to orchestrate multiple services:

```java

@Service
public class EventService extends BaseModelService<...>{

private final CalendarService calendarService;
private final EmailService emailService;
private final SocialMediaService socialMediaService;

@Transactional
public EventDTO publishEvent(Long eventId) {
    Event event = findEntityById(eventId);

    // Update event status
    event.setPublished(true);
    event = repository.save(event);

    // Sync to Google Calendar
    calendarService.sync(event);

    // Send notifications
    emailService.sendEventNotification(event);

    // Post to social media
    socialMediaService.postEvent(event);

    return mapper.toDTO(event);
}
}
```

### 5. **Strategy Pattern for External Services**

Different implementations for production and testing:

```java
public interface ContactService {
    void sendEmail(EmailContent content);
}

@Service
@Profile("!test")
public class BrevoContactService implements ContactService {
    // Real Brevo API implementation
}

@Service
@Profile("test")
public class MockContactService implements ContactService {
    // Mock implementation for testing
}
```

### 6. **Observer Pattern with JPA Listeners**

Entity lifecycle events trigger additional logic:

```java

@EntityListeners(JpaListener.class)
@Entity
public class Event extends BaseModel {
    // Entity fields
}

@Component
public class JpaListener {

    @PostPersist
    public void postPersist(Object entity) {
        // Trigger after entity is persisted
    }

    @PreUpdate
    public void preUpdate(Object entity) {
        // Trigger before entity is updated
    }
}
```

---

## 📁 Project Structure

```
api/src/main/java/net/blueshell/api/
├── auth/                          # Authentication & Authorization
│   ├── JwtTokenUtil.java          # JWT token generation/validation
│   ├── JwtAuthFilter.java         # Spring Security filter
│   └── JwtAuthenticationEntryPoint.java
├── base/                          # Generic base classes
│   ├── BaseModel.java             # Base entity class
│   ├── BaseRepository.java        # Base repository interface
│   ├── BaseModelService.java      # Generic CRUD service
│   ├── BaseController.java        # Generic REST controller
│   ├── BaseMapper.java            # DTO ↔ Entity mapper interface
│   ├── BaseDTO.java               # Base DTO class
│   ├── BasePermissionEvaluator.java
│   └── JpaListener.java           # JPA lifecycle callbacks
├── client/                        # External API clients
│   ├── BrevoClient.java           # Email marketing
│   ├── MollieClient.java          # Payment processing
│   └── SocialMediaClients.java    # Facebook, X/Twitter
├── common/                        # Common utilities
│   ├── exceptions/                # Custom exception hierarchy
│   ├── validation/                # Custom validators
│   └── constants/                 # Application constants
├── config/                        # Spring configuration
│   ├── SecurityConfig.java        # Security configuration
│   ├── JpaConfig.java             # JPA/Hibernate configuration
│   ├── AsyncConfig.java           # Async task configuration
│   ├── WebSocketConfig.java       # WebSocket configuration
│   └── JacksonConfig.java         # JSON serialization
├── controller/                    # REST API endpoints
│   ├── EventController.java
│   ├── UserController.java
│   ├── BlogController.java
│   └── ...
├── dto/                           # Data Transfer Objects
│   ├── event/
│   ├── user/
│   └── ...
├── email/                         # Email handling
│   ├── templates/                 # Email templates
│   └── EmailService.java
├── job/                           # Scheduled jobs
│   ├── ContributionReminderJob.java
│   └── EventSyncJob.java
├── listener/                      # Event listeners
│   └── EntityChangeListener.java
├── mapper/                        # DTO ↔ Entity mappers
│   ├── EventMapper.java
│   ├── UserMapper.java
│   └── ...
├── model/                         # JPA Entities (Domain Model)
│   ├── User.java
│   ├── event/
│   │   ├── Event.java
│   │   ├── EventSignup.java
│   │   └── EventCategory.java
│   ├── committee/
│   │   ├── Committee.java
│   │   └── CommitteeMember.java
│   ├── contribution/
│   │   ├── ContributionPeriod.java
│   │   └── Contribution.java
│   ├── Blog.java
│   ├── Membership.java
│   └── ...
├── permission/                    # Authorization logic
│   ├── PermissionEvaluator.java
│   └── annotations/
├── repository/                    # Data access layer
│   ├── EventRepository.java
│   ├── UserRepository.java
│   └── ...
├── service/                       # Business logic layer
│   ├── event/
│   │   ├── EventService.java
│   │   └── EventSignupService.java
│   ├── contribution/
│   ├── email/
│   │   ├── EmailService.java
│   │   └── BrevoEmailService.java
│   ├── CalendarService.java       # Google Calendar integration
│   ├── UserService.java
│   ├── MembershipService.java
│   └── ...
├── validation/                    # Custom validators
│   ├── PhoneNumberValidator.java
│   └── DateRangeValidator.java
└── ApiApplication.java            # Spring Boot main class
```

---

## 🔐 Security Implementation

### JWT Authentication Flow

```
1. User Login
   ↓
2. Credentials Validated → Generate JWT Token
   ↓
3. Return Token to Client
   ↓
4. Client Includes Token in Header: Authorization: Bearer <token>
   ↓
5. JwtAuthFilter Intercepts Request
   ↓
6. Token Validated & User Authenticated
   ↓
7. Request Proceeds with SecurityContext
```

### JWT Token Structure

```java

@Component
public class JwtTokenUtil {

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.expiration}")
    private Long expiration;

    public String generateToken(User user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getId());
        claims.put("authorities", user.getAuthorities());

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(user.getEmail())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(SignatureAlgorithm.HS512, secret)
                .compact();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser().setSigningKey(secret).parseClaimsJws(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
```

### Permission-Based Authorization

```java
// Method-level security
@PreAuthorize("hasAuthority('BOARD') or #userId == authentication.principal.id")
public UserDTO updateUser(Long userId, UserDTO dto) {
    // Implementation
}

// Custom permission evaluator
@Component
public class CustomPermissionEvaluator implements PermissionEvaluator {

    @Override
    public boolean hasPermission(
            Authentication auth, Object targetDomainObject, Object permission
    ) {
        if (auth == null || targetDomainObject == null) return false;

        User user = (User) auth.getPrincipal();

        if (permission.equals("EDIT_EVENT")) {
            Event event = (Event) targetDomainObject;
            return user.isBoard() || event.getCreator().equals(user);
        }

        return false;
    }
}
```

### CORS Configuration

```java

@Configuration
public class SecurityConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(
                Arrays.asList(frontendUrl)
        );
        configuration.setAllowedMethods(
                Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS")
        );
        configuration.setAllowedHeaders(Arrays.asList("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
```

---

## 💾 Database & Persistence

### Flyway Migration Strategy

Migrations are located in `src/main/resources/db/migration/`:

```
V1__initial_schema.sql
V2__add_events_table.sql
V3__add_committees.sql
V4__add_contributions.sql
...
```

**Naming Convention**: `V{version}__{description}.sql`

### Soft Delete Implementation

```java
// In BaseModel
private LocalDateTime deletedAt;

// In BaseRepository
@Query("SELECT e FROM #{#entityName} e WHERE e.deletedAt IS NULL")
List<T> findAllByDeletedAtIsNull();

// In BaseModelService
@Transactional
public void softDelete(Long id) {
    T entity = repository.findById(id)
            .orElseThrow(() -> new EntityNotFoundException());
    entity.setDeletedAt(LocalDateTime.now());
    repository.save(entity);
}
```

### Optimistic Locking

```java

@Entity
public class Event extends BaseModel {

    @Version
    private Long version; // Automatic optimistic locking

    // Other fields
}
```

### N+1 Query Prevention

```java
public interface EventRepository extends BaseRepository<Event> {

    @Query("SELECT e FROM Event e " +
            "LEFT JOIN FETCH e.category " +
            "LEFT JOIN FETCH e.creator " +
            "WHERE e.deletedAt IS NULL")
    List<Event> findAllWithAssociations();

    @EntityGraph(attributePaths = {"category", "creator", "signups"})
    Optional<Event> findByIdAndDeletedAtIsNull(Long id);
}
```

### Batch Operations

```java
// Configured in application.yaml
spring.jpa.properties.hibernate.default_batch_fetch_size:50

// In service
@Transactional
public void bulkUpdateEvents(List<Event> events) {
    repository.saveAll(events); // Hibernate batches this automatically
}
```

---

## 🔑 Key Implementations

### 1. Google Calendar Integration

The `CalendarService` synchronizes events with Google Calendar using the Google Calendar API with service account
authentication.

**Key Features:**

- Automatic sync on event create/update/delete
- Markdown to HTML conversion for descriptions
- Timezone handling (Europe/Amsterdam)
- Error handling and retry logic

**Usage:**

```java

@Service
public class EventService {
    private final CalendarService calendarService;

    @Transactional
    public EventDTO createEvent(EventDTO dto) {
        Event event = mapper.toEntity(dto);
        event = repository.save(event);

        // Sync to Google Calendar
        try {
            calendarService.add(event);
        } catch (IOException e) {
            log.error("Failed to sync event to calendar", e);
            // Event still created, calendar sync failed
        }

        return mapper.toDTO(event);
    }
}
```

### 2. Email Service with Brevo

Multi-provider email system with template support:

```java

@Service
public class EmailService {

    private final BrevoClient brevoClient;
    private final JavaMailSender mailSender;

    public void sendTemplateEmail(String to, Long templateId, Map<String, Object> params) {
        try {
            brevoClient.sendTransactionalEmail(to, templateId, params);
        } catch (Exception e) {
            log.error("Failed to send email via Brevo, falling back to SMTP", e);
            sendFallbackEmail(to, params);
        }
    }

    private void sendFallbackEmail(String to, Map<String, Object> params) {
        MimeMessage message = mailSender.createMimeMessage();
        // Build and send SMTP email
        mailSender.send(message);
    }
}
```

### 3. Payment Processing with Mollie

```java

@Service
public class PaymentService {

    private final MollieClient mollieClient;

    @Transactional
    public PaymentResponse createPayment(ContributionPaymentRequest request) {
        // Create Mollie payment
        Payment molliePayment = mollieClient.createPayment(
                request.getAmount(),
                "Contribution payment",
                frontendUrl + "/payment/callback"
        );

        // Store payment reference
        Contribution contribution = new Contribution();
        contribution.setMolliePaymentId(molliePayment.getId());
        contribution.setStatus(PaymentStatus.PENDING);
        contributionRepository.save(contribution);

        return new PaymentResponse(molliePayment.getCheckoutUrl());
    }

    @Transactional
    public void handlePaymentWebhook(String paymentId) {
        Payment molliePayment = mollieClient.getPayment(paymentId);

        Contribution contribution = contributionRepository
                .findByMolliePaymentId(paymentId)
                .orElseThrow();

        if (molliePayment.isPaid()) {
            contribution.setStatus(PaymentStatus.PAID);
            contribution.setPaidAt(LocalDateTime.now());
            emailService.sendPaymentConfirmation(contribution);
        }

        contributionRepository.save(contribution);
    }
}
```

### 4. Scheduled Jobs

```java

@Component
@EnableScheduling
public class ContributionReminderJob extends Job {

    private final ContributionService contributionService;
    private final EmailService emailService;

    @Scheduled(cron = "0 0 9 * * MON") // Every Monday at 9 AM
    @Async
    public void sendContributionReminders() {
        log.info("Starting contribution reminder job");

        List<Contribution> unpaidContributions =
                contributionService.findUnpaidContributions();

        for (Contribution contribution : unpaidContributions) {
            try {
                emailService.sendContributionReminder(contribution);
            } catch (Exception e) {
                log.error("Failed to send reminder for contribution {}",
                        contribution.getId(), e);
            }
        }

        log.info("Completed contribution reminder job. Sent {} reminders",
                unpaidContributions.size());
    }
}
```

### 5. File Storage Service

```java

@Service
public class FileService {

    @Value("${storage.location}")
    private String storageLocation;

    @Transactional
    public FileDTO uploadFile(MultipartFile file) throws IOException {
        String originalFilename = file.getOriginalFilename();
        String sanitizedFilename = sanitizeFilename(originalFilename);
        String uniqueFilename = UUID.randomUUID() + "_" + sanitizedFilename;

        Path filePath = Paths.get(storageLocation, uniqueFilename);
        Files.createDirectories(filePath.getParent());
        Files.copy(file.getInputStream(), filePath);

        File fileEntity = new File();
        fileEntity.setOriginalName(originalFilename);
        fileEntity.setStorageName(uniqueFilename);
        fileEntity.setContentType(file.getContentType());
        fileEntity.setSize(file.getSize());

        fileEntity = fileRepository.save(fileEntity);
        return fileMapper.toDTO(fileEntity);
    }

    public Resource loadFile(Long fileId) throws IOException {
        File fileEntity = fileRepository.findById(fileId)
                .orElseThrow(() -> new EntityNotFoundException());

        Path filePath = Paths.get(storageLocation, fileEntity.getStorageName());
        Resource resource = new UrlResource(filePath.toUri());

        if (!resource.exists() || !resource.isReadable()) {
            throw new FileNotFoundException();
        }

        return resource;
    }
}
```

---

## 🧪 Testing Strategy

### Test Structure

```
api/src/test/java/net/blueshell/api/
├── architecture/              # Architecture tests
│   └── ArchitectureTest.java  # ArchUnit tests
├── config/
│   └── TruncateTestDatabaseListener.java
├── controller/                # Integration tests
│   ├── EventControllerTest.java
│   └── UserControllerTest.java
├── service/                   # Unit tests
│   ├── EventServiceTest.java
│   ├── MockCalendarService.java
│   └── MockContactService.java
└── testsupport/              # Test utilities
    └── TestDataBuilder.java
```

### Integration Testing with Test Containers

```java

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
class EventControllerIntegrationTest {

    @Container
    static MariaDBContainer<?> mariaDB = new MariaDBContainer<>("mariadb:10.11.10")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @WithMockUser(authorities = {"BOARD"})
    void createEvent_WithValidData_ShouldReturnCreated() throws Exception {
        EventDTO dto = TestDataBuilder.buildEventDTO();

        mockMvc.perform(post("/api/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value(dto.getTitle()));
    }
}
```

### Architecture Testing with ArchUnit

```java

@AnalyzeClasses(packages = "net.blueshell.api")
public class ArchitectureTest {

    @ArchTest
    static final ArchRule servicesShould
    BeAnnotatedWithService =

    classes()
            .

    that().

    resideInAPackage("..service..")
            .

    should().

    beAnnotated(Service .class);

    @ArchTest
    static final ArchRule controllersShould
    UseServices =

    classes()
            .

    that().

    resideInAPackage("..controller..")
            .

    should().

    dependOnClassesThat().

    resideInAPackage("..service..");

    @ArchTest
    static final ArchRule repositoriesShould
    OnlyBeAccessedByServices =

    classes()
            .

    that().

    resideInAPackage("..repository..")
            .

    should().

    onlyBeAccessed().

    byAnyPackage("..service..","..config..");
}
```

---

## 📚 API Documentation

### OpenAPI/Swagger Configuration

API documentation is automatically generated using SpringDoc OpenAPI 3.

**Access Points:**

- Swagger UI: `http://localhost:8081/swagger-ui`
- OpenAPI JSON: `http://localhost:8081/v3/api-docs`

### Generating TypeScript Client

```shell script
# In project root
./generate_openapi.sh
```

This generates:

1. OpenAPI specification (`openapi/blueshell.json`)
2. TypeScript client for frontend (`frontend/src/lib/`)

### Documenting Endpoints

```java
@RestController
@RequestMapping("/api/events")
@Tag(name = "Events", description = "Event management API")
```

---

## 🔗 Dependency Graph

Generate a Graphviz visualization for internal Blueshell API class dependencies:

```shell script
./gradlew :api:classDependencyGraph
```

Artifacts:

- Dot file: `api/build/reports/class-dependencies/blueshell-api.dot`
- SVG file (generated when Graphviz `dot` is available): `api/build/reports/class-dependencies/blueshell-api.svg`
