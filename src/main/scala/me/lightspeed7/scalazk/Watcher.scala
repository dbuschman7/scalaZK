package me.lightspeed7.scalazk

import org.apache.zookeeper.WatchedEvent
import org.apache.curator.framework.api.CuratorListener
import org.apache.curator.framework.CuratorFramework
import org.apache.curator.framework.api.CuratorEvent
import scala.collection.concurrent.TrieMap
import java.util.UUID
import java.util.Random
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import scala.util.Try

object Watcher {

  case class ZkWatchedEvent(context: WatcherContext, event: CuratorEvent)

  case class WatcherContext(client: ZkClient)(f: ZkWatchedEvent => Unit) extends CuratorListener {
    def eventReceived(curator: CuratorFramework, event: CuratorEvent): Unit = {
      f(ZkWatchedEvent(this, event))
    }

    def removeListener: Unit = client.curator.getCuratorListenable().removeListener(this);
  }

  //
  // public API
  // ///////////////////////
  def addWatcher(client: ZkClient, path: String)(f: ZkWatchedEvent => Unit)(implicit ec: ExecutionContext): Future[WatcherContext] = {
    client.ensurePath(path) map { success =>
      val listener = WatcherContext(client)(f)
      client.curator.getCuratorListenable().addListener(listener)
      client.curator.getChildren().watched().forPath(path)
      listener
    }
  }

}