package dataaccess

import dataaccess.RepoTypes._
import models.{FieldTypeSpec, FieldTypeId}

trait JsonCrudRepoFactory {
  def apply(
    collectionName: String,
    fieldNamesAndTypes: Seq[(String, FieldTypeSpec)]
  ): JsonCrudRepo
}

trait MongoJsonCrudRepoFactory {
  def apply(
    collectionName: String,
    fieldNamesAndTypes: Seq[(String, FieldTypeSpec)],
    createIndexForProjectionAutomatically: Boolean
  ): JsonCrudRepo
}