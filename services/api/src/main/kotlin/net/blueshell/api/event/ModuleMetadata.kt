package net.blueshell.api.event

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

/**
 * Events and everything attached to one: sign-ups and their deadline rules, guests without an
 * account, pictures, banners and per-event feedback.
 *
 * Also owns the calendar projection of an event that `sync` pushes to Google Calendar, so the
 * shape of a calendar entry is decided by the module that owns the event.
 */
@PackageInfo
@ApplicationModule(
    id = "event",
    allowedDependencies = [
        // AbstractJsonJobHandler, which this module's job handlers extend.
        "jobs :: api",
        // The organising committee is resolved through CommitteeService.
        "committee :: api",
        // Event.committee is an owning @ManyToOne holding the FK into committees.
        // Also DEBT: the event permissions and the sign-up specifications read
        // Committee and CommitteeMember to answer whether the caller organises
        // the event.
        "committee :: entities",
        // Sign-up confirmations go out through EmailSenderService.
        "email :: api",
        // Banners and pictures are stored and resolved through FileService, and the
        // EventBannerFileLookup port file declares is implemented here.
        "file :: api",
        // EventBanner.file and EventPicture.picture are owning associations holding
        // the FKs into files.
        "file :: entities",
        // DEBT, not a surface. The event job resolvers implement JobSubject and
        // JobSubjectResolver, which sit under jobs' web package. Pinned in
        // CrossModuleWebAccessArchitectureTest.
        "jobs :: legacy-web",
        // Open kernel: EventPermission extends the base evaluator.
        "security",
        // Open kernel.
        "shared",
        // A sign-up form is built and validated through SurveyFactory, QuestionService
        // and the answer-list constraint.
        "survey :: api",
        // Event.signUpForm and EventSignUp.answers are owning associations holding
        // the FKs into the survey tables. Also DEBT: EventService and the sign-up
        // use cases assemble Question and Answer rows themselves.
        "survey :: entities",
        // DEBT, not a surface. The event controllers reuse survey's request and
        // response types and their mappers. Pinned in
        // CrossModuleWebAccessArchitectureTest.
        "survey :: legacy-web",
        // EventSignUp.user is an owning @ManyToOne holding the FK into users.
        "user :: entities",
        // DEBT, not a surface. The sign-up responses reuse user's
        // UserSummaryResponse and its mapper. Pinned in
        // CrossModuleWebAccessArchitectureTest.
        "user :: legacy-web",
    ],
)
class ModuleMetadata
