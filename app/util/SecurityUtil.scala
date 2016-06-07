package util

import java.security.MessageDigest

import be.objectify.deadbolt.scala.DeadboltActions
import models.security.{SecurityRole, CustomUser}
import play.api.mvc.Action
import scala.util.Random

/**
  * Utils for password security.
  */
object SecurityUtil {

  /**
    * Calculate md5 (e.g. for encrypting passwords).
    *
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
    *
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
    * Generate random sequence of alphanumerics.
    * Useful for random password generation.
    *
    * @param length Number of characters in resulting sequence
    * @return Random alphanumeric String
    */
  def randomString(length: Int): String = {
    val rand = Random.alphanumeric
    rand.take(length).mkString
  }

  def createDataSetPermission(
    dataSetId: String,
    controllerName: String,
    actionName: String
  ) = "\\bDS:" + dataSetId.replaceAll("\\.","\\\\.") + "(\\." + controllerName + "(\\." + actionName + ")?)?\\b"

  def restrictAdmin[A](deadbolt: DeadboltActions)(action: Action[A]): Action[A] =
    deadbolt.Restrict[A](List(Array(SecurityRole.admin)))(action)
}