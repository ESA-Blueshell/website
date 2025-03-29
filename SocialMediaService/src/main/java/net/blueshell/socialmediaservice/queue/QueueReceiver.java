package net.blueshell.socialmediaservice.queue;

import net.blueshell.common.communication.communicators.SocialMediaCommunicator;
import net.blueshell.socialmediaservice.data.Map;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

@Service
public class QueueReceiver {

    @RabbitListener(queues = SocialMediaCommunicator.name)
    public void receive(String in) {
        System.out.println(" [x] Received '" + in + "'");
        Map.hashMap.put("socialmedia" + Map.hashMap.size(), in);
    }
}
