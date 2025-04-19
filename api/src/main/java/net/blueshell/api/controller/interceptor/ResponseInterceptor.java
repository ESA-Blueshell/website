package net.blueshell.api.controller.interceptor;


import net.blueshell.api.base.BaseModel;
import org.jetbrains.annotations.NotNull;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@ControllerAdvice
public class ResponseInterceptor implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(@NotNull MethodParameter returnType, @NotNull Class converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, @NotNull MethodParameter returnType, @NotNull MediaType selectedContentType, @NotNull Class selectedConverterType, @NotNull ServerHttpRequest request, @NotNull ServerHttpResponse response) {
        if (body != null) {
            // Check if the returned object is an instance of a forbidden model
            if (body instanceof BaseModel) {
                throw new IllegalStateException("Direct return of model objects is forbidden. Use a DTO instead.");
            }
            // Handle collections or arrays of models if needed
            if (body instanceof Iterable) {
                for (Object item : (Iterable<?>) body) {
                    if ((item instanceof BaseModel)) {
                        throw new IllegalStateException("Direct return of model objects inside collections is forbidden. Use a DTO instead.");
                    }
                }
            }
        }
        return body;
    }
}

