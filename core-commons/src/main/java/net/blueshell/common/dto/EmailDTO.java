package net.blueshell.common.dto;

import lombok.*;

import java.sql.Timestamp;

@Data
@EqualsAndHashCode(callSuper = true)
public class EmailDTO extends BaseDTO {
    private Timestamp publishedAt;
    private String html;
}