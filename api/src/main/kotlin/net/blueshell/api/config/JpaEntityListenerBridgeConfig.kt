package net.blueshell.api.config;

import net.blueshell.api.base.JpaListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JpaEntityListenerBridgeConfig {
    @Autowired
    public JpaEntityListenerBridgeConfig(ApplicationEventPublisher eventPublisher) {
        JpaListener.setPublisher(eventPublisher);
    }
}