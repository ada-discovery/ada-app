package util

import java.security.MessageDigest

import be.objectify.deadbolt.scala.{AuthenticatedRequest, DeadboltActions, DeadboltHandler, SubjectActionBuilder}
import controllers.core.WithNoCaching
import controllers.dataset.ControllerName
import models.security.SecurityRole
import play.api.http.{Status => HttpStatus}
import play.api.mvc.BodyParsers.parse
import play.api.mvc._
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
    controllerName: ControllerName.Value,
    actionName: String
  ) = "\\bDS:" + dataSetId.replaceAll("\\.","\\\\.") + "(\\." + controllerName.toString + "(\\." + actionName + ")?)?\\b"

  type AuthenticatedAction[A] =
    AuthenticatedRequest[A] => Future[Result]

  def restrictAdmin[A](
    deadbolt: DeadboltActions,
    bodyParser: BodyParser[A])(
    action: AuthenticatedAction[A]
  ): Action[A] =
    deadbolt.Restrict[A](List(Array(SecurityRole.admin)))(bodyParser)(action)

  def restrictAdminAny(
    deadbolt: DeadboltActions)(
    action: AuthenticatedAction[AnyContent]
  ): Action[AnyContent] =
    restrictAdmin(deadbolt, parse.anyContent)(action)

  def restrictAdminNoCaching[A](
    deadbolt: DeadboltActions,
    bodyParser: BodyParser[A])(
    action: AuthenticatedAction[A]
  ): Action[A] = WithNoCaching (
    restrictAdmin(deadbolt, bodyParser)(action)
  )

  def restrictAdminAnyNoCaching(
    deadbolt: DeadboltActions)(
    action: AuthenticatedAction[AnyContent]
  ): Action[AnyContent] =
    restrictAdminNoCaching(deadbolt, parse.anyContent)(action)

  def restrictSubjectPresent[A](
    deadbolt: DeadboltActions,
    bodyParser: BodyParser[A])(
    action: AuthenticatedAction[A]
  ): Action[A] =
    deadbolt.SubjectPresent[A]()(bodyParser)(action)

  def restrictSubjectPresentNoCaching[A](
    deadbolt: DeadboltActions,
    bodyParser: BodyParser[A])(
    action: AuthenticatedAction[A]
  ): Action[A] =
    WithNoCaching(
      restrictSubjectPresent(deadbolt, bodyParser)(action)
    )

  def restrictSubjectPresentAny(
    deadbolt: DeadboltActions)(
    action: AuthenticatedAction[AnyContent]
  ): Action[AnyContent] =
    restrictSubjectPresent(deadbolt, parse.anyContent)(action)

  def restrictSubjectPresentAnyNoCaching(
    deadbolt: DeadboltActions)(
    action: AuthenticatedAction[AnyContent]
  ): Action[AnyContent] =
    WithNoCaching(
      restrictSubjectPresentAny(deadbolt)(action)
    )

  def toAuthenticatedAction[A](action: Action[A]): AuthenticatedAction[A] = {
    implicit request => action.apply(request)
  }

  def toAction[A](bodyParser: BodyParser[A])(action: AuthenticatedAction[A]): Action[A] = Action.async(bodyParser) {
    implicit request => action.apply(new AuthenticatedRequest[A](request, None))
  }

  def toActionAny(action: AuthenticatedAction[AnyContent]): Action[AnyContent] = toAction(BodyParsers.parse.anyContent)(action)

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

  def restrictChain2(
    restrictions: Seq[AuthenticatedAction[AnyContent] => Action[AnyContent]]
  ) = restrictChainFuture2(
    restrictions.map {
      restriction => action: AuthenticatedAction[AnyContent] => Future(restriction(action))
    }
  )(_)

  def restrictChainFuture2(
    restrictions: Seq[AuthenticatedAction[AnyContent] => Future[Action[AnyContent]]])(
    action: AuthenticatedAction[AnyContent]
  ): Action[AnyContent] =
    Action.async { implicit request => // action.parser)
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