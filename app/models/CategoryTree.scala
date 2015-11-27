package models

/**
  * Defines an i2b2 tree-like structure for categories.
  *
  */
abstract class CategoryTree(label: String, parent: Option[CategoryTree], children: Iterable[CategoryTree])
{
  override def toString = label
  override def hashCode = label.hashCode

  def getPath() : String =
  {
    this match {
      case Empty(_) => return ""
      case Root(_, _) => return label
      case _ => return this.toString() + "+" + parent.toString()
    }
  }
}

// variants of CategoryTree
// Empty class is used if no categories used
case class Empty(label: String) extends CategoryTree("", None, Iterable())
case class Root(label: String, children: Iterable[CategoryTree]) extends CategoryTree(label, None, children)
case class Node(label: String, parent: Option[CategoryTree], children: Iterable[CategoryTree]) extends CategoryTree(label, parent, children)
case class Leaf(label: String, parent: Option[CategoryTree]) extends CategoryTree(label, parent, List())

