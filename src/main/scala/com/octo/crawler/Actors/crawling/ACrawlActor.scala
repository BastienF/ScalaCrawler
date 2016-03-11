package com.octo.crawler.Actors.crawling

import akka.actor._
import com.octo.crawler.Actors.URLAggregatorActor.ExposeThisPageResponse
import com.octo.crawler.Actors.crawling.ACrawlActor.{CrawlActorMessage, ExecuteHTTPRequest}
import com.octo.crawler.AkkaWorkflowExceptions.{NotImplementedMessageException, WrongMessageException}


/**
 * Created by bastien on 05/01/2016.
 */
abstract class ACrawlActor(val retryNumberOnError: Int, httpBasicAuthLogin: String, httpBasicAuthPwd: String, proxyUrl: String, proxyPort: Int) extends Actor {

  override def receive: Receive = {
    case ExecuteHTTPRequest(url: String, remainingDepth: Int, refererUrl: String) => crawlUrl(url, remainingDepth, refererUrl)
    case x: CrawlActorMessage => throw NotImplementedMessageException(x)
    case x => throw WrongMessageException(x, sender())
  }

  def crawlUrl(url: String, remainingDepth: Int, refererUrl: String): Unit = {
    try {
      executeRequest(url, remainingDepth, refererUrl, retryNumberOnError)
    } catch {
      case e: Exception => {
        println( s"""HTTP request error on ${url} with ${e}""")
      }

    }
  }

  final def handleResponse(aggregator: ActorRef, responseCode: Int, responseBody: String, remainingDepth: Int, url: String, refererUrl: String, retry: Int, headerLocation: String, depthRedirect: Int): Unit = {
    val urlProperties = getUrlProperies(url)
    if (depthRedirect > 0)
      aggregator ! ExposeThisPageResponse(responseCode, responseBody, remainingDepth, url, refererUrl, urlProperties)
    else
      aggregator ! ExposeThisPageResponse(-2, responseBody, remainingDepth, url, refererUrl, urlProperties)

    if (depthRedirect > 0 && responseCode >= 300 && responseCode < 400) {
      val redirectAbs: String = {
        if (headerLocation.charAt(0).equals('/'))
          urlProperties._3 + urlProperties._1 + urlProperties._2 + headerLocation
        else
          headerLocation
      }
      executeRequest(redirectAbs, remainingDepth, url, retry - 1, depthRedirect - 1)
    }
  }

  def executeRequest(url: String, remainingDepth: Int, refererUrl: String, retry: Int, depthRedirect: Int = 5): Unit

  def getUrlProperies(url: String): (String, String, String) = {
    val urlJava = new java.net.URL(url)
    (urlJava getHost, if ((urlJava getPort) < 0) {
      ""
    } else {
      ":" + urlJava.getPort
    }, (urlJava getProtocol) + "://")
  }
}

object ACrawlActor {
  def props(retryNumberOnError: Int, httpBasicAuthLogin: String, httpBasicAuthPwd: String, proxyUrl: String, proxyPort: Int, async: Boolean): Props = {
    if (async)
      AsyncCrawlActor.props(retryNumberOnError, httpBasicAuthLogin, httpBasicAuthPwd, proxyUrl, proxyPort)
    else
      SyncCrawlActor.props(retryNumberOnError, httpBasicAuthLogin, httpBasicAuthPwd, proxyUrl, proxyPort)
  }

  sealed trait CrawlActorMessage

  case class ExecuteHTTPRequest(url: String, remainingDepth: Int, refererUrl: String) extends CrawlActorMessage

}




