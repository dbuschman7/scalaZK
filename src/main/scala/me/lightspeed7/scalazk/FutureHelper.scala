package me.lightspeed7.scalazk

import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import scala.util.Try
import scala.util.Success
import scala.util.Failure
import scala.concurrent.Promise

object WrapInFuture {

  //
  // future wrapper
  // //////////////////////////
  def apply[T](client: ZkClient)(f: ZkClient => T)(implicit ec: ExecutionContext): Future[T] = {
    val p = Promise[T]
    Future {
      Try(f(client)) match {
        case Success(result) => p success (result)
        case Failure(ex)     => p failure (ex)
      }
    }
    p.future
  }

}