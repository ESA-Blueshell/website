package net.blueshell.api.platform.config

import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.EnableAspectJAutoProxy

@Configuration
@EnableAspectJAutoProxy(exposeProxy = true)
class AopConfig 