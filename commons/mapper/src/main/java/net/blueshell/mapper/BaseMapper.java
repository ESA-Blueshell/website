package net.blueshell.mapper;

import net.blueshell.identity.IdentityProvider;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class BaseMapper<T, DTO> extends IdentityProvider {
    public abstract DTO toDTO(T t);

    public abstract T fromDTO(DTO dto);

    public Stream<DTO> toDTOs(Stream<T> stream) {
        if (stream == null) {
            return null;
        }
        return stream.map(this::toDTO);
    }

    public List<DTO> toDTOs(List<T> list) {
        if (list == null) {
            return null;
        }
        return list.stream().map(this::toDTO).toList();
    }

    public Stream<T> fromDTOs(Stream<DTO> dtos) {
        if (dtos == null) {
            return null;
        }
        return dtos.map(this::fromDTO);
    }

    public List<T> fromDTOs(List<DTO> dtos) {
        return dtos.stream().map(this::fromDTO).collect(Collectors.toList());
    }

    public Page<DTO> toDTOs(Page<T> list) {
        if (list == null) {
            return null;
        }
        return list.map(this::toDTO);
    }

    public Page<T> fromDTOs(Page<DTO> dtos) {
        if (dtos == null) {
            return null;
        }
        return dtos.map(this::fromDTO);
    }
}
