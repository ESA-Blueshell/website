FROM docker:28-cli AS docker-cli

FROM python:3.12-slim

# Copy only the docker CLI binary — we need it to exec into containers and
# manage services via the mounted Docker socket.
COPY --from=docker-cli /usr/local/bin/docker /usr/local/bin/docker

WORKDIR /app

COPY pyproject.toml .
RUN pip install --no-cache-dir .

COPY rotator/ ./rotator/
COPY schedule.yml .

ENTRYPOINT ["python", "-m", "rotator"]
CMD ["schedule"]
