package util

import java.security.MessageDigest

import be.objectify.deadbolt.scala.DeadboltActions
import models.security.SecurityRole
import play.api.http.{Status => HttpStatus}
import play.api.mvc.{Action, Result}
import play.api.mvc.Results._
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Future
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

  def restrictSubjectPresent[A](deadbolt: DeadboltActions)(action: Action[A]): Action[A] =
    deadbolt.SubjectPresent()(action)

  def restrictChain[A](
    restrictions: Seq[Action[A] => Action[A]]
  ) = restrictChainFuture(
    restrictions.map {
      restriction => action: Action[A] => Future(restriction(action))
    }
  )(_)

  def restrictChainFuture[A](
    restrictions: Seq[Action[A] => Future[Action[A]]])(
    action: Action[A]
  ): Action[A] =
    Action.async(action.parser) { implicit request =>
      def isAuthorized(result: Result) = result.header.status != HttpStatus.UNAUTHORIZED

      val authorizedResultFuture =
        restrictions.foldLeft(
          Future((false, BadRequest("No restriction found")))
        ) {
          (resultFuture, restriction) =>
            for {
              (authorized, result) <- resultFuture

              nextResult <-
                if (authorized)
                  Future((authorized, result))
                else
                  restriction(action).flatMap {
                    _.apply(request).map( newResult =>
                      (isAuthorized(newResult), newResult)
                    )
                  }
            } yield {
              nextResult
            }
        }

      authorizedResultFuture.map { case (_, result) => result }
    }
}