package com.octo.crawler.Actors

import java.util.concurrent.Executor

import akka.actor.{Actor, ActorRef, Props}
import com.octo.crawler.Actors.ParserActor.ParseUrlsFromThisHTTPResponse
import com.octo.crawler.Actors.URLAggregatorActor._
import com.octo.crawler.Actors.crawling.ACrawlActor.ExecuteHTTPRequest
import com.octo.crawler.AkkaWorkflowExceptions.{NotImplementedMessageException, WrongMessageException}
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
    case RegisterThisSubscriber(subscriber) => context become ready(Set(subscriber))
    case x: URLAggregatorActorMessages => throw NotImplementedMessageException(x)
    case x => throw WrongMessageException(x, sender())
  }

  def ready(subscriberList: Set[Subscriber[CrawledPage]]): Receive = {
    case RegisterThisSubscriber(subscriber) => context become ready(subscriberList + subscriber)
    case UnregisterThisSubscriber(subscriber) => context become ready(subscriberList - subscriber)
    case StopCrawling => sthap(subscriberList)
    case StartOnThisUrl(url) => crawlURLs(Set(url), crawlingDepth, "")
    case CrawlThisUrlSet(urls: Set[String], remainingDepth: Int, refererUrl: String) => {
      crawlURLs(urls, remainingDepth - 1, refererUrl)
      urlCrawlingDone()
    }
    case ExposeThisPageResponse(responseCode: Int, responseBody: String, remainingDepth: Int, url: String, refererUrl: String, (urlHost: String, urlPort: String, urlProtocolString)) => {
      subscriberList.foreach(subscriber => executor.execute(new Runnable {
        override def run(): Unit = subscriber.onNext(new CrawledPage(url, responseCode, responseBody, refererUrl))
      }))
      if (responseCode < 300 || responseCode >= 400)
        parserActor ! ParseUrlsFromThisHTTPResponse(responseBody, remainingDepth, url, (urlHost, urlPort, urlProtocolString))
    }
    case x: URLAggregatorActorMessages => throw NotImplementedMessageException(x)
    case x => throw WrongMessageException(x, sender())
  }

  def urlCrawlingDone(): Unit = {
    crawledUrlNb += 1
    inCrawlingUrlNb -= 1
    if (inCrawlingUrlNb <= 0) {
      self ! StopCrawling
    }
  }

  def sthap(subscriberList: Set[Subscriber[CrawledPage]]): Unit = {
    subscriberList.foreach(subscriber => executor.execute(new Runnable {
      override def run(): Unit = subscriberList.foreach(subscriber => subscriber.onCompleted())
    }))
    context.system.shutdown()
  }

  def crawlUrl(url: String, remainingDepth: Int, refererUrl: String) = {
    crawlActor ! ExecuteHTTPRequest(url, remainingDepth, refererUrl)
    crawledUrls.add(url)
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
    val crawledUrls = mutable.Set[String]()
    var timeLaps = startTime
    var crawledUrlNb = 0
    var crawledUrlNbLaps = 0
    var inCrawlingUrlNb = 0
  }

}

object URLAggregatorActor {
  def props(crawlingDepth: Int, crawlActor: ActorRef, parserActor: ActorRef, executor: Executor): Props = Props(new URLAggregatorActor(crawlingDepth, crawlActor, parserActor, executor))

  sealed trait URLAggregatorActorMessages

  case class ExposeThisPageResponse(responseCode: Int, responseBody: String, remainingDepth: Int, url: String, refererUrl: String, urlProperties: (String, String, String)) extends URLAggregatorActorMessages

  case class StartOnThisUrl(url: String) extends URLAggregatorActorMessages

  case class CrawlThisUrls(urls: Set[String]) extends URLAggregatorActorMessages

  case object StopCrawling extends URLAggregatorActorMessages

  case class CrawlThisUrlSet(urls: Set[String], remainingDepth: Int, refererUrl: String) extends URLAggregatorActorMessages

  case class RegisterThisSubscriber(subscriber: Subscriber[CrawledPage]) extends URLAggregatorActorMessages

  case class UnregisterThisSubscriber(listener: Subscriber[CrawledPage]) extends URLAggregatorActorMessages

}

