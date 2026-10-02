# syntax=docker/dockerfile:1.7

FROM alpine:3.22

WORKDIR /seed

COPY ncert ./ncert

RUN set -eux; \
    count="$(find ./ncert -type f -name '*.pdf' | wc -l)"; \
    test "$count" -gt 0; \
    find ./ncert -type f -name '*.pdf' -size +0c -print

CMD ["sh", "-c", "sleep infinity"]
