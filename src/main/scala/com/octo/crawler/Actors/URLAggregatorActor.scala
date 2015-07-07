package com.octo.crawler.Actors

import akka.actor.{Actor, Props}
import akka.routing.RoundRobinPool
import com.octo.crawler.ActorMain
import akka.pattern.ask

import scala.annotation.tailrec
import scala.concurrent.Await
import akka.util.Timeout
import scala.concurrent.duration._

/**
 * Created by bastien on 05/01/2015.
 */
class URLAggregatorActor extends Actor {

  override def receive: Receive = {
    case url: String => crawlURLs(Set(url), URLAggregatorActor.depth, "", Map[String, (Int, String)](), Set[String](), 0, 1)
    case x => println( s"""I don't know how to handle ${x}""")
  }

  def running(urlsInError: Map[String, (Int, String)], crawledUrls: Set[String], crawledUrlNb: Int, inCrawlingUrlNb: Int): Receive = {
    case "print" => printResult(crawledUrls, crawledUrlNb, inCrawlingUrlNb)
    case "printErrors" => printErrors(urlsInError)
    case (requestBody: String, remainingDepth: Int, refererUrl: String, (urlHost: String, urlPort: String, urlProtocolString)) => {
      //TODO passer en fwd
      URLAggregatorActor.parserActor !(requestBody, remainingDepth, refererUrl, (urlHost, urlPort, urlProtocolString))
    }
    case (code: Int, url: String, refererUrl: String) if code == -1 => {
      urlCrawlingDone(urlsInError.updated(url, (code, refererUrl)), crawledUrls, crawledUrlNb, inCrawlingUrlNb)
    }
    case (code: Int, url: String, refererUrl: String) => {
      context.become(running(urlsInError.updated(url, (code, refererUrl)), crawledUrls, crawledUrlNb, inCrawlingUrlNb))
    }
    case (urls: Set[String], remainingDepth: Int, refererUrl: String) => {
        crawlURLs(urls, remainingDepth - 1, refererUrl, urlsInError, crawledUrls, crawledUrlNb, inCrawlingUrlNb)
    }
    case x => println( s"""I don't know how to handle ${x}""")
  }

  @tailrec
  private def crawlURLs(urls: Set[String], remainingDepth: Int, refererUrl: String, urlsInError: Map[String, (Int, String)], crawledUrls: Set[String], crawledUrlNb: Int, inCrawlingUrlNb: Int): Unit = {
    urls match {
      case urls: Set[String] if urls.size == 0 => {
        urlCrawlingDone(urlsInError, crawledUrls, crawledUrlNb, inCrawlingUrlNb)
      }
      case urls: Set[String] => {
        if (remainingDepth > 0) {
          val haveToCrawl: Boolean = !(crawledUrls contains (urls head))
          if (haveToCrawl) {
            crawlUrl(urls head, remainingDepth, refererUrl)
          }
          crawlURLs(urls tail, remainingDepth, refererUrl, urlsInError, crawledUrls + (urls head), crawledUrlNb,
            if (haveToCrawl) inCrawlingUrlNb + 1 else inCrawlingUrlNb)
        } else {
          crawlURLs(Set(), remainingDepth, refererUrl, urlsInError, crawledUrls, crawledUrlNb, inCrawlingUrlNb)
        }
      }
    }
  }

  def urlCrawlingDone(urlsInError: Map[String, (Int, String)], crawledUrls: Set[String], crawledUrlNb: Int, inCrawlingUrlNb: Int): Unit = {
    context.become(running(urlsInError, crawledUrls, crawledUrlNb + 1, inCrawlingUrlNb - 1))
    if (inCrawlingUrlNb <= 1) {
      sthap(urlsInError, crawledUrlNb + 1, inCrawlingUrlNb - 1)
    }
  }

  def sthap(result: Map[String, (Int, String)], numberOfCrawledURL:Int, numberOfInCrawlingURL:Int): Unit = {
    import context.dispatcher
    implicit val timeout = Timeout(15 seconds)
    val future = URLAggregatorActor.displayActor ? ("flush", result)
    future.onComplete(x => {
      println( s"""Number of crawled URLs : ${numberOfCrawledURL}""")
      println( s"""Number of crawling URLs : ${numberOfInCrawlingURL}""")
      println( s"""Speed : ${numberOfCrawledURL / ((System.currentTimeMillis() - URLAggregatorActor.startTime) / 1000)} url/s""")
      context.system.shutdown()
      ActorMain.running = false
      System.exit(0);
    })
  }

  def crawlUrl(url: String, remainingDepth: Int, refererUrl: String) = {
    URLAggregatorActor.crawlActor !(url, remainingDepth, refererUrl)
  }

  def printResult(result: Set[String], numberOfCrawledURL: Int, numberOfInCrawlingURL: Int): Unit = {
    println("Start agregating")
    println( s"""Number of crawled URLs : ${numberOfCrawledURL}""")
    println( s"""Number of crawling URLs : ${numberOfInCrawlingURL}""")
    println( s"""Speed : ${numberOfCrawledURL / ((System.currentTimeMillis() - URLAggregatorActor.startTime) / 1000)} url/s""")
    URLAggregatorActor.displayActor ! result
  }

  def printErrors(result: Map[String, (Int, String)]): Unit = {
    URLAggregatorActor.displayActor !("errors", result)
  }

  object URLAggregatorActor {
    val depth = System.getProperty("depth").toInt
    val startTime = System.currentTimeMillis()
    val crawlActor = context.actorOf(RoundRobinPool(16).props(Props[CrawlActor]), "crawlerRouter")
    val parserActor = context.actorOf(RoundRobinPool(Runtime.getRuntime().availableProcessors()).props(Props[ParserActor]), "parserRouter")
    val displayActor = context.actorOf(RoundRobinPool(1).props(Props[DisplayMapActor]), "displayRouter")
  }

}
