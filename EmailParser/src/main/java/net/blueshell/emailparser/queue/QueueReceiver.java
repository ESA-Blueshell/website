package net.blueshell.emailparser.queue;

import net.blueshell.common.communication.communicators.EmailParserCommunicator;
import net.blueshell.emailparser.data.Map;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

@Service
public class QueueReceiver {

    @RabbitListener(queues = EmailParserCommunicator.name)
    public void receive(String in) {
        System.out.println(" [x] Received '" + in + "'");
        Map.hashMap.put("emailparser" + Map.hashMap.size(), in);
    }
}
