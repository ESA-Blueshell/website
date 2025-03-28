package net.blueshell.emailparser.queue;

import net.blueshell.emailparser.data.Map;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

@Service
public class QueueReceiver {

    @RabbitListener(queues = "emailparser")
    public void receive(String in) {
        System.out.println(" [x] Received '" + in + "'");
        Map.hashMap.put("emailparser" + Map.hashMap.size(), in);
    }
}
