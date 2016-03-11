package com.octo.crawler.Actors.crawling

import java.net._

import akka.actor.Props
import com.octo.crawler.Actors.URLAggregatorActor.ExposeThisPageResponse

import scalaj.http.{Http, HttpOptions, HttpRequest, HttpResponse}


/**
 * Created by bastien on 05/01/2015.
 */
class SyncCrawlActor(retryNumberOnError: Int, httpBasicAuthLogin: String, httpBasicAuthPwd: String, proxyUrl: String, proxyPort: Int) extends ACrawlActor(retryNumberOnError: Int, httpBasicAuthLogin: String, httpBasicAuthPwd: String, proxyUrl: String, proxyPort: Int) {

  override final def executeRequest(url: String, remainingDepth: Int, refererUrl: String, retry: Int, depthRedirect: Int = 5): Unit = {
    val aggregator = sender()
    val urlProperties = getUrlProperies(url)
    try {
      val response: HttpResponse[String] =
        setAuth(setProxy(Http(url))).option(HttpOptions.allowUnsafeSSL).asString
      val optionnalLocationHeader: Option[String] = response.headers.get("Location")
      handleResponse(aggregator, response.code, response.body, remainingDepth, url, refererUrl, retry, if (optionnalLocationHeader.isDefined) optionnalLocationHeader.get else "", depthRedirect)
    } catch {
      case e@(_: SocketTimeoutException | _: ConnectException) if retry > 0 => {
        println( s"""retry #${retryNumberOnError - (retry - 1)} : ${url}|error: ${e}""")
        Thread sleep 500 + (1500 * retryNumberOnError - (retry))
        executeRequest(url, remainingDepth, refererUrl, retry - 1)
      }
      case e: Exception => {
        println( s"""HTTP request error on ${url} with ${e}""")
        aggregator ! new ExposeThisPageResponse(-1, "", remainingDepth, url, refererUrl, urlProperties)
      }
    }
  }

  def setProxy(request: HttpRequest) = {
    if (proxyUrl == null || proxyUrl.isEmpty)
      request
    else
      request.proxy(proxyUrl, proxyPort)
  }

  def setAuth(request: HttpRequest) = {
    if (httpBasicAuthLogin == null || httpBasicAuthLogin.isEmpty)
      request
    else {
      request.auth(httpBasicAuthLogin, httpBasicAuthPwd)
    }
  }
}

object SyncCrawlActor {
  def props(retryNumberOnError: Int, httpBasicAuthLogin: String, httpBasicAuthPwd: String, proxyUrl: String, proxyPort: Int): Props = Props(new SyncCrawlActor(retryNumberOnError, httpBasicAuthLogin, httpBasicAuthPwd, proxyUrl, proxyPort))
}
