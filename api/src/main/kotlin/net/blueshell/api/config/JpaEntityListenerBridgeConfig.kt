package net.blueshell.api.config

import net.blueshell.api.listener.base.JpaListener
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Configuration

@Configuration
class JpaEntityListenerBridgeConfig @Autowired constructor(eventPublisher: ApplicationEventPublisher) {
    init {
        JpaListener.setPublisher(eventPublisher)
    }
}