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
import scala.concurrent.Future
import scala.concurrent.ExecutionContext

trait Configuration {

  final def getValue(key: String, default: String)(implicit ec: ExecutionContext): Future[String] = {
    getValue(key) map { result =>
      result match {
        case Some(value) => value
        case None        => setValue(key, default); default
      }
    }
  }

  final def getValue(key: String, default: Long)(implicit ec: ExecutionContext): Future[Long] = {
    getValue(key, default.toString) map { value => value.toLong }
  }

  final def getValue(key: String, default: Int)(implicit ec: ExecutionContext): Future[Int] = {
    getValue(key, default.toString) map { value => value.toInt }
  }

  final def getValue(key: String, default: Duration)(implicit ec: ExecutionContext): Future[Duration] = {
    val tmp = s"${default.length}|${default.unit}"
    getValue(key, tmp) map { result =>
      val split = result.split('|')
      Duration(split(0).toLong, TimeUnit.valueOf(split(1)))
    }
  }

  final def setValue(key: String, value: Long)(implicit ec: ExecutionContext): Future[Option[Long]] = {
    val old = this.setValue(key, value.toString) map { result =>
      result match {
        case None      => None
        case Some(raw) => Some(raw.toLong)
      }
    }
    old
  }

  final def setValue(key: String, value: Int)(implicit ec: ExecutionContext): Future[Option[Int]] = {
    val old = this.setValue(key, value.toString) map { result =>
      result match {
        case None      => None
        case Some(raw) => Some(raw.toInt)
      }
    }
    old
  }

  final def setValue(key: String, value: Duration)(implicit ec: ExecutionContext): Future[Option[Duration]] = {
    val old = this.setValue(key, s"${value.length}|${value.unit}") flatMap { result =>
      result match {
        case None => Future.successful(None)
        case Some(raw) => {
          getValue(key, raw) map { value =>
            val split = value.split('|')
            Some(Duration(split(0).toLong, TimeUnit.valueOf(split(1))).asInstanceOf[Duration] )
          }
        }
      }
    }
    old
  }

  def shutdown() = {}

  // abstracts 
  def getValue(key: String)(implicit ec: ExecutionContext): Future[Option[String]]
  def setValue(key: String, value: String)(implicit ec: ExecutionContext): Future[Option[String]]
}

// CTOR
object Configuration {

  def initialize(client: ZkClient)(implicit ec: ExecutionContext): Future[Configuration] = Future {
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

  override def getValue(key: String)(implicit ec: ExecutionContext): Future[Option[String]] = {
    Future.successful(values.get(key))
  }

  override def setValue(key: String, value: String)(implicit ec: ExecutionContext): Future[Option[String]] = {
    Future.successful(values.put(key, value))
  }
}

case class ZooKeeperConfiguration(client: ZkClient) extends Configuration {

  override def getValue(key: String)(implicit ec: ExecutionContext): Future[Option[String]] = {
    Future {
      val tempKey = if (key startsWith "/") key else "/" + key
      val old = client.checkExists().forPath(tempKey)
      val value = if (null == old) None
      else {
        val raw = client.getData().forPath(tempKey)
        if (raw.length == 0) None else Some(new String(raw))
      }
      value
    }
  }

  override def setValue(key: String, value: String)(implicit ec: ExecutionContext): Future[Option[String]] = {
    val tempKey = if (key startsWith "/") key else "/" + key

    val doesExist = (client.checkExists().forPath(tempKey) != null)

    val oldValue = doesExist match {
      case true =>
        getValue(tempKey) map { oldValue =>
          client.setData().forPath(tempKey, value.getBytes())
          oldValue
        }
      case false => Future {
        client.create().creatingParentsIfNeeded().forPath(tempKey, value.getBytes())
        None
      }
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
    }
    catch { case (e: Exception) => Failure(e) }
  }
}
