package net.blueshell.api.vote

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

/**
 * The annual votes, such as the OST Top 50 and Game of the Year: members rank their picks between
 * the dates the board set, and at the reveal date the ballots are counted and the result is
 * stored. The ballots are kept, so a member can look back on their own; nobody reads another's.
 * A ballot names its voter by id only.
 */
@PackageInfo
@ApplicationModule(
    id = "vote",
    allowedDependencies = [
        // Covers and thumbnails are copied into storage and served from there.
        "file :: api",
        // A game's cover and a track's thumbnail are owning references into files.
        "file :: entities",
        // The reveal runs as a job, so two api pods count a vote once.
        "jobs :: api",
        // Open kernel: ballots are @MemberOnly and the board's tools @BoardOnly.
        "security",
        // Open kernel.
        "shared",
    ],
)
class ModuleMetadata
