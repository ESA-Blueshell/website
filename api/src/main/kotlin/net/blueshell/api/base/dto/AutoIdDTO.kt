package net.blueshell.api.base.dto

import net.blueshell.api.base.BaseDTO

abstract class AutoIdDTO : BaseDTO(), IdentifiableDTO<Long> {
    override var id: Long? = null
}
