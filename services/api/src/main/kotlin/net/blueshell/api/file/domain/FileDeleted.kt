package net.blueshell.api.file.domain

import net.blueshell.api.shared.tracking.Actor
import net.blueshell.api.shared.tracking.ActorTracked

data class FileDeleted(
    val fileId: Long,
    val path: String,
    override val actor: Actor = Actor.system()
) : ActorTracked
