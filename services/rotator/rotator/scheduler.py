import logging
from pathlib import Path

import yaml
from apscheduler.schedulers.blocking import BlockingScheduler
from apscheduler.triggers.cron import CronTrigger

from rotator import rotators
from rotator.config import Config

log = logging.getLogger(__name__)

_SCHEDULE_FILE = Path(__file__).parent.parent / "schedule.yml"


def run(config: Config) -> None:
    with open(_SCHEDULE_FILE) as f:
        schedule = yaml.safe_load(f)

    scheduler = BlockingScheduler()

    for entry in schedule["rotations"]:
        name: str = entry["name"]
        cron: str = entry["cron"]
        stacks: list[str] = entry["stacks"]

        for stack in stacks:
            scheduler.add_job(
                rotators.run,
                CronTrigger.from_crontab(cron),
                args=[name, stack, config],
                id=f"{name}-{stack}",
                name=f"rotate {name} on {stack}",
                misfire_grace_time=300,
                coalesce=True,
                replace_existing=True,
            )
            log.info("Scheduled %s / %s at cron=%r", name, stack, cron)

    log.info("Scheduler ready — %d jobs registered", len(scheduler.get_jobs()))
    scheduler.start()
