package net.blueshell.api.auth;


import lombok.Data;
import net.blueshell.api.common.enums.Role;

import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

@Data
public class Identity implements Serializable {
    Long id;
    String username;
    Set<Role> roles = new HashSet<>();

    public boolean hasRole(Role role) {
        return roles.contains(role);
    }
}
