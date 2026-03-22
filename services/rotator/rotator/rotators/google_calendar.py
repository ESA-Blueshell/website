import base64
import json
import logging
import os
import time
from typing import Optional

import httpx
import jwt as pyjwt

from rotator import docker_ops
from rotator.config import Config
from rotator.env_files import update_env_file
from rotator.infisical import InfisicalClient, try_infisical_set
from rotator.rotators.base import Rotator

log = logging.getLogger(__name__)

_GOOGLE_TOKEN_URL = "https://oauth2.googleapis.com/token"
_IAM_BASE = "https://iam.googleapis.com/v1"


class GoogleCalendarRotator(Rotator):
    def rotate(self, env: str, stack: str, infisical: Optional[InfisicalClient], config: Config) -> None:
        log.info("[google-calendar] rotating service account key for stack=%s", stack)

        api_env = os.path.join(config.repo_root, "services/api/.api.env")
        sa_json_str = self._get_sa_json(env, infisical, api_env)
        sa = json.loads(sa_json_str)

        access_token = self._get_access_token(sa)
        old_key_id = sa["private_key_id"]
        new_sa_json = self._create_key(access_token, sa["project_id"], sa["client_email"])

        update_env_file(api_env, "GOOGLE_CALENDAR_SA_JSON", new_sa_json)
        try_infisical_set(infisical, "GOOGLE_CALENDAR_SA_JSON", new_sa_json, env)
        docker_ops.stack_deploy(config.repo_root, stack)

        self._delete_key(access_token, sa["project_id"], sa["client_email"], old_key_id)
        log.info("[google-calendar] key rotated, old key %s deleted", old_key_id)

    @staticmethod
    def _get_sa_json(env: str, infisical: Optional[InfisicalClient], api_env: str) -> str:
        """Get SA JSON from Infisical or fall back to local env file."""
        if infisical is not None:
            try:
                return infisical.get("GOOGLE_CALENDAR_SA_JSON", env)
            except Exception:
                log.warning("Could not fetch GOOGLE_CALENDAR_SA_JSON from Infisical, trying local env")
        if os.path.isfile(api_env):
            with open(api_env) as f:
                for line in f:
                    if line.startswith("GOOGLE_CALENDAR_SA_JSON="):
                        return line.split("=", 1)[1].strip()
        raise RuntimeError("GOOGLE_CALENDAR_SA_JSON not found in Infisical or local env file")

    def _get_access_token(self, sa: dict) -> str:
        now = int(time.time())
        claim = {
            "iss": sa["client_email"],
            "scope": "https://www.googleapis.com/auth/cloud-platform",
            "aud": _GOOGLE_TOKEN_URL,
            "exp": now + 3600,
            "iat": now,
        }
        signed = pyjwt.encode(claim, sa["private_key"], algorithm="RS256")

        response = httpx.post(
            _GOOGLE_TOKEN_URL,
            data={
                "grant_type": "urn:ietf:params:oauth:grant-type:jwt-bearer",
                "assertion": signed,
            },
        )
        response.raise_for_status()
        return response.json()["access_token"]

    def _create_key(self, access_token: str, project_id: str, sa_email: str) -> str:
        response = httpx.post(
            f"{_IAM_BASE}/projects/{project_id}/serviceAccounts/{sa_email}/keys",
            headers={"Authorization": f"Bearer {access_token}"},
            json={
                "keyAlgorithm": "KEY_ALG_RSA_2048",
                "privateKeyType": "TYPE_GOOGLE_CREDENTIALS_FILE",
            },
        )
        response.raise_for_status()
        return base64.b64decode(response.json()["privateKeyData"]).decode()

    def _delete_key(
        self, access_token: str, project_id: str, sa_email: str, key_id: str
    ) -> None:
        response = httpx.delete(
            f"{_IAM_BASE}/projects/{project_id}/serviceAccounts/{sa_email}/keys/{key_id}",
            headers={"Authorization": f"Bearer {access_token}"},
        )
        response.raise_for_status()
