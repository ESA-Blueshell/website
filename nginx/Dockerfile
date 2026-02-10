FROM nginx:alpine

# Copy nginx configurations (both HTTP-only and SSL versions)
COPY nginx.conf /etc/nginx/nginx.conf
COPY nginx-ssl.conf /etc/nginx/nginx-ssl.conf

# Copy the startup script (entrypoint)
COPY entrypoint.sh /usr/local/bin/entrypoint.sh
RUN chmod +x /usr/local/bin/entrypoint.sh

# Expose HTTP and HTTPS ports
EXPOSE 80 443

# Use our script as the container entrypoint
ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]