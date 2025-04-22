package net.blueshell.emailparser.controller;

import java.util.function.Consumer;
import jakarta.validation.Valid;
import net.blueshell.common.dto.InternalBlogDTO;
import net.blueshell.common.dto.EmailDTO;
import net.blueshell.emailparser.mapper.EmailMapper;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.logging.Logger;

@RestController
public class EmailParserController {

    private static final Logger logger = Logger.getLogger(EmailParserController.class.getName());
    private final EmailMapper emailMapper;
    private final StreamBridge streamBridge;

    @Autowired
    public EmailParserController(EmailMapper emailMapper, StreamBridge streamBridge) {
        this.emailMapper = emailMapper;
        this.streamBridge = streamBridge;
    }

    @PostMapping("/email")
    public InternalBlogDTO parseEmail(@Valid @RequestBody EmailDTO emailDTO) {
        InternalBlogDTO internalBlogDTO = emailMapper.toBlogDTO(emailDTO);
        streamBridge.send("blog-out-0", internalBlogDTO);
        return internalBlogDTO;
    }
}
