package util

import java.security.MessageDigest

import models.security.CustomUser
import play.api.data.validation.{Invalid, Valid, ValidationError, Constraint}
import reactivemongo.bson.BSONObjectID

import scala.collection.immutable.IndexedSeq
import scala.util.matching.Regex


/**
  * Utils for password security.
  */
object SecurityUtil {

  /**
    * Calculate md5 (e.g. for encrypting passwords).
    * @param in input String.
    * @return hashed string
    */
  def md5(in: String) : String = {
    if(in.isEmpty){
      new String("")
    }else{
      val md: Array[Byte] = MessageDigest.getInstance("MD5").digest(in.getBytes)
      new String(md)
    }
  }

  /**
    * Calculate sha-1 (e.g. for encrypting passwords).
    * @param in input String.
    * @return hashed string
    */
  def sha1(in: String) : String = {
    if(in.isEmpty){
      new String("")
    }else{
      val md: Array[Byte] = MessageDigest.getInstance("SHA-1").digest(in.getBytes)
      new String(md)
    }
  }

  /**
    * Create CustomUser object, with password hashed.
    * @return CusotmUser with hashed password.
    */
  def secureUserApply(_id: Option[BSONObjectID], name: String, email: String, password: String, affiliation: String, roles: Seq[String], permissions: Seq[String]): CustomUser = {
    CustomUser(_id, name, email, md5(password), affiliation, roles, permissions)
  }

  /**
    * Extract information from user and hide the password hash.
    * @param user CustomUser for extraction.
    * @return Tuple with extracted user properties, password is hashed.
    */
  def secureUserUnapply(user: CustomUser): Option[(Option[BSONObjectID], String, String, String, String, Seq[String], Seq[String])] = {
    val hidden: String = user.password.map(_ => '*')
    Some((user._id, user.name, user.email, hidden, user.affiliation, user.roles, user.permissions))
  }
}
