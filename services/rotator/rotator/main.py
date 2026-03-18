import logging

import click

from rotator import config as cfg
from rotator import rotators


@click.group()
@click.option("--log-level", default="INFO", show_default=True,
              type=click.Choice(["DEBUG", "INFO", "WARNING", "ERROR"], case_sensitive=False))
def cli(log_level: str) -> None:
    logging.basicConfig(
        level=log_level,
        format="%(asctime)s  %(levelname)-8s  %(name)s  %(message)s",
        datefmt="%Y-%m-%dT%H:%M:%S",
    )


@cli.command()
def schedule() -> None:
    """Start the scheduler (long-running). Reads schedule.yml for cron config."""
    from rotator.scheduler import run
    run(cfg.load())


@cli.command()
@click.argument("name", metavar="NAME",
                type=click.Choice([*rotators.available(), "all"], case_sensitive=False))
@click.argument("stack", default="website", metavar="STACK")
def rotate(name: str, stack: str) -> None:
    """Run a single rotation immediately.

    \b
    NAME   — rotator name or 'all'
    STACK  — docker stack name (default: website)
    """
    rotators.run(name, stack, cfg.load())
