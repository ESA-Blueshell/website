package net.blueshell.common.dto;

import lombok.*;

import java.sql.Timestamp;

@EqualsAndHashCode(callSuper = true)
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class EmailDTO extends BaseDTO {
    private Timestamp publishedAt;
    private String html;
}