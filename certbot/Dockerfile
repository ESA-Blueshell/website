FROM certbot/certbot:latest

# Copy entrypoint script
COPY entrypoint.sh /usr/local/bin/entrypoint.sh
RUN chmod +x /usr/local/bin/entrypoint.sh

# Use our custom entrypoint
ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]
