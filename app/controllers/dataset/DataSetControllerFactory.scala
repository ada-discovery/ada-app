package controllers.dataset

import com.google.inject.ImplementedBy
import persistence.dataset.DataSetAccessorFactory
import util.ReflectionUtil.findClasses
import util.toCamel
import play.api.inject.Injector
import javax.inject.{Inject, Singleton}
import collection.mutable.{Map => MMap}
import play.api.Logger
import play.api.libs.concurrent.Execution.Implicits.defaultContext

import scala.reflect.ClassTag

@ImplementedBy(classOf[DataSetControllerFactoryImpl])
trait DataSetControllerFactory {
  def apply(dataSetId: String): Option[DataSetController]
}

@Singleton
protected class DataSetControllerFactoryImpl @Inject()(
    dsaf: DataSetAccessorFactory,
    genericFactory: GenericDataSetControllerFactory,
    injector : Injector
  ) extends DataSetControllerFactory {

  private val logger = Logger  // (this.getClass())
  protected val cache = MMap[String, DataSetController]()

  private val libPrefix = "ncer-pd"

  // TODO: locking and concurrency
  override def apply(dataSetId: String): Option[DataSetController] = {
    cache.get(dataSetId) match {
      case Some(controller) => Some(controller)
      case None =>
        dsaf(dataSetId).map { _ =>
          val controller = createController(dataSetId)
          cache.put(dataSetId, controller)
          controller
        }
    }
  }

  private def createController(dataSetId: String) = {
    val controllerClass = findControllerClass[DataSetController](dataSetId)
    if (controllerClass.isDefined)
      injector.instanceOf(controllerClass.get)
    else {
      logger.info(s"Controller class for the data set id '$dataSetId' not found. Creating a generic one...")
      genericFactory(dataSetId)
    }
  }

  private def controllerClassName(dataSetId: String) = toCamel(dataSetId).replace(" ", "") + "Controller"

  private def findControllerClass[T : ClassTag](dataSetId: String): Option[Class[T]] = {
    val className = controllerClassName(dataSetId)
    val classes = findClasses[T](libPrefix, Some("controllers."), Some(className))
    if (classes.nonEmpty)
      Some(classes.head)
    else
      None
  }
}