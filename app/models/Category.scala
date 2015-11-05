package models

case class Category(name : String, var parent : Option[Category] = None, var children : Iterable[Category] = List[Category]()) {
  def getPath : List[String] = (if (parent.isDefined && parent.get.parent.isDefined) parent.get.getPath else List[String]()) ++ List(name)
  def addChildren(newChildren  : Iterable[Category]) : Category = {
    children = newChildren
    children.foreach(_.parent = Some(this))
    this
  }

  override def toString = name

  override def hashCode = name.hashCode
}
