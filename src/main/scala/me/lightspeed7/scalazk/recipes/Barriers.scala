package me.lightspeed7.scalazk.recipes

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Try
import scala.util.Failure
import scala.util.Success
import org.apache.curator.framework.recipes.barriers.DistributedBarrier

import me.lightspeed7.scalazk.ZkClient

object Barriers {

  //
  // DistributedBarrier
  // /////////////////////////////
  protected case class Barrier[T](barrier: DistributedBarrier)(implicit ec: ExecutionContext) {
    def removeBarrier(): Try[Unit] = { Try[Unit](barrier.removeBarrier()) }

    def waitOnBarrier(f: DistributedBarrier => T): Future[Try[T]] = {
      waitOnBarrier(0 seconds)(f)
    }

    def waitOnBarrier(timeout: Duration)(f: DistributedBarrier => T): Future[Try[T]] = Future {
      Try(f(barrier))
    }
  }

  def distributedBarrier[T](client: ZkClient, path: String)(implicit ec: ExecutionContext): Barrier[T] = {
    Barrier(new DistributedBarrier(client.curator, path))
  }

  // TODO 
  // Double Barrier -> DistributedDoubleBarrier

}