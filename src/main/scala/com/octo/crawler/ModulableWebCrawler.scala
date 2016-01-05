package com.octo.crawler

import akka.actor.ActorSystem
import akka.routing.RoundRobinPool
import com.octo.crawler.Actors.crawling.ACrawlActor
import com.octo.crawler.Actors.messages.{Subscribe, Unsubscribe}
import com.octo.crawler.Actors.{ParserActor, URLAggregatorActor}
import rx.lang.scala.{Observable, Subscription}

/**
 * Created by bastien on 05/01/2015.
 */
class ModulableWebCrawler(val hostsToCrawl: Set[String], crawlingDepth: Int = 0, retryNumberOnError: Int = 3, httpBasicAuthLogin: String = "", httpBasicAuthPwd: String = "", proxyUrl: String = "", proxyPort: Int = 0, async: Boolean) {
  val system = ActorSystem("CrawlerSystem")
  val crawlActor = system.actorOf(RoundRobinPool(Runtime.getRuntime().availableProcessors()).props(ACrawlActor.props(retryNumberOnError, httpBasicAuthLogin, httpBasicAuthPwd, proxyUrl, proxyPort, async)), "crawlerRouter")
  val parserActor = system.actorOf(RoundRobinPool(Runtime.getRuntime().availableProcessors()).props(ParserActor.props(hostsToCrawl)), "parserRouter")
  val urlAggregator = system.actorOf(URLAggregatorActor.props(crawlingDepth, crawlActor, parserActor), name = "aggregator")


  def addObservable(): Observable[CrawledPage] = {
    Observable { observer =>
      urlAggregator ! Subscribe(observer)
      new Subscription {
        override def unsubscribe: Unit = urlAggregator ! Unsubscribe
      }
    }
  }

  def startCrawling(startingUrl: String): ModulableWebCrawler = {
    urlAggregator ! startingUrl
    this
  }
}

case class CrawledPage(url: String, errorCode: Int, responseBody: String, refererUrl: String)
