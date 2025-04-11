package net.blueshell.socialmediaservice.queue;

import net.blueshell.common.communication.communicators.Communicators;
import net.blueshell.socialmediaservice.data.Map;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

@Service
public class QueueReceiver {

    @RabbitListener(queues = Communicators.SOCIALMEDIA_NAME)
    public void receive(String in) {
        System.out.println(" [x] Received '" + in + "'");
        Map.hashMap.put("socialmedia" + Map.hashMap.size(), in);
    }
}
