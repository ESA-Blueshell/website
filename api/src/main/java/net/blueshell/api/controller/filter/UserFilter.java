package net.blueshell.api.controller.filter;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

@Data
public class UserFilter {
    private Boolean isMember;
}