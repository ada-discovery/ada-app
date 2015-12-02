package persistence

/**
  * Defines an i2b2 tree-like structure for categories.
  *
  */
abstract class CategoryTree(var label: String, var parent: Option[CategoryTree], var children: Iterable[CategoryTree])
{
  //var label : String
  //var parent : Option[CategoryTree]
  //var children : Iterable[CategoryTree]

  override def toString = label
  override def hashCode = label.hashCode

  def getPath() : String =
  {
    this match {
      case Empty(lab) => return lab
      case Root(lab, _) => return lab
      case Leaf(lab, _) => return lab + "+" + parent.get.getPath()
      case Node(lab, Some(x), _) => return x.getPath() + "+" + getPath()
      case _ => return ""
    }
  }

  // set new children
  def setChildren(newChildren  : Iterable[CategoryTree]) : Unit = {
    children = newChildren
    children.foreach(_.parent = Some(this))
  }

}

// variants of CategoryTree
// Empty class is used if no categories used
case class Empty(lab: String = "") extends CategoryTree(lab, None, Iterable())
case class Root(lab: String, chi: Iterable[CategoryTree]) extends CategoryTree(lab, None, chi){setChildren(chi)}
case class Node(lab: String, par: Option[CategoryTree], chi: Iterable[CategoryTree]) extends CategoryTree(lab, par, chi){setChildren(chi)}
case class Leaf(lab: String, par: Option[CategoryTree]) extends CategoryTree(lab, par, List())




object CategoryUtils{
  //encode entire i2b2 tree as string
  def encode(tree: CategoryTree) : String = {
    //TODO dummy

    return ""
  }

  // decode branch of CategoryTree based on i2b2 tree
  def decode(i2b2: String) : CategoryTree = {
    //TODO dummy

    return Empty()
  }
}


