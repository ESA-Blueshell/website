package net.blueshell.api.shared.model

@Deprecated("Use AuditedAutoIdEntity/AuditedCustomIdEntity or AutoIdEntity/CustomIdEntity to make concerns explicit.")
abstract class BaseModel : AuditedAutoIdEntity()
