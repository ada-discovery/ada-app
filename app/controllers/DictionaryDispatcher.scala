package controllers

import util.FilterSpec

class DictionaryDispatcher(controllers : Iterable[(String, DictionaryController)]) extends ControllerDispatcher[DictionaryController]("dataSet", controllers) with DictionaryController {

  override def get(id: String) = dispatch(_.get(id))

  override def find(page: Int, orderBy: String, filter: FilterSpec) = dispatch(_.find(page, orderBy, filter))

  override def listAll(orderBy: Int) = dispatch(_.listAll(orderBy))

  override def create = dispatch(_.create)

  override def update(id: String) = dispatch(_.update(id))

  override def edit(id: String) = dispatch(_.edit(id))

  override def delete(id: String) = dispatch(_.delete(id))

  override def save = dispatch(_.save)

  override def overviewList(page: Int, orderBy: String, filter: FilterSpec) = dispatch(_.overviewList(page, orderBy, filter))

  override def getFieldNames = dispatch(_.getFieldNames)

  override def dataSetId = ???
}