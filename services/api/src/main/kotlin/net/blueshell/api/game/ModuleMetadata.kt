package net.blueshell.api.game

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

/**
 * The games the association plays, casually or in competition: what each is called, the address
 * it answers to, its pictures, whether it is archived and the Discord channels it lives in.
 *
 * Competition is not this module's. Whether a game is fielded is derived in `esports`, which
 * points at a game by its code; a module holding something against a game says so through
 * [net.blueshell.api.game.api.GameHoldings], so this one never has to know what a team is.
 */
@PackageInfo
@ApplicationModule(
    id = "game",
    allowedDependencies = [
        // Open kernel.
        "shared",
        // A game's pictures are stored through StoredPictures and drawn through asImage.
        "file :: api",
        // Game.banner and Game.icon hold the FK into files.
        "file :: entities",
    ],
)
class ModuleMetadata
