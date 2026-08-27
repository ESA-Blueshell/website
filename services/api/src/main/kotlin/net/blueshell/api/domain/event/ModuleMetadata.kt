package net.blueshell.api.domain.event

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
@ApplicationModule(id = "event")
class ModuleMetadata
