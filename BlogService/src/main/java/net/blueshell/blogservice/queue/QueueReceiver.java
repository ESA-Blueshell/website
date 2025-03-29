package net.blueshell.blogservice.queue;

import net.blueshell.blogservice.data.Map;
import net.blueshell.common.communication.communicators.BlogCommunicator;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

@Service
public class QueueReceiver {

    @RabbitListener(queues = BlogCommunicator.name)
    public void receive(String in) {
        System.out.println(" [x] Received '" + in + "'");
        Map.hashMap.put("blog" + Map.hashMap.size(), in);
    }
}
