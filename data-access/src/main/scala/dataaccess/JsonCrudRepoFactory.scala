package dataaccess

import dataaccess.RepoTypes._
import dataaccess.FieldTypeId

trait JsonCrudRepoFactory {
  def apply(collectionName: String): JsonCrudRepo

  // by default call apply
  def applyWithDictionary(collectionName: String, fieldNamesAndTypes: Seq[(String, FieldTypeId.Value)]) =
    apply(collectionName)
}