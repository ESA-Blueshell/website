package net.blueshell.emailparser.controller;

import jakarta.validation.Valid;
import net.blueshell.common.communicator.BlogCommunicator;
import net.blueshell.common.dto.InternalBlogDTO;
import net.blueshell.common.dto.EmailDTO;
import net.blueshell.emailparser.mapper.EmailMapper;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.logging.Logger;

@RestController
public class EmailParserController {

    private static final Logger logger = Logger.getLogger(EmailParserController.class.getName());
    private final EmailMapper emailMapper;
    private final BlogCommunicator blogCommunicator;

    @Autowired
    public EmailParserController(EmailMapper emailMapper, BlogCommunicator blogCommunicator) {
        this.emailMapper = emailMapper;
        this.blogCommunicator = blogCommunicator;
    }

    @PostMapping("/parse")
    public InternalBlogDTO parseEmail(@Valid @RequestBody EmailDTO emailDTO) {
        System.out.println("Synchronously Received '" + emailDTO.getHtml() + "'");
        InternalBlogDTO internalBlogDTO = emailMapper.toBlogDTO(emailDTO);
        blogCommunicator.sendAsync(internalBlogDTO);
        return internalBlogDTO;
    }

    @RabbitListener(queues = "${communicators.emailParser.name}")
    public void asyncParseEmail(EmailDTO emailDTO) {
        System.out.println("Synchronously Received '" + emailDTO + "'");
        InternalBlogDTO internalBlogDTO = emailMapper.toBlogDTO(emailDTO);
        blogCommunicator.sendAsync(internalBlogDTO);
    }
}
