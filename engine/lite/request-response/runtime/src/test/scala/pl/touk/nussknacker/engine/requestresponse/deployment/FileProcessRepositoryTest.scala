package pl.touk.nussknacker.engine.requestresponse.deployment

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller

import java.nio.charset.StandardCharsets
import java.nio.file.Files

class FileProcessRepositoryTest extends AnyFunSuite with Matchers {

  private val processesDirectory = Files.createTempDirectory("fileProcessRepositoryTest")

  private val repository = new FileProcessRepository(processesDirectory.toFile)

  private val processId = 54544L

  private val canonicalProcessJson =
    """{"additionalBranches":[],"nodes":[{"ref":{"parameters":[],"typ":"request1-post-source"},"id":"start","type":"Source"},{"ref":{"parameters":[],"typ":"response-sink"},"id":"endNodeIID","type":"Sink"}],"metaData":{"typeSpecificData":{"slug":"alamakota","type":"RequestResponseMetaData"},"id":"process1"}}"""

  private val deploymentJson =
    s"""
      |{
      |  "processVersion" : {
      |    "versionId" : 1,
      |    "processName" : "process1",
      |    "processId": $processId,
      |    "user" : "testUser",
      |    "modelVersion" : 3
      |  },
      |  "deploymentData" : {
      |    "deploymentId" : "",
      |    "user": { "id": "userId", "name": "userName" },
      |    "additionalDeploymentData": {}
      |  },
      |  "deploymentTime" : 5,
      |  "processJson":$canonicalProcessJson
      |}
      |""".stripMargin

  test("should load deployment data from file") {
    val processName = ProcessName("process1")
    Files.write(processesDirectory.resolve(processName.value), deploymentJson.getBytes(StandardCharsets.UTF_8))

    val deployments = repository.loadAll

    deployments should have size 1
    deployments should contain key (processName)
    val deployment = deployments(processName)
    deployment.processVersion.processName shouldBe processName
    deployment.processVersion.versionId shouldBe VersionId.initialVersionId
    deployment.processVersion.processId shouldBe ProcessId(processId)
    deployment.processVersion.modelVersion shouldBe Some(3)
    deployment.processVersion.user shouldBe "testUser"
    deployment.deploymentTime shouldBe 5
    deployment.processJson shouldBe ProcessMarshaller.fromJsonUnsafe(canonicalProcessJson)
  }

}
