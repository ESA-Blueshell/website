package net.blueshell.api.common.enums;

import io.swagger.v3.oas.annotations.media.Schema;

// Define a PermissionAction enum
@Schema(enumAsRef = true)
public enum PermissionAction {
    CREATE, SEE, EDIT, DELETE
}
