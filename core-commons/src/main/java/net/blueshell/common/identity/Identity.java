package net.blueshell.common.identity;


import lombok.Data;
import net.blueshell.common.enums.Role;

import java.io.Serializable;
import java.util.List;
import java.util.Set;

@Data
public abstract class Identity implements Serializable {
    Long userId;
    String username;
    Set<Role> roles;
}
