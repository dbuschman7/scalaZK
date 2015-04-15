package me.lightspeed7.scalazk

import org.apache.curator.framework.CuratorFramework
import scala.collection.JavaConverters.asScalaBufferConverter

case class Entry(level: Int, path: String, name: String, value: String)

case class ZooKeeperTree(client: ZkClient) {

  def displayTree(f: Entry => Unit): Unit = displayTree(0, "", "root")(f)

  def displayTree(level: Int, path: String, name: String)(f: Entry => Unit): Unit = {

    val lookup = if (path.isEmpty()) "/" else path

    val raw = client.getData().forPath(lookup)
    val value = if( raw == null)  "" else new String(raw)
    f(Entry(level, lookup, name, value))

    // children
    import scala.collection.JavaConverters._
    client.getChildren().forPath(lookup).asScala.map { child =>
      displayTree(level + 1, path + "/" + child, child)(f)
    }
  }

}

object ZooKeeperTree {}