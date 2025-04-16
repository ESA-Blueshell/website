package net.blueshell.blogparser.mapper;
import net.blueshell.common.dto.BlogDTO;
import net.blueshell.common.dto.InternalBlogDTO;
import net.blueshell.db.BaseMapper;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public abstract class InternalBlogMapper extends BaseMapper<InternalBlogDTO, BlogDTO> {

    public abstract BlogDTO fromDTO(InternalBlogDTO dto);
}
