package net.blueshell.blogservice;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;
import net.blueshell.common.dto.BaseDTO;

@Data
@EqualsAndHashCode(callSuper = false)
public class BlogDTO extends BaseDTO {
    private String id;

    @NotBlank(message = "creatorId is required.")
    private String creatorId;

    private String creatorUsername;

    @NotBlank(message = "lastEditorId is required.")
    private String lastEditorId;

    private String lastEditorUsername;

    @NotBlank(message = "News type is required.")
    private String newsType;

    @NotBlank(message = "News title cannot be blank.")
    @Size(max = 200, message = "Title cannot exceed 200 characters.")
    private String title;

    @NotBlank(message = "Content cannot be blank.")
    private String content;

    private String postedAt;
}
