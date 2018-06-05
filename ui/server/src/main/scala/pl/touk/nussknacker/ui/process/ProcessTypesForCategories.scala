package pl.touk.nussknacker.ui.process

import java.util.Collections

import com.typesafe.config.{Config, ConfigFactory}
import pl.touk.nussknacker.ui.db.entity.ProcessEntity.ProcessingType
import pl.touk.nussknacker.ui.db.entity.ProcessEntity.ProcessingType.ProcessingType

import scala.collection.JavaConversions._
import net.ceedubs.ficus.Ficus._


class ProcessTypesForCategories(config: Config) {

  private val categoriesToTypesMap = {
    val categories = config.getOrElse("categoriesConfig", ConfigFactory.parseMap(Collections.singletonMap("Default", "streaming")))
    categories.entrySet().map(_.getKey).map(category => category ->
      ProcessingType.withName(categories.getString(category))).toMap
  }

  def getTypeForCategory(category: String) : Option[ProcessingType] = {
    categoriesToTypesMap.get(category)
  }

}
