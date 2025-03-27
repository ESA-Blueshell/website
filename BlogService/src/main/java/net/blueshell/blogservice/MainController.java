package net.blueshell.blogservice;

import net.blueshell.common.communication.AsyncCommunicationService;
import net.blueshell.common.communication.communicators.BlogCommunicator;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MainController {
    private final RabbitTemplate template;

    @Autowired
    public MainController(RabbitTemplate template) {
        System.out.println("Blog Service Started");
        this.template = template;
    }

    @GetMapping("/")
    public String SayHello() {
        String response = new AsyncCommunicationService().sendToSocialMediaService(
                new BlogCommunicator(template),
                "Test message"
        );

        return "Blog Service " + response;
    }

    @PostMapping("/")
    public String ReceivePost(@RequestBody String body) {
        return "Received: " + body;
    }
}
