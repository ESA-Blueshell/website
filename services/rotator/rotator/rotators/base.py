from abc import ABC, abstractmethod
from typing import Optional

from rotator.config import Config
from rotator.infisical import InfisicalClient


class Rotator(ABC):
    @abstractmethod
    def rotate(self, env: str, stack: str, infisical: Optional[InfisicalClient], config: Config) -> None:
        """Perform the rotation for the given environment and stack name."""
