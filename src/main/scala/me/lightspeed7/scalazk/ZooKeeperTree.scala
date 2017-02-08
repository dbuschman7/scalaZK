package me.lightspeed7.scalazk

import org.apache.curator.framework.CuratorFramework
import scala.collection.JavaConverters.asScalaBufferConverter
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.util.Success
import scala.util.Failure
import scala.concurrent.Await
import scala.concurrent.duration.Duration

case class Entry(level: Int, path: String, name: String, value: String)

class ZooKeeperTree(client: ZkClient, path: String = "")(implicit ec: ExecutionContext) {

  def displayTree(name: String, timeout: Duration)(f: Entry => Unit): Unit = displayTree(0, path, name, timeout)(f)

  /**
   * This method is currently synchronous since it is only for development purposes. It may move to asynch if needed in the future
   */
  def displayTree(level: Int, path: String, name: String, timeout: Duration)(f: Entry => Unit): Unit = {

    val lookup: String = if (path.startsWith("/")) path else "/" + path
    val raw = Await.result(client.get(lookup), timeout)

    val value = if (raw == null) "" else new String(raw)
    f(Entry(level, lookup, name, value))

    // children
    val children = Await.result(client.children(lookup), timeout)
    children.toList.sortBy(identity) foreach { child =>
      displayTree(level + 1, path + "/" + child, child, timeout)(f)
    }
  }

}

