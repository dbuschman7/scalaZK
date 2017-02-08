package me.lightspeed7.scalazk

import scala.concurrent.Await
import scala.concurrent.duration._
import me.lightspeed7.scalazk.ZkClient._
import scala.util.Random

trait TestHelper {
  lazy val baseDir = "/" + Random.alphanumeric(10)
  lazy val timeout = 10 seconds
  lazy val client: ZkClient = Await.result(ZkClientBuilder(Seq("services.local")).namespace("configuration-unit-test").build, timeout)
}