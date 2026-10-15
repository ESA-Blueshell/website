# syntax=docker/dockerfile:1.27
#
# Build context: services/imaging. One static binary on a base with no shell, no package manager
# and no libc: libwebp is linked in, and the pictures are decoded by Go (architecture ADR-014).

FROM golang:1.26-alpine@sha256:3082400e369fa24d5fc60bca20edab3f6d604e0c5a690ec66b295eff4dd87ade AS build
RUN apk add --no-cache build-base libwebp-dev libwebp-static
WORKDIR /src
COPY go.mod go.sum ./
RUN go mod download
COPY . .
RUN CGO_ENABLED=1 go build -trimpath -ldflags='-s -w -linkmode external -extldflags "-static"' -o /out/imaging .

# The unit suite, against the libwebp the image links and the cwebp the api used to run, held to
# every statement. CI builds this stage alone and reads coverage.out out of the next one.
FROM build AS test
SHELL ["/bin/ash", "-eo", "pipefail", "-c"]
RUN apk add --no-cache libwebp-tools
RUN test -z "$(gofmt -l .)" || { gofmt -l .; exit 1; }
RUN go vet ./...
RUN go test -count=1 -coverpkg=./internal/... -coverprofile=/out/coverage.out ./internal/... \
 && go tool cover -func=/out/coverage.out \
  | awk '/^total:/ { print; if ($3 != "100.0%") { print "unit coverage must be 100%"; exit 1 } }'

FROM scratch AS coverage
COPY --from=test /out/coverage.out /

FROM gcr.io/distroless/static-debian13:nonroot@sha256:e2e927ec666bae08560abb3c55d0659eceabb657f56b6782ab500a9fc7f555e3 AS runtime
COPY --from=build /out/imaging /imaging
USER 65532:65532
EXPOSE 8080
ENTRYPOINT ["/imaging"]
