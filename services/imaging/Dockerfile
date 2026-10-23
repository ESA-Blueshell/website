# syntax=docker/dockerfile:1.27
# A static binary on a shell-less base (architecture ADR-014).

FROM golang:1.26-alpine@sha256:27f2d3b83cfc5803c8078f8e730af0f7c431bf067afaec8f60a36fb763187cba AS build
RUN apk add --no-cache build-base libwebp-dev libwebp-static
WORKDIR /src
COPY go.mod go.sum ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=1 go build -trimpath -ldflags='-s -w -linkmode external -extldflags "-static"' -o /out/imaging .

# CI reads coverage.sh's output from the coverage stage.
FROM build AS test
SHELL ["/bin/ash", "-eo", "pipefail", "-c"]
RUN apk add --no-cache libwebp-tools
RUN test -z "$(gofmt -l .)" || { gofmt -l .; exit 1; }
RUN go vet ./... && ./coverage.sh /out

FROM scratch AS coverage
COPY --from=test /out/coverage.out /out/coverage-totals.json /

FROM gcr.io/distroless/static-debian13:nonroot@sha256:e2e927ec666bae08560abb3c55d0659eceabb657f56b6782ab500a9fc7f555e3 AS runtime
COPY --from=build /out/imaging /imaging
USER 65532:65532
EXPOSE 8080
ENTRYPOINT ["/imaging"]
