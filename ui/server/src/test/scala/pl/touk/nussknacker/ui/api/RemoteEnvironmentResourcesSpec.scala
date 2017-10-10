package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{BeforeAndAfterEach, FlatSpec, Inside, Matchers}
import pl.touk.nussknacker.ui.api.helpers.EspItTest
import pl.touk.nussknacker.ui.api.helpers.TestFactory._
import pl.touk.nussknacker.ui.process.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.ui.process.migrate.{RemoteEnvironmentCommunicationError, RemoteEnvironment, TestMigrationResult}
import pl.touk.nussknacker.ui.sample.SampleProcess
import pl.touk.nussknacker.ui.util.ProcessComparator.{Difference, NodeNotPresentInCurrent, NodeNotPresentInOther}
import pl.touk.nussknacker.ui.util.ProcessComparator.{Difference, NodeNotPresentInCurrent}
import pl.touk.nussknacker.ui.validation.ValidationResults.ValidationResult

import scala.concurrent.{ExecutionContext, Future}
import argonaut.ArgonautShapeless._
import argonaut.DecodeJson
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.graph.node.Filter
import pl.touk.nussknacker.ui.EspError
import pl.touk.nussknacker.ui.process.repository.ProcessRepository.ProcessHistoryEntry
import pl.touk.nussknacker.ui.security.api.{LoggedUser, Permission}
import pl.touk.nussknacker.ui.util.ProcessComparator

class RemoteEnvironmentResourcesSpec extends FlatSpec with ScalatestRouteTest with ScalaFutures with Matchers
  with BeforeAndAfterEach with Inside with EspItTest {

  import pl.touk.nussknacker.ui.codec.UiCodecs._

  private implicit val codec = ProcessComparator.codec
  private implicit val map = DecodeJson.derive[TestMigrationResult]
  private implicit val decoder = DecodeJson.derive[TestMigrationSummary]

  implicit override val patienceConfig = PatienceConfig(timeout = scaled(Span(1, Seconds)), interval = scaled(Span(100, Millis)))

  private val processId: String = ProcessTestData.validProcess.id

  it should "fail when process does not exist" in {
    val remoteEnvironment = new MockRemoteEnvironment
    val route = withPermissions(new RemoteEnvironmentResources(remoteEnvironment, processRepository), Permission.Deploy)

    Get(s"/remoteEnvironment/$processId/2/compare/1") ~> route ~> check {
      status shouldEqual StatusCodes.NotFound
      responseAs[String] should include("No process fooProcess found")
    }

    Post(s"/remoteEnvironment/$processId/2/migrate") ~> route ~> check {
      status shouldEqual StatusCodes.NotFound
      responseAs[String] should include("No process fooProcess found")
    }

    remoteEnvironment.compareInvocations shouldBe 'empty
    remoteEnvironment.migrateInvocations shouldBe 'empty

  }

  it should "invoke migration for found process" in {
    val difference = Map("node1" -> NodeNotPresentInCurrent("node1", Filter("node1", Expression("spel", "#input == 4"))))
    val remoteEnvironment = new MockRemoteEnvironment(mockDifferences = Map(processId -> difference))

    val route = withPermissions(new RemoteEnvironmentResources(remoteEnvironment, processRepository), Permission.Deploy)
    import pl.touk.http.argonaut.Argonaut62Support._

    saveProcess(processId, ProcessTestData.validProcess) {
      Get(s"/remoteEnvironment/$processId/2/compare/1") ~> route ~> check {
        status shouldEqual StatusCodes.OK

        responseAs[Map[String, Difference]] shouldBe difference
      }
      remoteEnvironment.compareInvocations shouldBe List(ProcessTestData.validDisplayableProcess.copy(validationResult = None))


      Post(s"/remoteEnvironment/$processId/2/migrate") ~> route ~> check {
        status shouldEqual StatusCodes.OK
      }
      remoteEnvironment.migrateInvocations shouldBe List(ProcessTestData.validDisplayableProcess.copy(validationResult = None))

    }
  }

  it should "return 500 on failed test migration" in {
    import pl.touk.http.argonaut.Argonaut62Support._

    val results = List(
      TestMigrationResult(ProcessTestData.validDisplayableProcess.copy(id = "failingProcess"), ValidationResult.success, true),
      TestMigrationResult(ProcessTestData.validDisplayableProcess.copy(id = "notFailing"), ValidationResult.success, false)
    )

    val route = withPermissions(new RemoteEnvironmentResources(new MockRemoteEnvironment(results), processRepository), Permission.Deploy)

    Get(s"/remoteEnvironment/testAutomaticMigration") ~> route ~> check {
      status shouldEqual StatusCodes.InternalServerError

      responseAs[TestMigrationSummary] shouldBe TestMigrationSummary("Migration failed, following processes have new errors: failingProcess", results)
    }
  }

  it should "return result on test migration" in {
    import pl.touk.http.argonaut.Argonaut62Support._

    val results = List(
      TestMigrationResult(ProcessTestData.validDisplayableProcess, ValidationResult.success, false),
      TestMigrationResult(ProcessTestData.validDisplayableProcess.copy(id = "notFailing"), ValidationResult.success, false)
    )
    val route = withPermissions(new RemoteEnvironmentResources(new MockRemoteEnvironment(results), processRepository), Permission.Deploy)

    Get(s"/remoteEnvironment/testAutomaticMigration") ~> route ~> check {
      status shouldEqual StatusCodes.OK

      responseAs[TestMigrationSummary] shouldBe TestMigrationSummary("Migrations successful", results)
    }
  }

  it should "compare environments" in {

    import pl.touk.http.argonaut.Argonaut62Support._
    import pl.touk.nussknacker.engine.spel.Implicits._
    val processId1 = "proc1"
    val processId2 = "proc2"

    val difference = NodeNotPresentInOther("a", Filter("a", ""))


    val route = withPermissions(new RemoteEnvironmentResources(new MockRemoteEnvironment(mockDifferences = Map(
      processId1 -> Map("n1" -> difference),
      processId2 -> Map()

    )),
      processRepository), Permission.Deploy)

    saveProcess(processId1, ProcessTestData.validProcessWithId(processId1)) {
      saveProcess(processId2, ProcessTestData.validProcessWithId(processId2)) {
        Get(s"/remoteEnvironment/compare") ~> route ~> check {
          status shouldEqual StatusCodes.OK
          responseAs[EnvironmentComparisonResult] shouldBe EnvironmentComparisonResult(
            List(ProcessDifference(processId1,true,Map("n1" -> difference))))
        }
      }
    }

  }

  it should "not fail in comparing environments if process does not exist in the other one" in {
    import pl.touk.http.argonaut.Argonaut62Support._
    import pl.touk.nussknacker.engine.spel.Implicits._
    val processId1 = "proc1"
    val processId2 = "proc2"

    val difference = NodeNotPresentInOther("a", Filter("a", ""))


    val route = withPermissions(new RemoteEnvironmentResources(new MockRemoteEnvironment(mockDifferences = Map(
      processId1 -> Map("n1" -> difference)
    )),
      processRepository), Permission.Deploy)

    saveProcess(processId1, ProcessTestData.validProcessWithId(processId1)) {
      saveProcess(processId2, ProcessTestData.validProcessWithId(processId2)) {
        Get(s"/remoteEnvironment/compare") ~> route ~> check {
          status shouldEqual StatusCodes.OK
          responseAs[EnvironmentComparisonResult] shouldBe EnvironmentComparisonResult(
            List(ProcessDifference(processId1,true,Map("n1" -> difference)), ProcessDifference(processId2, false, Map())))
        }
      }
    }
  }

  class MockRemoteEnvironment(testMigrationResults: List[TestMigrationResult] = List(),
                              val mockDifferences : Map[String, Map[String, ProcessComparator.Difference]] = Map()) extends RemoteEnvironment {

    var migrateInvocations = List[DisplayableProcess]()
    var compareInvocations = List[DisplayableProcess]()

    override def migrate(localProcess: DisplayableProcess)(implicit ec: ExecutionContext, user: LoggedUser) = {
      migrateInvocations = localProcess :: migrateInvocations
      Future.successful(Right(()))
    }

    override def compare(localProcess: DisplayableProcess, remoteProcessVersion: Option[Long],
                  businessView: Boolean = false)(implicit ec: ExecutionContext) : Future[Either[EspError, Map[String, ProcessComparator.Difference]]]= {
      compareInvocations = localProcess :: compareInvocations
      Future.successful(mockDifferences.get(localProcess.id).fold[Either[EspError, Map[String, ProcessComparator.Difference]]](Left(RemoteEnvironmentCommunicationError(StatusCodes.NotFound, "")))
        (diffs => Right(diffs)))
    }

    override def processVersions(processId: String)(implicit ec: ExecutionContext): Future[List[ProcessHistoryEntry]] = Future.successful(List())

    override def testMigration(implicit ec: ExecutionContext): Future[Either[EspError, List[TestMigrationResult]]] = {
      Future.successful(Right(testMigrationResults))
    }

    override def targetEnvironmentId = "abcd"
  }


}
