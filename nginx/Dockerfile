FROM nginx:alpine

# Install certbot for SSL certificate management
RUN apk add --no-cache certbot

# Copy nginx configurations (both HTTP-only and SSL versions)
COPY nginx.conf /tmp/nginx/nginx.conf
COPY nginx-ssl.conf /tmp/nginx/nginx-ssl.conf

# Copy the startup script (entrypoint)
COPY entrypoint.sh /usr/local/bin/entrypoint.sh
RUN chmod +x /usr/local/bin/entrypoint.sh

# Expose HTTP and HTTPS ports
EXPOSE 80 443

# Use our script as the container entrypoint
ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]