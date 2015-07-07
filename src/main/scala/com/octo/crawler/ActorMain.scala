package com.octo.crawler

import akka.actor.{ActorSystem, Props}
import com.octo.crawler.Actors.URLAggregatorActor

import scala.io.StdIn.readLine

/**
 * Created by bastien on 05/01/2015.
 */
object ActorMain {

  var running: Boolean = true

  def main(args: Array[String]) {
    val system = ActorSystem("CrawlerSystem")

    val urlAggregator = system.actorOf(Props[URLAggregatorActor], name = "aggregator")
    urlAggregator ! System.getProperty("startUrl")
    if (System.getProperty("notTTY") == null)
      while (running) {
        urlAggregator ! readLine()
      }
  }
}
