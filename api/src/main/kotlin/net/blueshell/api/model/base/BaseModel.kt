package net.blueshell.api.model.base

@Deprecated("Use AuditedAutoIdEntity/AuditedCustomIdEntity or AutoIdEntity/CustomIdEntity to make concerns explicit.")
abstract class BaseModel : AuditedAutoIdEntity()
