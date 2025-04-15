package net.blueshell.apigateway.controllers;

import net.blueshell.common.communicator.EventParserCommunicator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/event")
public class EventParserController {
    @Autowired
    private EventParserCommunicator communicator;

    @GetMapping("/queue")
    public String getEventQueue() {
        return communicator.sendSync("/queue", HttpMethod.GET, String.class);
    }
}
