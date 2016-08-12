package dataaccess

import dataaccess.RepoTypes._
import dataaccess.FieldType

trait JsonCrudRepoFactory {
  def apply(collectionName: String): JsonCrudRepo

  // by default call apply
  def applyWithDictionary(collectionName: String, fieldNamesAndTypes: Seq[(String, FieldType.Value)]) =
    apply(collectionName)
}