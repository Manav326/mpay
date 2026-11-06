#!/usr/bin/env python3
"""Derive every mPay public origin from one canonical HTTPS root URL."""
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from urllib.parse import urlsplit

LABEL = re.compile(r"^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$")


class ConfigError(ValueError):
    """Invalid or ambiguous public URL configuration."""


def normalize_root(value: str) -> tuple[str, str]:
    try:
        parsed = urlsplit(value.strip())
        hostname = parsed.hostname
        port = parsed.port
    except ValueError as exc:
        raise ConfigError(f"MPAY_PUBLIC_ROOT_URL is invalid: {exc}") from exc
    if parsed.scheme.lower() != "https" or not parsed.netloc or not hostname:
        raise ConfigError("MPAY_PUBLIC_ROOT_URL must be an HTTPS root origin, e.g. https://quantaedge.in")
    if parsed.username is not None or parsed.password is not None or port is not None:
        raise ConfigError("MPAY_PUBLIC_ROOT_URL must not contain credentials or a port")
    if parsed.path not in ("", "/") or parsed.query or parsed.fragment or "?" in value or "#" in value:
        raise ConfigError("MPAY_PUBLIC_ROOT_URL must not contain a path, query, or fragment")
    try:
        domain = hostname.encode("idna").decode("ascii").lower()
    except UnicodeError as exc:
        raise ConfigError("MPAY_PUBLIC_ROOT_URL hostname is not valid IDNA") from exc
    labels = domain.split(".")
    if len(labels) < 2 or domain.startswith("www.") or any(not LABEL.fullmatch(label) for label in labels):
        raise ConfigError("MPAY_PUBLIC_ROOT_URL must be a bare DNS root domain (omit www.)")
    if len(domain) > 225:
        raise ConfigError("MPAY_PUBLIC_ROOT_URL is too long for generated mPay hostnames")
    return f"https://{domain}", domain


def read_root(env_file: Path) -> str:
    values: list[str] = []
    try:
        lines = env_file.read_text(encoding="utf-8").splitlines()
    except OSError as exc:
        raise ConfigError(f"cannot read env file: {exc}") from exc
    for raw in lines:
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = (part.strip() for part in line.split("=", 1))
        if key == "MPAY_PUBLIC_ROOT_URL":
            if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
                value = value[1:-1]
            values.append(value)
    if len(values) != 1:
        raise ConfigError("env file must contain exactly one MPAY_PUBLIC_ROOT_URL=... entry")
    return values[0]


def derive(root_value: str) -> dict[str, str]:
    root_url, domain = normalize_root(root_value)
    web_host = f"mpay.{domain}"
    api_host = f"api-mpay.{domain}"
    turn_host = f"turn-mpay.{domain}"
    web_url = f"https://{web_host}"
    api_url = f"https://{api_host}"
    ice = (
        "stun:stun.l.google.com:19302,"
        f"turn:{turn_host}:3478?transport=udp,"
        f"turn:{turn_host}:3478?transport=tcp"
    )
    return {
        "MPAY_PUBLIC_ROOT_URL": root_url,
        "MPAY_WEB_PUBLIC_URL": web_url,
        "MPAY_API_PUBLIC_URL": api_url,
        "MPAY_TURN_HOST": turn_host,
        "MPAY_PUBLIC_DOMAIN": web_host,
        "MPAY_API_DOMAIN": api_host,
        "MPAY_WEB_API_BASE_URL": api_url,
        "MPAY_API_BASE_URL": api_url,
        "MPAY_CORS_ALLOWED_ORIGINS": web_url,
        "MPAY_CALL_TURN_REALM": turn_host,
        "MPAY_CALL_ICE_SERVERS": ice,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    source = parser.add_mutually_exclusive_group(required=True)
    source.add_argument("--root-url", help="Canonical HTTPS root URL")
    source.add_argument("--env-file", type=Path, help="Read MPAY_PUBLIC_ROOT_URL from an env file")
    parser.add_argument("--format", choices=("env", "json", "github"), default="env")
    args = parser.parse_args()
    try:
        root = args.root_url if args.root_url is not None else read_root(args.env_file)
        config = derive(root)
    except ConfigError as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 2
    if args.format == "json":
        print(json.dumps(config, indent=2, sort_keys=True))
    else:
        for key, value in config.items():
            print(f"{key}={value}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
