package net.blueshell.emailparser;

import net.blueshell.common.communication.IAsyncCommunicationService;
import net.blueshell.common.communication.ICommunicationService;
import net.blueshell.common.communication.communicators.EmailParserCommunicator;
import net.blueshell.common.dto.BlogDTO;
import net.blueshell.common.dto.EmailDTO;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.logging.Logger;

@RestController
public class EmailParserController {

    private static final Logger logger = Logger.getLogger(EmailParserController.class.getName());
    private final EmailMapper emailMapper;
    private final ICommunicationService communicationService;
    private final IAsyncCommunicationService asyncCommunicationService;

    public EmailParserController(IAsyncCommunicationService asyncCommunicationService,
                                 ICommunicationService communicationService,
                                 EmailMapper emailMapper) {
        this.asyncCommunicationService = asyncCommunicationService;
        this.communicationService = communicationService;
        this.emailMapper = emailMapper;
    }

    @GetMapping("/")
    public Boolean healthCheck() {
        return true;
    }

    @PostMapping("/parse-email")
    public BlogDTO parseEmail(EmailDTO emailDTO) {
        System.out.println("Synchronously Received '" + emailDTO.getHtml() + "'");
        BlogDTO blogDTO = emailMapper.toBlogDTO(emailDTO);
//        communicationService.sendToBlogService()
        return blogDTO;
    }

    @RabbitListener(queues = EmailParserCommunicator.name)
    public void asyncParseEmail(EmailDTO emailDTO) {
//        System.out.println("Synchronously Received '" + emailDTO + "'");
//        BlogDTO blogDTO = emailMapper.toBlogDTO(emailDTO);
//        asyncCommunicationService.sendToBlogService(blogDTO);
        BlogDTO dto = new BlogDTO();
        dto.setHtml("<html><body><h1>This is a test email</h1></body></html>");
        dto.setPublishedAt(Timestamp.from(Instant.now()));
        System.out.println("Sending blog to email parser service asynchronously");

        asyncCommunicationService.sendToBlogService(dto);
    }
}
