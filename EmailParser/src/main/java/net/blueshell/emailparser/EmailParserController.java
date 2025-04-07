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

import java.util.logging.Logger;

@RestController
public class EmailParserController {

    private static final Logger logger = Logger.getLogger(EmailParserController.class.getName());
    private final EmailParsingService parsingService;
    private final ICommunicationService communicationService;
    private final IAsyncCommunicationService asyncCommunicationService;

    public EmailParserController(IAsyncCommunicationService asyncCommunicationService,
                                 ICommunicationService communicationService,
                                 EmailParsingService parsingService) {
        this.asyncCommunicationService = asyncCommunicationService;
        this.communicationService = communicationService;
        this.parsingService = parsingService;
    }

    @GetMapping("/")
    public Boolean healthCheck() {
        return true;
    }

    @PostMapping("/parse-email")
    public BlogDTO parseEmail(EmailDTO emailDTO) {
        System.out.println("Synchronously Received '" + emailDTO.getHtml() + "'");
        BlogDTO blogDTO = parsingService.parseHTML(emailDTO.getHtml());
//        communicationService.sendToBlogService()
        return blogDTO;
    }

    @RabbitListener(queues = EmailParserCommunicator.name)
    public void asyncParseEmail(EmailDTO emailDTO) {
        System.out.println("Synchronously Received '" + emailDTO + "'");
        BlogDTO blogDTO = parsingService.parseHTML(emailDTO.getHtml());
        asyncCommunicationService.sendToBlogService(blogDTO);
    }

    @GetMapping("/queue")
    public ResponseEntity<String> addQueue() {

        StringBuilder sb = new StringBuilder().append("EmailParser ").append("\n");
//        for (java.util.Map.Entry<String, String> entry : Map.hashMap.entrySet()) {
//            sb.append(entry.getKey()).append(": ").append(entry.getValue()).append("\n");
//        }

        return new ResponseEntity<>(sb.toString(), HttpStatusCode.valueOf(200));
    }
}
