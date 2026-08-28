package net.blueshell.api.shared

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

/**
 * The kernel the other modules are written against: audited and soft-delete entity bases, the
 * enums persisted across module tables, the job definition and dispatch contract, dirty tracking
 * and the base controller and repository types.
 *
 * Open, because these types are extended rather than called — publishing a surface for them would
 * mean naming almost everything the package holds, and cycle detection has nothing useful to say
 * about a supertype.
 */
@PackageInfo
@ApplicationModule(
    id = "shared",
    type = ApplicationModule.Type.OPEN,
    allowedDependencies = [
        // AuditedVersionedEntity.createdBy and .updatedBy are owning @ManyToOne
        // associations holding the FKs into users, so every audited row in every
        // module carries them. It is the one dependency the kernel has on a feature
        // module, and it is the schema's own direction.
        "user :: entities",
    ],
)
class ModuleMetadata
