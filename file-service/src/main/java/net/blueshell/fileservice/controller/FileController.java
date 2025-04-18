package net.blueshell.fileservice.controller;

import net.blueshell.common.dto.FileDTO;
import net.blueshell.db.BaseController;
import net.blueshell.fileservice.mapper.FileMapper;
import net.blueshell.fileservice.service.FileService;
import net.blueshell.fileservice.model.File;
import net.blueshell.fileservice.repository.FileRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
public class FileController extends BaseController<FileService, FileRepository> {

    private final FileMapper fileMapper;

    @Autowired
    public FileController(FileService service, FileRepository repository, FileMapper fileMapper) {
        super(service, repository);
        this.fileMapper = fileMapper;
    }

    @GetMapping("/{filename:.+}")
    @ResponseBody
    public ResponseEntity<Resource> downloadFile(@PathVariable String filename) {
        File file = service.findByName(filename);
        return service.prepareFileResponse(file);
    }

    @PostMapping
    public List<FileDTO> uploadFile(List<FileDTO> dtos) {
        List<File> files = fileMapper.fromDTOs(dtos);
        return fileMapper.toDTOs(files);
    }
}