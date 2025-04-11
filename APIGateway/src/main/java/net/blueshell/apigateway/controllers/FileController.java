package net.blueshell.apigateway.controllers;

import net.blueshell.common.communication.ICommunicationService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/file")
public class FileController extends SwaggerController {

    private final ICommunicationService communicationService;

    public FileController(ICommunicationService communicationService) {
        this.communicationService = communicationService;
    }
    
    @Override
    protected Object sendSwaggerRequestToService() {
        // TODO send to file service in communicator
        return null;
    }
}
