package net.blueshell.api.factory.dto;

import lombok.RequiredArgsConstructor;
import net.blueshell.api.common.enums.FileType;
import net.blueshell.api.dto.FileDTO;
import org.springframework.stereotype.Component;

/**
 * Factory for FileDTO test instances.
 */
@Component
@RequiredArgsConstructor
public class FileDTOFactory extends BaseDtoFactory<FileDTO> {

    @Override
    public Class<FileDTO> targetType() {
        return FileDTO.class;
    }

    @Override
    public FileDTO createBasic() {
        FileDTO dto = new FileDTO();
        dto.setName(unique("file"));
        dto.setMediaType("image/jpeg");
        dto.setSize(1024L);
        dto.setType(FileType.EVENT_BANNER);
        return dto;
    }
}
