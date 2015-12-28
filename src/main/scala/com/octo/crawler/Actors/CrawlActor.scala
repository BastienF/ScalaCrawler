package com.octo.crawler.Actors

import java.net._
import akka.actor.{Actor, Props}
import com.octo.crawler.Actors.messages.CrawlActorResponse
import scalaj.http.{Http, HttpOptions, HttpRequest, HttpResponse}


/**
 * Created by bastien on 05/01/2015.
 */
class CrawlActor(val retryNumberOnError: Int, httpBasicAuthLogin: String, httpBasicAuthPwd: String, proxyUrl: String, proxyPort: Int) extends Actor {

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

  private final def executeRequest(url: String, remainingDepth: Int, refererUrl: String, retry: Int): Unit = {
    val aggregator = sender()
    val urlProperties = getUrlProperies(url)
    try {
      val response: HttpResponse[String] =
        setAuth(setProxy(Http(url))).option(HttpOptions.allowUnsafeSSL).asString

      aggregator ! new CrawlActorResponse(response.code, response.body, remainingDepth, url, refererUrl, urlProperties)

      if (response.is3xx) {
        val redirectURL: Option[String] = response.headers.get("Location")
        if (redirectURL.isDefined) {
          val redirectAbs: String = {
            if (redirectURL.get.charAt(0).equals('/'))
              urlProperties._3 + urlProperties._1 + urlProperties._2 + redirectURL.get
            else
              redirectURL.get
          }
          executeRequest(redirectAbs, remainingDepth, url, retry - 1)
        }
      }
    } catch {
      case e@(_: SocketTimeoutException | _: ConnectException) if retry > 0 => {
        println( s"""retry #${retryNumberOnError - (retry - 1)} : ${url}| error: ${e}""")
        Thread sleep 500 + (1500 * retryNumberOnError - (retry))
        executeRequest(url, remainingDepth, refererUrl, retry - 1)
      }
      case e: Exception => {
        println( s"""HTTP request error on ${url} with ${e}""")
        aggregator ! new CrawlActorResponse(-1, "", remainingDepth, url, refererUrl, urlProperties)
      }
    }
  }

  def getUrlProperies(url: String): (String, String, String) = {
    val urlJava = new java.net.URL(url)
    (urlJava getHost, if ((urlJava getPort) < 0) {
      ""
    } else {
      ":" + urlJava.getPort
    }, (urlJava getProtocol) + "://")
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

object CrawlActor {
  def props(retryNumberOnError: Int, httpBasicAuthLogin: String, httpBasicAuthPwd: String, proxyUrl: String, proxyPort: Int): Props = Props(new CrawlActor(retryNumberOnError, httpBasicAuthLogin, httpBasicAuthPwd, proxyUrl, proxyPort))
}
