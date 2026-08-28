package net.blueshell.api.domain.event.application.calendar

import org.springframework.modulith.NamedInterface
import org.springframework.modulith.PackageInfo

/**
 * The driven side of calendar publication — one `CalendarAdapter` per target calendar,
 * with the event shape it is handed. Implemented outside this module.
 */
@PackageInfo
@NamedInterface("api")
class PackageMetadata
