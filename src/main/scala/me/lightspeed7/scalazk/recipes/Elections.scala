package me.lightspeed7.scalazk.recipes

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{ Try, Failure, Success }

import org.apache.curator.framework.CuratorFramework
import org.apache.curator.framework.recipes.leader._
import org.apache.curator.framework.recipes.leader.LeaderLatch.CloseMode

import me.lightspeed7.scalazk.WrapInFuture
import me.lightspeed7.scalazk.ZkClient

object Elections {

  // 
  // Leader Election 
  // //////////////////////////////
  private class InternalLeaderClass[T](client: ZkClient)(f: ZkClient => Unit) extends LeaderSelectorListenerAdapter {
    def takeLeadership(curator: CuratorFramework) { f(client) }
  }

  def leaderElection[T](client: ZkClient, path: String, autoRequeue: Boolean)(f: ZkClient => Unit)(implicit ec: ExecutionContext): Unit = {
    WrapInFuture.andCatch(client) { client =>
      val listener = new InternalLeaderClass(client)(f)
      val selector = new LeaderSelector(client.curator, path, listener)
      if (autoRequeue) selector.autoRequeue()
      selector.start()
    }
  }

  //
  // Leader Latch
  // ////////////////////////////////
  def leaderLatch[T](client: ZkClient, path: String, participantId: Option[String] = None, closeMode: CloseMode = CloseMode.SILENT)(timeout: Duration)(f: ZkClient => T)(implicit ec: ExecutionContext): Future[Try[T]] = {
    WrapInFuture.andFail(client) { client =>
      val latch = participantId match {
        case Some(id) => new LeaderLatch(client.curator, path, id, closeMode)
        case None     => new LeaderLatch(client.curator, path, "", closeMode)
      }
      latch.start()
      Try { // block here until we acquire it
        if (timeout.length == 0) { latch.await(); true }
        else latch.await(timeout.length, timeout.unit)
      } match {
        case f: Failure[T]     => { println("Failure"); f }
        case Success(isLeader) => if (isLeader) Try(f(client)) else Failure(new IllegalStateException("I am not the leader"))
      }
    }
  }

}