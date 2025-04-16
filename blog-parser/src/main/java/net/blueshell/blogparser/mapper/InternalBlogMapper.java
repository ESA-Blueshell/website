package net.blueshell.blogparser.mapper;

import net.blueshell.common.dto.BlogDTO;
import net.blueshell.common.dto.InternalBlogDTO;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public abstract class InternalBlogMapper {

    public abstract BlogDTO fromInternal(InternalBlogDTO dto);

    public List<BlogDTO> fromInternals(List<InternalBlogDTO> list) {
        if (list == null) {
            return null;
        }
        return list.stream().map(this::fromInternal).toList();
    }
}
