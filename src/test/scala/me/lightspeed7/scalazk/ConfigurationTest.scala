package me.lightspeed7.scalazk

import java.util.concurrent.TimeUnit

import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration

import org.scalatest.FunSuite
import org.scalatest.Matchers._

import me.lightspeed7.scalazk._

class ConfigurationTest extends FunSuite with TestHelper {

  test("duration should have distinct units") {
    val test1 = 5 minutes
    val unit = test1.unit
    val length = test1.length
    println(s"Length is $length, Unit is $unit")
    val test2 = FiniteDuration.apply(length, TimeUnit.valueOf(unit.toString()))
    assert(test1 == test2)
  }

  test("MemoryConfiguration should save and restore values") {
    testSetAndGetOnConfig(new MemoryConfiguration())
  }

  test("ZK Configuration  should store and recall values, and leave cleaned up") {
    testSetAndGetOnConfig(ZooKeeperConfiguration(client))

    val actual = dumpZKTree(client)
    val expected = """+ root = 
|  + dir1 = 
|  |  + dir2 = 
|  |  |  + value = 1234
|  + duration = 5|HOURS
|  + long = 1234
|  + int = 123
|  + string = value
"""

    actual should be(expected)
  }


  //
  // Private methods
  // /////////////////////////////
  def testSetAndGetOnConfig(config: Configuration) = {
    config.setValue("int", 123)
    config.setValue("long", 1234L)
    config.setValue("duration", 5 hours)
    config.setValue("string", "value")

    config.setValue("/dir1/dir2/value", 1234L)

    // test
    123 should be(config.getValue("int", 321))
    1234L should be(config.getValue("long", 4321L))
    (5 hours).toString should be(config.getValue("duration", 2 seconds).toString)
    "value" should be(config.getValue("string", "default"))
    1234L should be(config.getValue("/dir1/dir2/value", 54321L))
  }

  def dumpZKTree(client: ZkClient): String = {
    def pad(level: Int, buf: String): String = {
      level match {
        case 0 => buf
        case _ => pad(level - 1, buf + "|  ")
      }
    }

    val buf: StringBuffer = new StringBuffer
    ZooKeeperTree(client).displayTree({ e => buf.append(s"${pad(e.level, "")}+ ${e.name} = ${new String(e.value)}\n") })
    buf.toString()
  }
}