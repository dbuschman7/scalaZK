package me.lightspeed7.scalazk

import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import scala.util.Try
import scala.util.Success
import scala.util.Failure

object WrapInFuture {

  //
  // future wrapper
  // //////////////////////////
  def andCatch[T](client: ZkClient)(f: ZkClient => T)(implicit ec: ExecutionContext): Future[Try[T]] = {
    Future { Try { f(client) } }
  }

  def andFail[T](client: ZkClient)(f: ZkClient => T)(implicit ec: ExecutionContext): Future[T] = {
    Try { f(client) } match {
      case Success(s)  => Future.successful(s)
      case Failure(ex) => Future.failed(ex)
    }
  }
}