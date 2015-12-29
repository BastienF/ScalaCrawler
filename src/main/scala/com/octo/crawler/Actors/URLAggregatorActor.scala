package com.octo.crawler.Actors

import akka.actor.{Actor, ActorRef, Props}
import akka.pattern.ask
import akka.routing.RoundRobinPool
import akka.util.Timeout
import com.octo.crawler.Actors.messages.{CrawlActorResponse, Subscribe}
import com.octo.crawler.{DemoMain, CrawledPage}

import scala.annotation.tailrec
import scala.collection.mutable
import scala.concurrent.duration._

/**
 * Created by bastien on 05/01/2015.
 */
class URLAggregatorActor(val crawlingDepth: Int, crawlActor: ActorRef, parserActor: ActorRef) extends Actor {

  import URLAggregatorActor._;

  override def receive = {
    case Subscribe(onNext) => context become ready(onNext::Nil)
  }

  def ready(onNextList: List[CrawledPage => Unit]): Receive = {
    case Subscribe(onNext) => context become ready(onNext::onNextList)
    case "print" => printResult()
    case "printErrors" => printErrors()
    case url: String => crawlURLs(Set(url), crawlingDepth, "")
    case CrawlActorResponse(responseCode : Int, responseBody : String, remainingDepth: Int, url: String, refererUrl: String, (urlHost: String, urlPort: String, urlProtocolString)) => {
      //TODO à faire en asynchrone
      onNextList.foreach(onNext => onNext(new CrawledPage(url, responseCode, responseBody, refererUrl)))
      if (responseCode >= 200 && responseCode < 300)
        parserActor !(responseBody, remainingDepth, refererUrl, (urlHost, urlPort, urlProtocolString))
      else if (responseCode < 300 && responseCode >= 400) {
        urlsInError.update(url, (responseCode, refererUrl))
        if (responseCode == -1)
          urlCrawlingDone()
      }

    }
    case (urls: Set[String], remainingDepth: Int, refererUrl: String) => {
      crawlURLs(urls, remainingDepth - 1, refererUrl)
    }
    case x => println( s"""I don't know how to handle ${x}""")
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

      DemoMain.running = false
    })
  }

  def crawlUrl(url: String, remainingDepth: Int, refererUrl: String) = {
    crawlActor !(url, remainingDepth, refererUrl)
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

  object URLAggregatorActor {
    val startTime = System.currentTimeMillis()
    val displayActor = context.actorOf(RoundRobinPool(1).props(Props[DisplayMapActor]), "displayRouter")
    val urlsInError = mutable.Map[String, (Int, String)]()
    val crawledUrls = mutable.Set[String]()
    var timeLaps = startTime
    var crawledUrlNb = 0
    var crawledUrlNbLaps = 0
    var inCrawlingUrlNb = 1
  }

}

object URLAggregatorActor {
  def props(crawlingDepth: Int, crawlActor: ActorRef, parserActor: ActorRef): Props = Props(new URLAggregatorActor(crawlingDepth, crawlActor, parserActor))
}
