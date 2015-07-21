package com.octo.crawler.Actors

import akka.actor.{Actor, Props}
import akka.pattern.ask
import akka.routing.RoundRobinPool
import akka.util.Timeout
import com.octo.crawler.ActorMain

import scala.annotation.tailrec
import scala.collection.mutable
import scala.concurrent.duration._

/**
 * Created by bastien on 05/01/2015.
 */
class URLAggregatorActor extends Actor {

  import URLAggregatorActor._;

  override def receive: Receive = {
    case "print" => printResult()
    case "printErrors" => printErrors()
    case url: String => crawlURLs(Set(url), URLAggregatorActor.depth, "")
    case (requestBody: String, remainingDepth: Int, refererUrl: String, (urlHost: String, urlPort: String, urlProtocolString)) => {
      //TODO passer en fwd
      URLAggregatorActor.parserActor !(requestBody, remainingDepth, refererUrl, (urlHost, urlPort, urlProtocolString))
    }
    case (code: Int, url: String, refererUrl: String) => {
      urlsInError.update(url, (code, refererUrl))
      if (code == -1)
        urlCrawlingDone()
    }
    case (urls: Set[String], remainingDepth: Int, refererUrl: String) => {
      crawlURLs(urls, remainingDepth - 1, refererUrl)
    }
    case x => println( s"""I don't know how to handle ${x}""")
  }

  @tailrec
  private def crawlURLs(urls: Set[String], remainingDepth: Int, refererUrl: String): Unit = {
    urls match {
      case urls: Set[String] if urls.size == 0 => {
        urlCrawlingDone()
      }
      case urls: Set[String] => {
        if (remainingDepth > 0) {
          val haveToCrawl: Boolean = !(crawledUrls contains (urls head))
          if (haveToCrawl) {
            crawlUrl(urls head, remainingDepth, refererUrl)
            inCrawlingUrlNb += 1
          }
          crawlURLs(urls tail, remainingDepth, refererUrl)
        } else {
          crawlURLs(Set(), remainingDepth, refererUrl)
        }
      }
    }
  }

  def urlCrawlingDone(): Unit = {
    crawledUrlNb += 1
    inCrawlingUrlNb -= 1
    if (inCrawlingUrlNb <= 0) {
      sthap()
    }
  }

  def sthap(): Unit = {
    import context.dispatcher
    implicit val timeout = Timeout(15 seconds)
    val future = URLAggregatorActor.displayActor ?("flush", urlsInError)
    future.onComplete(x => {
      println( s"""Number of crawled URLs : ${crawledUrlNb}""")
      println( s"""Number of crawling URLs : ${inCrawlingUrlNb}""")
      println( s"""Speed : ${crawledUrlNb / Math.max(1, (System.currentTimeMillis() - URLAggregatorActor.startTime) / 1000)} url/s""")
      println( s"""Crawling done in : ${(System.currentTimeMillis() - URLAggregatorActor.startTime) / 1000}s""")
      context.system.shutdown()

      ActorMain.running = false
    })
  }

  def crawlUrl(url: String, remainingDepth: Int, refererUrl: String) = {
    URLAggregatorActor.crawlActor !(url, remainingDepth, refererUrl)
    crawledUrls.add(url)
  }

  def printResult(): Unit = {
    println("Start agregating")
    println( s"""Number of crawled URLs : ${crawledUrlNb}""")
    println( s"""Number of crawling URLs : ${inCrawlingUrlNb}""")
    val currentTime = System.currentTimeMillis()
    println( s"""Speed : ${crawledUrlNb / Math.max(1, (currentTime - URLAggregatorActor.startTime) / 1000)} url/s""")
    println( s"""Instant speed : ${(crawledUrlNb - crawledUrlNbLaps) / Math.max(1, (currentTime - timeLaps) / 1000)} url/s""")
    timeLaps = currentTime
    crawledUrlNbLaps = crawledUrlNb
    URLAggregatorActor.displayActor ! crawledUrls
  }

  def printErrors(): Unit = {
    URLAggregatorActor.displayActor !("errors", urlsInError)
  }

  object URLAggregatorActor {
    val depth = System.getProperty("depth").toInt
    val startTime = System.currentTimeMillis()
    var timeLaps = startTime
    val crawlActor = context.actorOf(RoundRobinPool(Runtime.getRuntime().availableProcessors()).props(Props[CrawlActor]), "crawlerRouter")
    val parserActor = context.actorOf(RoundRobinPool(Runtime.getRuntime().availableProcessors()).props(Props[ParserActor]), "parserRouter")
    val displayActor = context.actorOf(RoundRobinPool(1).props(Props[DisplayMapActor]), "displayRouter")

    val urlsInError = mutable.Map[String, (Int, String)]()
    val crawledUrls = mutable.Set[String]()
    var crawledUrlNb = 0
    var crawledUrlNbLaps = 0
    var inCrawlingUrlNb = 1

  }

}
