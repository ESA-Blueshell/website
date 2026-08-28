package net.blueshell.api.domain.board

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

/**
 * The association's boards by year, the seats on them and the documents a board publishes.
 *
 * A seat's `user` is nullable on purpose — most people who have sat on a board never had an
 * account here, so an unlinked seat still records the board that sat.
 */
@PackageInfo
@ApplicationModule(
    id = "board",
    allowedDependencies = [
        // Board pictures and documents are stored and resolved through FileService.
        "file :: api",
        // Board.picture, BoardMember.picture and BoardDocument.file are owning
        // associations holding the FKs into files.
        "file :: entities",
        // Open kernel: BoardPermission extends the base evaluator.
        "security",
        // Open kernel.
        "shared",
        // Members are resolved through UserService.
        "user :: api",
        // BoardMember.user is an owning @ManyToOne holding the FK into users.
        "user :: entities",
    ],
)
class ModuleMetadata
