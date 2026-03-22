import logging
import subprocess

log = logging.getLogger(__name__)


def _run(cmd: list[str]) -> str:
    result = subprocess.run(cmd, capture_output=True, text=True)
    if result.returncode != 0:
        raise RuntimeError(
            f"docker command failed: {' '.join(cmd)}\nstderr: {result.stderr.strip()}"
        )
    return result.stdout.strip()


def find_container(service_name: str) -> str:
    """Return the first running container ID for a Swarm service."""
    container_id = _run([
        "docker", "ps", "-q",
        "--filter", f"label=com.docker.swarm.service.name={service_name}",
    ])
    if not container_id:
        raise RuntimeError(f"No running container for service {service_name!r}")
    return container_id.splitlines()[0]


def exec_in_service(service_name: str, cmd: list[str]) -> str:
    """Run a command inside the first container of a Swarm service."""
    container_id = find_container(service_name)
    output = _run(["docker", "exec", container_id, *cmd])
    log.debug("exec in %s: %s → %s", service_name, cmd, output[:200])
    return output


def force_update(service_name: str) -> None:
    """Force-update (rolling restart) a Swarm service."""
    _run(["docker", "service", "update", "--force", service_name])
    log.info("Force-updated service %s", service_name)


def remove_volume(volume_name: str) -> None:
    """Remove a Docker volume, silently ignoring if it does not exist."""
    result = subprocess.run(
        ["docker", "volume", "rm", volume_name],
        capture_output=True, text=True,
    )
    if result.returncode == 0:
        log.info("Removed volume %s", volume_name)
    elif "No such volume" in result.stderr:
        log.debug("Volume %s not found, skipping removal", volume_name)
    else:
        raise RuntimeError(f"Failed to remove volume {volume_name}: {result.stderr.strip()}")


def stack_deploy(repo_root: str, stack: str) -> None:
    """Run the deploy script to redeploy a stack with updated env vars."""
    deploy_script = f"{repo_root}/services/deploy.sh"
    log.info("Redeploying stack %s via %s", stack, deploy_script)
    result = subprocess.run(
        ["bash", deploy_script, stack],
        capture_output=True, text=True,
        cwd=repo_root,
    )
    if result.returncode != 0:
        raise RuntimeError(
            f"Stack deploy failed for {stack}\nstdout: {result.stdout[-500:]}\nstderr: {result.stderr[-500:]}"
        )
    log.info("Stack %s redeployed successfully", stack)
