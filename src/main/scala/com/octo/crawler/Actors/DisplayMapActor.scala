package com.octo.crawler.Actors

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}

import akka.actor.Actor

import scala.collection.mutable


/**
 * Created by bastien on 06/01/2015.
 */
class DisplayMapActor extends Actor {
  override def receive: Receive = {
    case map: mutable.Set[String] => {
      // println(map)
    }
    case ("errors", map: mutable.Map[String, (Int, String)]) => {
      val sortedList: List[(Int, String, String)] = map.foldLeft(List[(Int, String, String)]()) { case (a, (k, (v, g))) => a :+(v, k, g) }.sortBy(elem => elem._1 * -1)
      println(sortedList)
    }
    case ("flush", map: mutable.Map[String, (Int, String)]) => {
      val csv = map.foldLeft(List[(Int, String, String)]()) { case (a, (k, (v, g))) => a :+(v, k, g) }.sortBy(elem => elem._1 * -1).foldLeft(StringBuilder.newBuilder.append("error,url,first_referer\n")) { case (a, elem) => a.append(elem._1).append(",").append(elem._2).append(",").append(elem._3).append("\n") }.toString()
      println(csv)
      Files.write(Paths.get(DisplayMapActor.csvResultPath), csv.getBytes(StandardCharsets.UTF_8))
      sender ! "OK"
    }
  }
}

object DisplayMapActor {
  val csvResultPath = System.getProperty("csvResultPath")
}
