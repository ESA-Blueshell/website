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
@ApplicationModule(id = "board")
class ModuleMetadata
