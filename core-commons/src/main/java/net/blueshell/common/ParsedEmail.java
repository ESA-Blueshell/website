package net.blueshell.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Data
public class ParsedEmail {
    private String plainText;
    private String rawHTML;
    private List<Image> images;
}

