package com.octo.crawler.Actors

import java.net.{ConnectException, SocketTimeoutException}

import akka.actor.Actor

import scalaj.http.{HttpRequest, Http, HttpOptions, HttpResponse}


/**
 * Created by bastien on 05/01/2015.
 */
class CrawlActor extends Actor {

  override def receive: Receive = {
    case (url: String, remainingDepth: Int, refererUrl: String) => crawlUrl(url, remainingDepth, refererUrl)
  }

  def crawlUrl(url: String, remainingDepth: Int, refererUrl: String): Unit = {
    try {
      executeRequest(url, remainingDepth, refererUrl, CrawlActor.retryNumber)
    } catch {
      case e: Exception => {
        println( s"""HTTP request error on ${url} with ${e}""")
      }

    }
  }

  private final def executeRequest(url2: String, remainingDepth: Int, refererUrl: String, retry: Int): Unit = {
    val aggregator = sender()
    try {

      def setProxy(request:HttpRequest) = {
        val proxyUrl: String = System.getProperty("proxyUrl")
        if (proxyUrl == null || proxyUrl.isEmpty)
          request
        else
          request.proxy(proxyUrl, System.getProperty("proxyPort").toInt)
      }
      def setAuth(request:HttpRequest) = {
        val basicAuth: String = System.getProperty("basicAuth")
        if (basicAuth == null || basicAuth.isEmpty)
          request
        else {
          val basicAuthSplited:Array[String] = basicAuth.split(":")
          request.auth(basicAuthSplited(0), basicAuthSplited(1))
        }
      }
      val response: HttpResponse[String] =
        setAuth(setProxy(Http(url2))).option(HttpOptions.allowUnsafeSSL).asString

      val urlJava = new java.net.URL(url2)
      val urlProperties = (urlJava getHost, if ((urlJava getPort) < 0) {
        ""
      } else {
        ":" + urlJava.getPort
      }, (urlJava getProtocol) + "://")

      if (response.is3xx) {
        val redirectURL: Option[String] = response.headers.get("Location")
        if (redirectURL.isDefined) {
          val redirectAbs: String = {
            if (redirectURL.get.charAt(0).equals('/'))
              urlProperties._3 + urlProperties._1 + urlProperties._2 + redirectURL.get
            else
              redirectURL.get
          }
          executeRequest(redirectAbs, remainingDepth, url2, retry - 1)
        }
      } else {
        aggregator !(response.body, remainingDepth, url2, urlProperties)
        if (!response.is2xx) {
          aggregator !(response.code, url2, refererUrl)
        }
      }
    } catch {
      case e @ (_ : SocketTimeoutException | _ : ConnectException) if retry > 0 => {
        println( s"""retry #${CrawlActor.retryNumber - (retry - 1)} : ${url2} | error: ${e}""")
        Thread sleep 500 + (1500 * CrawlActor.retryNumber - (retry))
        executeRequest(url2, remainingDepth, refererUrl, retry - 1)
      }
      case e: Exception => {
        println( s"""HTTP request error on ${url2} with ${e}""")
        aggregator ! (-1, url2, refererUrl)
      }
    }
  }

}

object CrawlActor {
  val retryNumber = System.getProperty("retryNumber").toInt
}
