# syntax=docker/dockerfile:1.27
#
# Build context: services/pinger. The page is server-rendered Go with a hand-written
# stylesheet and fonts embedded through go:embed, so there is no Node or CSS build step.

FROM golang:1.26-alpine AS build
WORKDIR /src
COPY go.mod go.sum ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o /out/pinger ./cmd/pinger

# Root, because a container gains NET_RAW only through its bounding set and Kubernetes cannot
# grant ambient capabilities to another user. The Deployment drops every other capability.
FROM gcr.io/distroless/static-debian12
COPY --from=build /out/pinger /pinger
EXPOSE 8090
ENTRYPOINT ["/pinger"]
