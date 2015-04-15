package me.lightspeed7.scalazk

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.Duration
import scala.concurrent.Future
import org.apache.curator.framework.recipes.locks.InterProcessMutex
import scala.util.Try
import org.apache.curator.framework.recipes.leader.LeaderSelectorListenerAdapter
import org.apache.curator.framework.recipes.leader.LeaderSelectorListener
import org.apache.curator.framework.CuratorFramework
import org.apache.curator.framework.recipes.leader.LeaderSelector
import scala.util.Success

object Recipes {

  //
  // Distributed Lock 
  // ///////////////////////////////
  def distributedLock[T](client: ZkClient, path: String, timeout: Duration)(f: ZkClient => T)(implicit ec: ExecutionContext): Future[Try[T]] = {
    wrapInFutureAndCatch(client) { client =>
      val lock: InterProcessMutex = new InterProcessMutex(client.curator, path)
      try {
        if (lock.acquire(timeout.length, timeout.unit)) f(client)
        else throw new IllegalAccessException("Unable to acquire lock for path '${path}'")
      }
      finally {
        lock.release()
      }
    }
  }

  // 
  // Leader Election 
  // //////////////////////////////
  private case class ResultException[T](result: T) extends Exception {}

  private class InternalLeaderClass[T](client: ZkClient)(f: ZkClient => Unit) extends LeaderSelectorListenerAdapter {
    def takeLeadership(curator: CuratorFramework) { f(client) }
  }

  def leaderElection[T](client: ZkClient, path: String, autoRequeue: Boolean)(f: ZkClient => Unit)(implicit ec: ExecutionContext): Unit = {
    wrapInFutureAndCatch(client) { client =>
      val listener = new InternalLeaderClass(client)(f)
      val selector = new LeaderSelector(client.curator, path, listener)
      if (autoRequeue) selector.autoRequeue()
      selector.start()
    }
  }

  //
  // internal
  // //////////////////////////
  private def wrapInFutureAndCatch[T](client: ZkClient)(f: ZkClient => T)(implicit ec: ExecutionContext): Future[Try[T]] = {
    Future { Try { f(client) } }
  }
}