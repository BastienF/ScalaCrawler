package com.octo.crawler.Actors

import java.util.concurrent.Executor

import akka.actor.{Actor, ActorRef, Props}
import akka.routing.RoundRobinPool
import com.octo.crawler.Actors.messages.{CrawlActorResponse, Subscribe}
import com.octo.crawler.CrawledPage
import rx.lang.scala.Subscriber

import scala.annotation.tailrec
import scala.collection.mutable

/**
 * Created by bastien on 05/01/2015.
 */
class URLAggregatorActor(val crawlingDepth: Int, crawlActor: ActorRef, parserActor: ActorRef, executor: Executor) extends Actor {

  import URLAggregatorActor._;

  override def receive = {
    case Subscribe(subscriber) => context become ready(subscriber :: Nil)
  }

  def ready(subscriberList: List[Subscriber[CrawledPage]]): Receive = {
    case Subscribe(subscriber) => context become ready(subscriber :: subscriberList)
    case "print" => printResult()
    case "printErrors" => printErrors()
    case "crawlingDone" => sthap(subscriberList)
    case url: String => crawlURLs(Set(url), crawlingDepth, "")
    case CrawlActorResponse(responseCode: Int, responseBody: String, remainingDepth: Int, url: String, refererUrl: String, (urlHost: String, urlPort: String, urlProtocolString)) => {
      subscriberList.foreach(subscriber => executor.execute(new Runnable {
        override def run(): Unit = subscriber.onNext(new CrawledPage(url, responseCode, responseBody, refererUrl))
      }))
      if (responseCode < 300 || responseCode >= 400)
        parserActor !(responseBody, remainingDepth, url, (urlHost, urlPort, urlProtocolString))
    }
    case (urls: Set[String], remainingDepth: Int, refererUrl: String) => {
      crawlURLs(urls, remainingDepth - 1, refererUrl)
      urlCrawlingDone()
    }
    case x => println( s"""I don't know how to handle ${x}""")
  }

  def urlCrawlingDone(): Unit = {
    crawledUrlNb += 1
    inCrawlingUrlNb -= 1
    if (inCrawlingUrlNb <= 0) {
      self ! "crawlingDone"
    }
  }

  def sthap(subscriberList: List[Subscriber[CrawledPage]]): Unit = {
    subscriberList.foreach(subscriber => executor.execute(new Runnable {
      override def run(): Unit = subscriberList.foreach(subscriber => subscriber.onCompleted())
    }))
    context.system.shutdown()
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
    var inCrawlingUrlNb = 0
  }

}

object URLAggregatorActor {
  def props(crawlingDepth: Int, crawlActor: ActorRef, parserActor: ActorRef, executor: Executor): Props = Props(new URLAggregatorActor(crawlingDepth, crawlActor, parserActor, executor))
}
