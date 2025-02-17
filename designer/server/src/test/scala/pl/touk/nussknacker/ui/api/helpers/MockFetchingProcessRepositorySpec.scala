package pl.touk.nussknacker.ui.api.helpers

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.deployment.ProcessActionType._
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import pl.touk.nussknacker.restmodel.processdetails.{ProcessDetails, ProcessShapeFetchStrategy}
import pl.touk.nussknacker.ui.api.helpers.TestProcessUtil._
import pl.touk.nussknacker.ui.api.helpers.TestProcessingTypes._
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter
import pl.touk.nussknacker.ui.process.repository.FetchingProcessRepository.FetchProcessesDetailsQuery
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.util.Try

class MockFetchingProcessRepositorySpec extends AnyFlatSpec with Matchers with ScalaFutures {

  import org.scalatest.prop.TableDrivenPropertyChecks._

  import scala.concurrent.ExecutionContext.Implicits.global

  private val categoryMarketing   = "marketing"
  private val categoryFraud       = "fraud"
  private val categoryFraudSecond = "fraudSecond"
  private val categorySecret      = "secret"

  private val json    = ProcessTestData.sampleDisplayableProcess
  private val subJson = ProcessConverter.toDisplayable(ProcessTestData.sampleFragment, Streaming, categoryMarketing)

  private val someVersion = VersionId(666L)

  private val marketingProcess =
    createBasicProcess("marketingProcess", category = categoryMarketing, lastAction = Some(Deploy), json = Some(json))
  private val marketingFragment =
    createFragment("marketingFragment", category = categoryMarketing, json = Some(subJson))

  private val marketingArchivedFragment = createFragment(
    "marketingArchivedFragment",
    isArchived = true,
    category = categoryMarketing,
    lastAction = Some(Archive)
  )

  private val marketingArchivedProcess = createBasicProcess(
    "marketingArchivedProcess",
    isArchived = true,
    category = categoryMarketing,
    lastAction = Some(Archive)
  )

  private val fraudProcess =
    createBasicProcess("fraudProcess", category = categoryFraud, processingType = Fraud, lastAction = Some(Deploy))

  private val fraudArchivedProcess = createBasicProcess(
    "fraudArchivedProcess",
    isArchived = true,
    category = categoryFraudSecond,
    processingType = Fraud,
    lastAction = Some(Archive),
    json = Some(json)
  )

  private val fraudFragment =
    createFragment("fraudFragment", category = categoryFraud, processingType = Fraud, json = Some(json))

  private val fraudArchivedFragment = createFragment(
    "fraudArchivedFragment",
    isArchived = true,
    category = categoryFraud,
    processingType = Fraud,
    json = Some(subJson)
  )

  private val fraudSecondProcess = createBasicProcess(
    "fraudSecondProcess",
    category = categoryFraudSecond,
    processingType = Fraud,
    lastAction = Some(Cancel),
    json = Some(json)
  )

  private val fraudSecondFragment =
    createFragment("fraudSecondFragment", category = categoryFraudSecond, processingType = Fraud)

  private val secretProcess  = createBasicProcess("secretProcess", category = categorySecret)
  private val secretFragment = createFragment("secretFragment", category = categorySecret)
  private val secretArchivedFragment =
    createFragment("secretArchivedFragment", isArchived = true, category = categorySecret, lastAction = Some(Archive))

  private val secretArchivedProcess = createBasicProcess(
    "secretArchivedProcess",
    isArchived = true,
    category = categorySecret,
    lastAction = Some(Archive),
    json = Some(json)
  )

  private val processes: List[ProcessDetails] = List(
    marketingProcess,
    marketingArchivedProcess,
    marketingFragment,
    marketingArchivedFragment,
    fraudProcess,
    fraudArchivedProcess,
    fraudFragment,
    fraudArchivedFragment,
    fraudSecondProcess,
    fraudSecondFragment,
    secretProcess,
    secretArchivedProcess,
    secretFragment,
    secretArchivedFragment
  )

  private val admin: LoggedUser = TestFactory.adminUser()
  private val marketingUser: LoggedUser =
    TestFactory.userWithCategoriesReadPermission(categories = List(categoryMarketing))
  private val fraudUser: LoggedUser =
    TestFactory.userWithCategoriesReadPermission(categories = List(categoryFraud, categoryFraudSecond))

  private val DisplayableShape = ProcessShapeFetchStrategy.FetchDisplayable
  private val CanonicalShape   = ProcessShapeFetchStrategy.FetchCanonical
  private val NoneShape        = ProcessShapeFetchStrategy.NotFetch

  private val mockRepository = MockFetchingProcessRepository.withProcessesDetails(processes)

  it should "fetchProcessesDetails for each user" in {
    val testingData = Table(
      ("user", "expected"),
      (admin, List(marketingProcess, fraudProcess, fraudSecondProcess, secretProcess)),
      (marketingUser, List(marketingProcess)),
      (fraudUser, List(fraudProcess, fraudSecondProcess)),
    )

    forAll(testingData) { (user: LoggedUser, expected: List[ProcessDetails]) =>
      val result = mockRepository
        .fetchProcessesDetails(FetchProcessesDetailsQuery.unarchivedProcesses)(DisplayableShape, user, global)
        .futureValue
      result shouldBe expected
    }
  }

  it should "fetchDeployedProcessesDetails for each user" in {
    val testingData = Table(
      ("user", "expected"),
      (admin, List(marketingProcess, fraudProcess)),
      (marketingUser, List(marketingProcess)),
      (fraudUser, List(fraudProcess)),
    )

    forAll(testingData) { (user: LoggedUser, expected: List[ProcessDetails]) =>
      val result = mockRepository
        .fetchProcessesDetails(FetchProcessesDetailsQuery.deployed)(DisplayableShape, user, global)
        .futureValue
      result shouldBe expected
    }
  }

  it should "fetchProcessesDetails by names for each user" in {
    val testingData = Table(
      ("user", "expected"),
      (admin, List(marketingProcess, fraudProcess, fraudSecondProcess, secretProcess)),
      (marketingUser, List(marketingProcess)),
      (fraudUser, List(fraudProcess, fraudSecondProcess)),
    )

    forAll(testingData) { (user: LoggedUser, expected: List[ProcessDetails]) =>
      val names = processes.map(_.idWithName.name)
      val result = mockRepository
        .fetchProcessesDetails(
          FetchProcessesDetailsQuery(names = Some(names), isArchived = Some(false), isFragment = Some(false))
        )(DisplayableShape, user, global)
        .futureValue
      result shouldBe expected
    }
  }

  it should "fetchFragmentsDetails for each user" in {
    val testingData = Table(
      ("user", "expected"),
      (admin, List(marketingFragment, fraudFragment, fraudSecondFragment, secretFragment)),
      (marketingUser, List(marketingFragment)),
      (fraudUser, List(fraudFragment, fraudSecondFragment)),
    )

    forAll(testingData) { (user: LoggedUser, expected: List[ProcessDetails]) =>
      val result = mockRepository
        .fetchProcessesDetails(FetchProcessesDetailsQuery.unarchivedFragments)(DisplayableShape, user, global)
        .futureValue
      result shouldBe expected
    }
  }

  it should "fetchFragmentsDetails with each processing shape strategy" in {
    val fragments      = List(marketingFragment, fraudFragment, fraudSecondFragment, secretFragment)
    val mockRepository = MockFetchingProcessRepository.withProcessesDetails(fragments)

    val displayableFragments = List(marketingFragment, fraudFragment, fraudSecondFragment, secretFragment)
    val canonicalFragments   = displayableFragments.map(p => p.copy(json = ProcessConverter.fromDisplayable(p.json)))
    val noneFragments        = displayableFragments.map(p => p.copy(json = ()))

    mockRepository
      .fetchProcessesDetails(FetchProcessesDetailsQuery.unarchivedFragments)(DisplayableShape, admin, global)
      .futureValue shouldBe displayableFragments
    mockRepository
      .fetchProcessesDetails(FetchProcessesDetailsQuery.unarchivedFragments)(CanonicalShape, admin, global)
      .futureValue shouldBe canonicalFragments
    mockRepository
      .fetchProcessesDetails(FetchProcessesDetailsQuery.unarchivedFragments)(NoneShape, admin, global)
      .futureValue shouldBe noneFragments
  }

  it should "fetchAllProcessesDetails for each user" in {
    val testingData = Table(
      ("user", "expected"),
      (
        admin,
        List(
          marketingProcess,
          marketingFragment,
          fraudProcess,
          fraudFragment,
          fraudSecondProcess,
          fraudSecondFragment,
          secretProcess,
          secretFragment
        )
      ),
      (marketingUser, List(marketingProcess, marketingFragment)),
      (fraudUser, List(fraudProcess, fraudFragment, fraudSecondProcess, fraudSecondFragment)),
    )

    forAll(testingData) { (user: LoggedUser, expected: List[ProcessDetails]) =>
      val result = mockRepository
        .fetchProcessesDetails(FetchProcessesDetailsQuery.unarchived)(DisplayableShape, user, global)
        .futureValue
      result shouldBe expected
    }
  }

  it should "fetchLatestProcessDetailsForProcessId for each user" in {
    val testingData = Table(
      ("user", "ProcessWithoutNodes", "expected"),
      (admin, secretFragment, Some(secretFragment)),
      (marketingUser, marketingProcess, Some(marketingProcess)),
      (marketingUser, marketingArchivedProcess, Some(marketingArchivedProcess)),
      (marketingUser, marketingArchivedFragment, Some(marketingArchivedFragment)),
      (fraudUser, marketingProcess, None),
    )

    forAll(testingData) { (user: LoggedUser, process: ProcessDetails, expected: Option[ProcessDetails]) =>
      val result = mockRepository
        .fetchLatestProcessDetailsForProcessId(process.processId)(DisplayableShape, user, global)
        .futureValue
      result shouldBe expected
    }
  }

  it should "fetchProcessDetailsForId for each user" in {
    val testingData = Table(
      ("user", "processId", "versionId", "expected"),
      (admin, secretFragment.processId, secretFragment.processVersionId, Some(secretFragment)),
      (admin, secretFragment.processId, someVersion, None),
      (marketingUser, marketingProcess.processId, marketingProcess.processVersionId, Some(marketingProcess)),
      (marketingUser, marketingProcess.processId, someVersion, None),
      (
        marketingUser,
        marketingArchivedProcess.processId,
        marketingArchivedProcess.processVersionId,
        Some(marketingArchivedProcess)
      ),
      (marketingUser, marketingArchivedProcess.processId, someVersion, None),
      (
        marketingUser,
        marketingArchivedFragment.processId,
        marketingArchivedFragment.processVersionId,
        Some(marketingArchivedFragment)
      ),
      (marketingUser, marketingArchivedFragment.processId, someVersion, None),
      (fraudUser, marketingProcess.processId, marketingProcess.processVersionId, None),
    )

    forAll(testingData) {
      (user: LoggedUser, processId: ProcessId, versionId: VersionId, expected: Option[ProcessDetails]) =>
        val result =
          mockRepository.fetchProcessDetailsForId(processId, versionId)(DisplayableShape, user, global).futureValue
        result shouldBe expected
    }
  }

  it should "return ProcessId for ProcessName" in {
    val data = processes.map(p => (p.idWithName.name, Some(p.processId))) ++ List((ProcessName("not-exist-name"), None))

    data.foreach { case (processName, processId) =>
      val result = mockRepository.fetchProcessId(processName).futureValue
      result shouldBe processId
    }
  }

  it should "return ProcessName for ProcessId" in {
    val data = processes.map(p => (p.processId, Some(p.idWithName.name))) ++ List((ProcessId(666), None))

    data.foreach { case (processId, processName) =>
      val result = mockRepository.fetchProcessName(processId).futureValue
      result shouldBe processName
    }
  }

  it should "return ProcessingType for ProcessId" in {
    val testingData = Table(
      ("user", "userProcesses"),
      (admin, processes),
      (marketingUser, List(marketingProcess, marketingArchivedProcess, marketingFragment, marketingArchivedFragment)),
      (
        fraudUser,
        List(
          fraudProcess,
          fraudFragment,
          fraudSecondProcess,
          fraudSecondFragment,
          fraudArchivedProcess,
          fraudArchivedFragment
        )
      ),
    )

    forAll(testingData) { (user: LoggedUser, userProcesses: List[ProcessDetails]) =>
      processes.foreach(process => {
        val result         = mockRepository.fetchProcessingType(process.processId)(user, global)
        val processingType = Try(result.futureValue).toOption
        val expected       = if (userProcesses.contains(process)) Some(process.processingType) else None
        processingType shouldBe expected
      })
    }
  }

  it should "fetchProcesses for each user by mixed FetchQuery" in {
    // given
    val allProcessesQuery = FetchProcessesDetailsQuery()
    val allProcessesCategoryQuery =
      allProcessesQuery.copy(categories = Some(Seq(categoryMarketing, categoryFraud, categoryFraudSecond)))
    val allProcessesCategoryTypesQuery = allProcessesCategoryQuery.copy(processingTypes = Some(List(Streaming)))

    val processesQuery         = FetchProcessesDetailsQuery(isFragment = Some(false), isArchived = Some(false))
    val deployedProcessesQuery = processesQuery.copy(isDeployed = Some(true))
    val deployedProcessesCategoryQuery =
      deployedProcessesQuery.copy(categories = Some(Seq(categoryMarketing, categoryFraud, categoryFraudSecond)))
    val deployedProcessesCategoryProcessingTypesQuery =
      deployedProcessesCategoryQuery.copy(processingTypes = Some(List(Streaming)))

    val notDeployedProcessesQuery = processesQuery.copy(isDeployed = Some(false))
    val notDeployedProcessesCategoryQuery =
      notDeployedProcessesQuery.copy(categories = Some(Seq(categoryMarketing, categoryFraud, categoryFraudSecond)))
    val notDeployedProcessesCategoryProcessingTypesQuery =
      notDeployedProcessesCategoryQuery.copy(processingTypes = Some(List(Streaming)))

    val archivedQuery          = FetchProcessesDetailsQuery(isArchived = Some(true))
    val archivedProcessesQuery = archivedQuery.copy(isFragment = Some(false))
    val archivedProcessesCategoryQuery =
      archivedProcessesQuery.copy(categories = Some(Seq(categoryMarketing, categoryFraud, categoryFraudSecond)))
    val archivedProcessesCategoryProcessingTypesQuery =
      archivedProcessesCategoryQuery.copy(processingTypes = Some(List(Streaming)))

    val allFragmentsQuery = FetchProcessesDetailsQuery(isFragment = Some(true))
    val fragmentsQuery    = allFragmentsQuery.copy(isArchived = Some(false))
    val fragmentsCategoryQuery =
      fragmentsQuery.copy(categories = Some(Seq(categoryMarketing, categoryFraud, categoryFraudSecond)))
    val fragmentsCategoryTypesQuery = fragmentsCategoryQuery.copy(processingTypes = Some(List(Streaming)))

    val archivedFragmentsQuery = FetchProcessesDetailsQuery(isFragment = Some(true), isArchived = Some(true))
    val archivedFragmentsCategoryQuery =
      archivedFragmentsQuery.copy(categories = Some(Seq(categoryMarketing, categoryFraud, categoryFraudSecond)))
    val archivedFragmentsCategoryTypesQuery =
      archivedFragmentsCategoryQuery.copy(processingTypes = Some(List(Streaming)))

    // when
    val testingData = Table(
      ("user", "query", "expected"),
      // admin user
      (admin, allProcessesQuery, processes),
      (
        admin,
        allProcessesCategoryQuery,
        List(
          marketingProcess,
          marketingArchivedProcess,
          marketingFragment,
          marketingArchivedFragment,
          fraudProcess,
          fraudArchivedProcess,
          fraudFragment,
          fraudArchivedFragment,
          fraudSecondProcess,
          fraudSecondFragment
        )
      ),
      (
        admin,
        allProcessesCategoryTypesQuery,
        List(marketingProcess, marketingArchivedProcess, marketingFragment, marketingArchivedFragment)
      ),
      (admin, processesQuery, List(marketingProcess, fraudProcess, fraudSecondProcess, secretProcess)),
      (admin, deployedProcessesQuery, List(marketingProcess, fraudProcess)),
      (admin, deployedProcessesCategoryQuery, List(marketingProcess, fraudProcess)),
      (admin, deployedProcessesCategoryProcessingTypesQuery, List(marketingProcess)),
      (admin, notDeployedProcessesQuery, List(fraudSecondProcess, secretProcess)),
      (admin, notDeployedProcessesCategoryQuery, List(fraudSecondProcess)),
      (admin, notDeployedProcessesCategoryProcessingTypesQuery, List()),
      (
        admin,
        archivedQuery,
        List(
          marketingArchivedProcess,
          marketingArchivedFragment,
          fraudArchivedProcess,
          fraudArchivedFragment,
          secretArchivedProcess,
          secretArchivedFragment
        )
      ),
      (admin, archivedProcessesQuery, List(marketingArchivedProcess, fraudArchivedProcess, secretArchivedProcess)),
      (admin, archivedProcessesCategoryQuery, List(marketingArchivedProcess, fraudArchivedProcess)),
      (admin, archivedProcessesCategoryProcessingTypesQuery, List(marketingArchivedProcess)),
      (
        admin,
        allFragmentsQuery,
        List(
          marketingFragment,
          marketingArchivedFragment,
          fraudFragment,
          fraudArchivedFragment,
          fraudSecondFragment,
          secretFragment,
          secretArchivedFragment
        )
      ),
      (admin, fragmentsQuery, List(marketingFragment, fraudFragment, fraudSecondFragment, secretFragment)),
      (admin, fragmentsCategoryQuery, List(marketingFragment, fraudFragment, fraudSecondFragment)),
      (admin, fragmentsCategoryTypesQuery, List(marketingFragment)),
      (admin, archivedFragmentsQuery, List(marketingArchivedFragment, fraudArchivedFragment, secretArchivedFragment)),
      (admin, archivedFragmentsCategoryQuery, List(marketingArchivedFragment, fraudArchivedFragment)),
      (admin, archivedFragmentsCategoryTypesQuery, List(marketingArchivedFragment)),

      // marketing user
      (
        marketingUser,
        allProcessesQuery,
        List(marketingProcess, marketingArchivedProcess, marketingFragment, marketingArchivedFragment)
      ),
      (
        marketingUser,
        allProcessesCategoryQuery,
        List(marketingProcess, marketingArchivedProcess, marketingFragment, marketingArchivedFragment)
      ),
      (
        marketingUser,
        allProcessesCategoryTypesQuery,
        List(marketingProcess, marketingArchivedProcess, marketingFragment, marketingArchivedFragment)
      ),
      (marketingUser, processesQuery, List(marketingProcess)),
      (marketingUser, deployedProcessesQuery, List(marketingProcess)),
      (marketingUser, deployedProcessesCategoryQuery, List(marketingProcess)),
      (marketingUser, deployedProcessesCategoryProcessingTypesQuery, List(marketingProcess)),
      (marketingUser, notDeployedProcessesQuery, List()),
      (marketingUser, notDeployedProcessesCategoryQuery, List()),
      (marketingUser, notDeployedProcessesCategoryProcessingTypesQuery, List()),
      (marketingUser, archivedQuery, List(marketingArchivedProcess, marketingArchivedFragment)),
      (marketingUser, archivedProcessesQuery, List(marketingArchivedProcess)),
      (marketingUser, archivedProcessesCategoryQuery, List(marketingArchivedProcess)),
      (marketingUser, archivedProcessesCategoryProcessingTypesQuery, List(marketingArchivedProcess)),
      (marketingUser, allFragmentsQuery, List(marketingFragment, marketingArchivedFragment)),
      (marketingUser, fragmentsQuery, List(marketingFragment)),
      (marketingUser, fragmentsCategoryQuery, List(marketingFragment)),
      (marketingUser, fragmentsCategoryTypesQuery, List(marketingFragment)),
      (marketingUser, archivedFragmentsQuery, List(marketingArchivedFragment)),
      (marketingUser, archivedFragmentsCategoryQuery, List(marketingArchivedFragment)),
      (marketingUser, archivedFragmentsCategoryTypesQuery, List(marketingArchivedFragment)),

      // fraud user
      (
        fraudUser,
        allProcessesQuery,
        List(
          fraudProcess,
          fraudArchivedProcess,
          fraudFragment,
          fraudArchivedFragment,
          fraudSecondProcess,
          fraudSecondFragment
        )
      ),
      (
        fraudUser,
        allProcessesCategoryQuery,
        List(
          fraudProcess,
          fraudArchivedProcess,
          fraudFragment,
          fraudArchivedFragment,
          fraudSecondProcess,
          fraudSecondFragment
        )
      ),
      (fraudUser, allProcessesCategoryTypesQuery, List()),
      (fraudUser, processesQuery, List(fraudProcess, fraudSecondProcess)),
      (fraudUser, deployedProcessesQuery, List(fraudProcess)),
      (fraudUser, deployedProcessesCategoryQuery, List(fraudProcess)),
      (fraudUser, deployedProcessesCategoryProcessingTypesQuery, List()),
      (fraudUser, notDeployedProcessesQuery, List(fraudSecondProcess)),
      (fraudUser, notDeployedProcessesCategoryQuery, List(fraudSecondProcess)),
      (fraudUser, notDeployedProcessesCategoryProcessingTypesQuery, List()),
      (fraudUser, archivedQuery, List(fraudArchivedProcess, fraudArchivedFragment)),
      (fraudUser, archivedProcessesQuery, List(fraudArchivedProcess)),
      (fraudUser, archivedProcessesCategoryQuery, List(fraudArchivedProcess)),
      (fraudUser, archivedProcessesCategoryProcessingTypesQuery, List()),
      (fraudUser, allFragmentsQuery, List(fraudFragment, fraudArchivedFragment, fraudSecondFragment)),
      (fraudUser, fragmentsQuery, List(fraudFragment, fraudSecondFragment)),
      (fraudUser, fragmentsCategoryQuery, List(fraudFragment, fraudSecondFragment)),
      (fraudUser, fragmentsCategoryTypesQuery, List()),
      (fraudUser, archivedFragmentsQuery, List(fraudArchivedFragment)),
      (fraudUser, archivedFragmentsQuery, List(fraudArchivedFragment)),
      (fraudUser, archivedFragmentsCategoryQuery, List(fraudArchivedFragment)),
      (fraudUser, archivedFragmentsCategoryTypesQuery, List()),
    )

    forAll(testingData) { (user: LoggedUser, query: FetchProcessesDetailsQuery, expected: List[ProcessDetails]) =>
      val result = mockRepository.fetchProcessesDetails(query)(DisplayableShape, user, global).futureValue

      // then
      result shouldBe expected
    }
  }

}
