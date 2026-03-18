from abc import ABC, abstractmethod

from rotator.config import Config
from rotator.infisical import InfisicalClient


class Rotator(ABC):
    @abstractmethod
    def rotate(self, env: str, stack: str, infisical: InfisicalClient, config: Config) -> None:
        """Perform the rotation for the given Infisical environment and stack name."""
