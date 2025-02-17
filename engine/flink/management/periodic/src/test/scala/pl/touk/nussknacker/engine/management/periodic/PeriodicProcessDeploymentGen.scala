package pl.touk.nussknacker.engine.management.periodic

import pl.touk.nussknacker.engine.management.periodic.model.{
  PeriodicProcessDeployment,
  PeriodicProcessDeploymentId,
  PeriodicProcessDeploymentState,
  PeriodicProcessDeploymentStatus,
  ScheduleName
}

import java.time.LocalDateTime

object PeriodicProcessDeploymentGen {

  def apply(): PeriodicProcessDeployment = {
    PeriodicProcessDeployment(
      id = PeriodicProcessDeploymentId(42),
      periodicProcess = PeriodicProcessGen(),
      runAt = LocalDateTime.now(),
      scheduleName = ScheduleName(None),
      retriesLeft = 0,
      nextRetryAt = None,
      state = PeriodicProcessDeploymentState(
        deployedAt = None,
        completedAt = None,
        status = PeriodicProcessDeploymentStatus.Scheduled,
      )
    )
  }

}
