"""Unit tests for the LXD helper."""

from __future__ import annotations

import json
from pathlib import Path
from types import SimpleNamespace

from tests.helpers.lxd import LxdVm


def _vm_with_network(network: dict) -> LxdVm:
    vm = LxdVm("test", "images:debian/13/cloud", Path("/tmp/cloud-config.yaml"))
    payload = json.dumps([{"state": {"network": network}}])
    vm._lxc = lambda *args, **kwargs: SimpleNamespace(stdout=payload)  # type: ignore[method-assign]
    return vm


def test_get_ip_prefers_eth0_over_docker_bridge() -> None:
    vm = _vm_with_network(
        {
            "docker0": {
                "addresses": [
                    {"family": "inet", "scope": "global", "address": "172.17.0.1"},
                ]
            },
            "eth0": {
                "addresses": [
                    {"family": "inet", "scope": "global", "address": "10.161.16.22"},
                ]
            },
        }
    )

    assert vm.get_ip() == "10.161.16.22"


def test_get_ip_skips_bridge_like_interfaces_when_possible() -> None:
    vm = _vm_with_network(
        {
            "br-1234": {
                "addresses": [
                    {"family": "inet", "scope": "global", "address": "172.18.0.1"},
                ]
            },
            "ens5": {
                "addresses": [
                    {"family": "inet", "scope": "global", "address": "192.0.2.10"},
                ]
            },
        }
    )

    assert vm.get_ip() == "192.0.2.10"
