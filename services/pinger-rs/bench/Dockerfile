# Build context: services/. One image holding both senders and a veth pair to send into.
FROM rust:1-bookworm AS rust
WORKDIR /src
COPY pinger-rs/Cargo.toml pinger-rs/Cargo.lock ./
COPY pinger-rs/src src
RUN cargo build --release --locked

FROM golang:1.26 AS go
WORKDIR /src
COPY pinger/go.mod pinger/go.sum ./
RUN go mod download
COPY pinger/ .
COPY pinger-rs/bench/gobench cmd/gobench
RUN CGO_ENABLED=0 go build -o /out/gobench ./cmd/gobench

FROM debian:bookworm-slim
RUN apt-get update && apt-get install -y --no-install-recommends iproute2 && rm -rf /var/lib/apt/lists/*
COPY --from=rust /src/target/release/pinger-rs /usr/local/bin/
COPY --from=go /out/gobench /usr/local/bin/
COPY pinger-rs/bench/veth.sh /usr/local/bin/
ENTRYPOINT ["veth.sh"]
