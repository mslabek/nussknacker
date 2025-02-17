package pl.touk.nussknacker.ui.validation

import cats.data.Validated
import cats.data.Validated.{Invalid, Valid, invalid, valid}
import pl.touk.nussknacker.engine.api.context.PartSubGraphCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{MissingRequiredProperty, UnknownProperty}
import pl.touk.nussknacker.engine.api.definition.{MandatoryParameterValidator, ParameterValidator}
import pl.touk.nussknacker.engine.api.component.ScenarioPropertyConfig
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.restmodel.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.restmodel.validation.PrettyValidationErrors
import pl.touk.nussknacker.restmodel.validation.ValidationResults.ValidationResult
import pl.touk.nussknacker.ui.definition.scenarioproperty.ScenarioPropertyValidatorDeterminerChain
import pl.touk.nussknacker.ui.process.processingtypedata.ProcessingTypeDataProvider

class ScenarioPropertiesValidator(
    scenarioPropertiesConfig: ProcessingTypeDataProvider[Map[String, ScenarioPropertyConfig], _]
) {

  import cats.implicits._

  implicit val nodeId: NodeId = NodeId("properties")

  type PropertyConfig = Map[String, ScenarioPropertyConfig]

  def validate(process: DisplayableProcess): ValidationResult =
    scenarioPropertiesConfig.forType(process.processingType) match {
      case None =>
        ValidationResult.globalErrors(List(PrettyValidationErrors.noValidatorKnown(process.processingType)))

      case Some(config) => {
        val scenarioProperties = process.properties.additionalFields.properties.toList

        val validated = (
          getConfiguredValidationsResults(config, scenarioProperties),
          getMissingRequiredPropertyValidationResults(config, scenarioProperties),
          getUnknownPropertyValidationResults(config, scenarioProperties)
        )
          .mapN { (_, _, _) => () }

        val processPropertiesErrors = validated match {
          case Invalid(e) => e.map(error => PrettyValidationErrors.formatErrorMessage(error)).toList
          case Valid(_)   => List.empty
        }

        ValidationResult.errors(Map(), processPropertiesErrors, List())
      }
    }

  private def getConfiguredValidationsResults(config: PropertyConfig, scenarioProperties: List[(String, String)]) = {
    val validatorsByPropertyName = config
      .map(propertyConfig =>
        propertyConfig._1 -> ScenarioPropertyValidatorDeterminerChain(propertyConfig._2).determine()
      )

    val propertiesWithConfiguredValidator = for {
      property  <- scenarioProperties
      validator <- validatorsByPropertyName.getOrElse(property._1, List.empty)
    } yield (property, config.get(property._1), validator)

    propertiesWithConfiguredValidator
      .collect { case (property, Some(config), validator: ParameterValidator) =>
        validator.isValid(property._1, property._2, config.label).toValidatedNel
      }
      .sequence
      .map(_ => ())
  }

  private def getMissingRequiredPropertyValidationResults(
      config: PropertyConfig,
      scenarioProperties: List[(String, String)]
  ) = {
    config
      .filter(_._2.validators.nonEmpty)
      .filter(_._2.validators.get.contains(MandatoryParameterValidator))
      .map(propertyConfig =>
        (propertyConfig._1, propertyConfig._2, MissingRequiredPropertyValidator(scenarioProperties.map(_._1)))
      )
      .toList
      .map { case (propertyName, config, validator) =>
        validator.isValid(propertyName, config.label).toValidatedNel
      }
      .sequence
      .map(_ => ())
  }

  private def getUnknownPropertyValidationResults(
      config: PropertyConfig,
      scenarioProperties: List[(String, String)]
  ) = {
    scenarioProperties
      .map(property => (property._1, UnknownPropertyValidator(config)))
      .map { case (propertyName, validator) =>
        validator.isValid(propertyName).toValidatedNel
      }
      .sequence
      .map(_ => ())
  }

}

private final case class MissingRequiredPropertyValidator(actualPropertyNames: List[String]) {

  def isValid(propertyName: String, label: Option[String] = None)(
      implicit nodeId: NodeId
  ): Validated[PartSubGraphCompilationError, Unit] = {

    if (actualPropertyNames.contains(propertyName)) valid(()) else invalid(MissingRequiredProperty(propertyName, label))
  }

}

private final case class UnknownPropertyValidator(config: Map[String, ScenarioPropertyConfig]) {

  def isValid(propertyName: String)(implicit nodeId: NodeId): Validated[PartSubGraphCompilationError, Unit] = {

    if (config.get(propertyName).nonEmpty) valid(()) else invalid(UnknownProperty(propertyName))
  }

}
