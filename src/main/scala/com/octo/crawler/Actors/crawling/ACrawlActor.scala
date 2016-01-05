package com.octo.crawler.Actors.crawling

import akka.actor.{Actor, ActorRef, Props}
import com.octo.crawler.Actors.messages.CrawlActorResponse


/**
 * Created by bastien on 05/01/2016.
 */
abstract class ACrawlActor(val retryNumberOnError: Int, httpBasicAuthLogin: String, httpBasicAuthPwd: String, proxyUrl: String, proxyPort: Int) extends Actor {

  override def receive: Receive = {
    case (url: String, remainingDepth: Int, refererUrl: String) => crawlUrl(url, remainingDepth, refererUrl)
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

  final def handleResponse(aggregator: ActorRef, responseCode: Int, responseBody: String, remainingDepth: Int, url: String, refererUrl: String, retry: Int, headerLocation: String): Unit = {
    val urlProperties = getUrlProperies(url)
    aggregator ! new CrawlActorResponse(responseCode, responseBody, remainingDepth, url, refererUrl, urlProperties)

    if (responseCode >= 300 && responseCode < 400) {
      val redirectAbs: String = {
        if (headerLocation.charAt(0).equals('/'))
          urlProperties._3 + urlProperties._1 + urlProperties._2 + headerLocation
        else
          headerLocation
      }
      executeRequest(redirectAbs, remainingDepth, url, retry - 1)
    }
  }

  def executeRequest(url: String, remainingDepth: Int, refererUrl: String, retry: Int): Unit

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
}




