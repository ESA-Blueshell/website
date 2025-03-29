package net.blueshell.eventparser.queue;

import net.blueshell.common.communication.communicators.EventParserCommunicator;
import net.blueshell.eventparser.data.Map;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

@Service
public class QueueReceiver {

    @RabbitListener(queues = EventParserCommunicator.name)
    public void receive(String in) {
        System.out.println(" [x] Received '" + in + "'");
        Map.hashMap.put("eventparser" + Map.hashMap.size(), in);
    }
}
