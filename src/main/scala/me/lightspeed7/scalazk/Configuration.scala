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
import scala.collection.concurrent.TrieMap
import org.apache.zookeeper.data.Stat

case class Configuration(client: ZkClient) {

  final def getValue(key: String, default: String)(implicit ec: ExecutionContext): Future[String] = {
    getValue(key) map { result =>
      result match {
        case Some(value) => new String(value)
        case None        => default
      }
    }
  }

  final def getValue(key: String, default: Long)(implicit ec: ExecutionContext): Future[Long] = {
    getValue(key, default.toString) flatMap { value => WrapInFuture[Long](client) { client => value.toLong } }
  }

  final def getValue(key: String, default: Int)(implicit ec: ExecutionContext): Future[Int] = {
    getValue(key, default.toString) flatMap { value => WrapInFuture[Int](client) { client => value.toInt } }
  }

  final def getValue(key: String, default: Duration)(implicit ec: ExecutionContext): Future[Duration] = {
    val tmp = s"${default.length}|${default.unit}"
    getValue(key, tmp) flatMap { value =>
      WrapInFuture[Duration](client) { cleint =>
        val split = value.split('|')
        Duration(split(0).toLong, TimeUnit.valueOf(split(1)))
      }
    }
  }

  private final def convertOldValue[T](f: => Try[T]): Option[T] = {
    f match {
      case Success(old) => Some(old)
      case Failure(ex)  => None
    }
  }

  final def setValue(key: String, value: Long)(implicit ec: ExecutionContext): Future[Option[Long]] = {
    val old = this.setValue(key, value.toString, true) map { result =>
      result match {
        case None      => None
        case Some(raw) => convertOldValue(Try(raw.toString.toLong))
      }
    }
    old
  }

  final def setValue(key: String, value: Int)(implicit ec: ExecutionContext): Future[Option[Int]] = {
    val old = this.setValue(key, value.toString, true) map { result =>
      result match {
        case None      => None
        case Some(raw) => convertOldValue(Try(raw.toString.toInt))
      }
    }
    old
  }

  final def setValue(key: String, value: Duration)(implicit ec: ExecutionContext): Future[Option[Duration]] = {
    val old = this.setValue(key, s"${value.length}|${value.unit}", true) flatMap { result =>
      result match {
        case None => Future.successful(None)
        case Some(raw) => {
          getValue(key, raw.toString) map { value =>
            val split = value.split('|')
            Try(Duration(split(0).toLong, TimeUnit.valueOf(split(1)))) match {
              case Success(old) => Some(old)
              case Failure(ex)  => None
            }

          }
        }
      }
    }
    old
  }

  def shutdown = client.shutdown

  // impls 
  def getValue(key: String)(implicit ec: ExecutionContext): Future[Option[Array[Byte]]] = {
    val tempKey = if (key startsWith "/") key else "/" + key
    client.exists(tempKey) flatMap { exists =>
      val value = if (!exists) Future successful (None)
      else {
        client.get(tempKey) map { raw =>
          if (raw.length == 0) None
          else new Some(raw)
        }
      }
      value
    }
  }

  def setValue(key: String, value: String, createParents: Boolean = true)(implicit ec: ExecutionContext): Future[Option[Array[Byte]]] = {
    val tempKey = if (key startsWith "/") key else "/" + key

    client.exists(tempKey) flatMap { exists =>
      val oldValue = exists match {
        case true =>
          getValue(tempKey) flatMap { oldValue =>
            client.set(tempKey, value.getBytes()) map { newValue =>
              oldValue
            }
          }
        case false =>
          client.create(tempKey, value.getBytes(), true) map { stat =>
            None
          }

      }
      oldValue
    }
  }

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

// CTOR
object Configuration {

  def initialize(client: ZkClient)(implicit ec: ExecutionContext): Future[Configuration] = Future {
    // make sure our root app path exists, it blocks
    new EnsurePath("/" + client.curator.getNamespace()).ensure(client.getZookeeperClient())

    Configuration(client)
  }

}



