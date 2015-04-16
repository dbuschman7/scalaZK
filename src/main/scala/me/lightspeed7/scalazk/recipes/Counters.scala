package me.lightspeed7.scalazk.recipes

import me.lightspeed7.scalazk.ZkClient
import scala.concurrent.ExecutionContext
import org.apache.curator.framework.recipes.shared.SharedCount
import scala.concurrent.Future
import org.apache.curator.framework.recipes.shared.VersionedValue
import org.apache.curator.framework.recipes.shared.SharedCountListener
import org.apache.curator.framework.CuratorFramework
import org.apache.curator.framework.recipes.shared.SharedCountReader
import org.apache.curator.framework.state.ConnectionState
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

object Counters {

  //
  // SharedCounter
  // /////////////////////////////
  private class InternalSharedCountListenerClass[T](client: ZkClient, state: AtomicReference[ConnectionState])(f: Int => Unit) extends SharedCountListener {
    def countHasChanged(sharedCount: SharedCountReader, newCount: Int) = f(newCount)
    def stateChanged(client: CuratorFramework, newState: ConnectionState): Unit = state.set(newState)
  }

  class NotConnectedException(state: ConnectionState) extends Exception(s"ConnectionState = ${state}")

  protected case class SharedCounter(client: ZkClient, counter: SharedCount)(implicit ec: ExecutionContext) {

    val state: AtomicReference[ConnectionState] = new AtomicReference(ConnectionState.CONNECTED)

    def getCount(): Future[Int] = {
      if (state.get().isConnected())
        Future { counter.getCount() }
      else
        Future.failed(new NotConnectedException(state.get))
    }

    def getVersionedValue(): Future[VersionedValue[Integer]] = {
      if (state.get().isConnected())
        Future { counter.getVersionedValue }
      else
        Future.failed(new NotConnectedException(state.get))
    }

    def setCount(newCount: Int): Future[Unit] = {
      if (state.get().isConnected())
        Future { counter.setCount(newCount) }
      else
        Future.failed(new NotConnectedException(state.get))
    }

    def trySetCount(versionedValue: VersionedValue[Integer], newCount: Int): Future[Boolean] = {
      if (state.get().isConnected())
        Future { counter.trySetCount(versionedValue, newCount) }
      else
        Future.failed(new NotConnectedException(state.get))
    }

    def addListener(f: Int => Unit) = counter.addListener(new InternalSharedCountListenerClass[Int](client, state)(f))

    def close(): Unit = {
      if (state.get().isConnected())
        Future { counter.close() }
      else
        Future.failed(new NotConnectedException(state.get))
    }
  }

  def sharedCounter(client: ZkClient, path: String, seed: Int)(implicit ec: ExecutionContext): Future[SharedCounter] = Future {
    val internal = new SharedCount(client.curator, path, seed)
    internal.start()
    SharedCounter(client: ZkClient, internal)
  }

  // TODO 
  // Distributed Atomic Long -> DistributedAtomicLong
}