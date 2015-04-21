package me.lightspeed7.scalazk

import org.apache.zookeeper.KeeperException
import org.apache.zookeeper.data.Stat

object Implicits {

  // Java to Scala to Java conversions
  implicit def toJava[T](in: Seq[T]): java.util.List[T] = scala.collection.JavaConversions.seqAsJavaList(in)
  implicit def toScala[T](in: java.util.List[T]): Seq[T] = scala.collection.JavaConversions.collectionAsScalaIterable(in).toSeq

  // sugar for options 
  implicit def toGetBytes[T](in: T): Array[Byte] = if (in == null) null else in.toString.getBytes
  implicit def stringToSome(in: String): Option[String] = if (in == null) None else Some(in)
  implicit def intToSome(in: Int): Option[Int] = Some(in)
  implicit def statToSome(in: Stat): Option[Stat] = if (in == null) None else Some(in)

  implicit def keeperToSome(k: KeeperException): Option[KeeperException] = if (k == null) None else Some(k)
}