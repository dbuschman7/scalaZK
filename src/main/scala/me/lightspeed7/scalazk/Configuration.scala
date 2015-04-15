package me.lightspeed7.scalazk
import java.util.concurrent.Callable

import java.util.concurrent.TimeUnit

import scala.collection.mutable.HashMap
import scala.collection.mutable.Map
import scala.collection.mutable.SynchronizedMap
import scala.concurrent.duration._
import scala.util._
import scala.util.Success
import scala.util.Try

import org.apache.curator.RetryLoop
import org.apache.curator.utils.EnsurePath

trait Configuration {

  final def getValue(key: String, default: String): String = {
    getValue(key) match {
      case Some(value) => value
      case None => setValue(key, default); default
    }
  }

  final def getValue(key: String, default: Long): Long = {
    getValue(key, default.toString).toLong
  }

  final def getValue(key: String, default: Int): Int = {
    getValue(key, default.toString).toInt
  }

  final def getValue(key: String, default: Duration): FiniteDuration = {
    val tmp = s"${default.length}|${default.unit}"
    val split = getValue(key, tmp).split('|')
    Duration(split(0).toLong, TimeUnit.valueOf(split(1)))
  }

  final def setValue(key: String, value: Long): Option[Long] = {
    val old = this.setValue(key, value.toString) match {
      case None => None
      case Some(raw) => Some(raw.toLong)
    }
    old
  }

  final def setValue(key: String, value: Int): Option[Int] = {
    val old = this.setValue(key, value.toString) match {
      case None => None
      case Some(raw) => Some(raw.toInt)
    }
    old
  }

  final def setValue(key: String, value: Duration): Option[Duration] = {
    val old = this.setValue(key, s"${value.length}|${value.unit}") match {
      case None => None
      case Some(raw) => {
        val split = getValue(key, raw).split('|')
        Some(Duration(split(0).toLong, TimeUnit.valueOf(split(1))))
      }
    }
    old
  }

  def shutdown() = {}

  // abstracts 
  def getValue(key: String): Option[String]
  def setValue(key: String, value: String): Option[String]
}

// CTOR
object Configuration {

  def initialize(client: ZkClient): Configuration = {
    // make sure our root app path exists, it blocks
    new EnsurePath("/" + client.curator.getNamespace()).ensure(client.getZookeeperClient())

    ZooKeeperConfiguration(client)
  }

}

// handle environment level values
trait EnvironmentConfiguration {

  private lazy val envVars = readEnvironment()

  private def readEnvironment(): Map[String, String] = {
    import collection.JavaConversions._
    val m = new HashMap[String, String] with SynchronizedMap[String, String]
    System.getenv() foreach { e => m.put(e._1, e._2) } // initial values
    m
  }

  def getEnvValue(key: String, default: Boolean): Boolean = {
    val raw = System.getenv(key)
    if (raw == null || raw.length() == 0) default
    else "true".equals(raw.toLowerCase())
  }

  def setEnvValue(key: String, value: String): Option[String] = {
    envVars.put(key, value)
  }

}

case class MemoryConfiguration() extends Configuration {

    
  lazy val values = new HashMap[String, String] with SynchronizedMap[String, String]

  override def getValue(key: String): Option[String] = {
    values.get(key)
  }

  override def setValue(key: String, value: String): Option[String] = {
    values.put(key, value)
  }
}

case class ZooKeeperConfiguration(client: ZkClient) extends Configuration {

  override def getValue(key: String): Option[String] = {
    val tempKey = if (key startsWith "/") key else "/" + key
    val old = client.checkExists().forPath(tempKey)
    val value = if (null == old) None
    else {
      val raw = client.getData().forPath(tempKey)
      if (raw.length == 0) None else Some(new String(raw))
    }
    value
  }

  override def setValue(key: String, value: String): Option[String] = {
    val tempKey = if (key startsWith "/") key else "/" + key

    val oldValue = (client.checkExists().forPath(tempKey) != null) match {
      case true =>
        val oldValue = getValue(tempKey)
        client.setData().forPath(tempKey, value.getBytes())
        oldValue
      case false =>
        client.create().creatingParentsIfNeeded().forPath(tempKey, value.getBytes())
        None
    }
    oldValue
  }

  override def shutdown = client.shutdown

  def callWithRetry[T](f: ZkClient => T): Try[T] = {
    try {
      val result = RetryLoop.callWithRetry(client.curator.getZookeeperClient(), new Callable[T] {
        override def call(): T = {
          f(client)
        }
      })
      Success(result)
    } catch { case (e: Exception) => Failure(e) }
  }
}
