package net.blueshell.emailparser.queue;

import com.fasterxml.jackson.core.JsonProcessingException;
import net.blueshell.common.ParsedEmail;
import net.blueshell.common.communication.communicators.EmailParserCommunicator;
import net.blueshell.common.communication.communicators.serializers.JsonSerializer;
import net.blueshell.emailparser.data.Map;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import java.util.logging.Level;
import java.util.logging.Logger;

@Service
public class QueueReceiver {

    private static final Logger logger = Logger.getLogger(QueueReceiver.class.getName());

    @RabbitListener(queues = EmailParserCommunicator.name)
    public void receive(String in) {
        System.out.println(" [x] Received '" + in + "'");
        try {
            ParsedEmail parsedEmail = new JsonSerializer().deserialize(in, ParsedEmail.class);
            Map.hashMap.put("emailparser" + Map.hashMap.size(), parsedEmail.getPlainText());
        } catch (JsonProcessingException e) {
            logger.log(Level.SEVERE, e.getMessage());
        }
    }
}
