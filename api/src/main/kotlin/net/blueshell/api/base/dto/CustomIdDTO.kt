package net.blueshell.api.base.dto

import net.blueshell.api.base.BaseDTO

abstract class CustomIdDTO<ID> : BaseDTO(), IdentifiableDTO<ID> {
    override var id: ID? = null
}
