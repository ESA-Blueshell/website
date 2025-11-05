package net.blueshell.api.factory.dto;

import lombok.RequiredArgsConstructor;
import net.blueshell.api.dto.PictureDTO;
import org.springframework.stereotype.Component;

/**
 * Factory for PictureDTO test instances.
 */
@Component
@RequiredArgsConstructor
public class PictureDTOFactory extends BaseDtoFactory<PictureDTO> {

    @Override
    public Class<PictureDTO> targetType() {
        return PictureDTO.class;
    }

    @Override
    public PictureDTO createBasic() {
        PictureDTO dto = new PictureDTO();
        dto.setName(unique("picture"));
        dto.setUrl("https://cdn.example.com/" + unique("pic") + ".jpg");
        dto.setUploaderId(nextId());
        dto.setEventId(nextId());
        return dto;
    }
}
