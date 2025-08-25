package net.blueshell.api.config;

import net.blueshell.api.model.listener.CommitteeMemberJpaListener;
import net.blueshell.api.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

@Configuration
public class JpaEntityListenerBridgeConfig {
    @Autowired
    public JpaEntityListenerBridgeConfig(ApplicationEventPublisher eventPublisher) {
        CommitteeMemberJpaListener.setPublisher(eventPublisher);
    }
}