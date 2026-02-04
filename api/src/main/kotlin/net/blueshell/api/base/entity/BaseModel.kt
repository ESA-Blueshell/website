package net.blueshell.api.base.entity

@Deprecated("Use AuditedAutoIdEntity/AuditedCustomIdEntity or AutoIdEntity/CustomIdEntity to make concerns explicit.")
abstract class BaseModel : AuditedAutoIdEntity()
