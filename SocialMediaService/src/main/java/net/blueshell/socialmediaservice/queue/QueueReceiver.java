package net.blueshell.socialmediaservice.queue;

import net.blueshell.common.Constants;
import net.blueshell.socialmediaservice.data.Map;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

@Service
public class QueueReceiver {

    @RabbitListener(queues = Constants.SM_QUEUE_NAME)
    public void receive(String in) {
        System.out.println(" [x] Received '" + in + "'");
        Map.hashMap.put(Constants.SM_QUEUE_NAME + Map.hashMap.size(), in);
    }
}
