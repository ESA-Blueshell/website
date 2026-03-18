import logging
from typing import Optional

from rotator.config import Config
from rotator.infisical import InfisicalClient
from rotator.rotators.database import DatabaseRotator
from rotator.rotators.google_calendar import GoogleCalendarRotator
from rotator.rotators.jwt import JwtRotator
from rotator.rotators.listmonk import ListmonkRotator

log = logging.getLogger(__name__)

_ROTATORS = {
    "jwt": JwtRotator(),
    "database": DatabaseRotator(),
    "listmonk": ListmonkRotator(),
    "google-calendar": GoogleCalendarRotator(),
}


def available() -> list[str]:
    return list(_ROTATORS)


def run(name: str, stack: str, config: Config) -> None:
    env = _stack_to_env(stack)

    infisical: Optional[InfisicalClient] = None
    if config.infisical_configured:
        infisical = InfisicalClient(config.infisical_base_url, config.infisical_token, config.infisical_project_id)
        log.info("Infisical client configured (%s)", config.infisical_base_url)
    else:
        log.warning("Infisical not configured — rotations will update local env files only")

    try:
        if name == "all":
            for rotator_name, rotator in _ROTATORS.items():
                log.info("--- running rotator: %s ---", rotator_name)
                rotator.rotate(env, stack, infisical, config)
        else:
            if name not in _ROTATORS:
                raise ValueError(f"Unknown rotator {name!r}. Available: {', '.join(_ROTATORS)}")
            _ROTATORS[name].rotate(env, stack, infisical, config)
    finally:
        if infisical is not None:
            infisical.close()


def _stack_to_env(stack: str) -> str:
    return "prod" if stack == "website" else stack.removeprefix("website-")
