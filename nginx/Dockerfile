FROM nginx:alpine

# 1) install openssl
RUN apk add --no-cache openssl

# 2) copy our tiny `service` shim script
COPY service.sh /usr/local/bin/service
RUN chmod +x /usr/local/bin/service

# 3) copy the startup script (entrypoint)
COPY entrypoint.sh /usr/local/bin/entrypoint.sh
RUN chmod +x /usr/local/bin/entrypoint.sh

# 4) mount SSL certs and expose ports
VOLUME ["/etc/nginx/ssl"]
EXPOSE 80 443

# 5) use our script as the container entrypoint
ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]