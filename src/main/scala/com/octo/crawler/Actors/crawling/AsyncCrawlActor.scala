package com.octo.crawler.Actors.crawling

import java.net._

import akka.actor.Props
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpEntity.Strict
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.{ActorMaterializer, Materializer}
import com.octo.crawler.Actors.URLAggregatorActor.ExposeThisPageResponse

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}


/**
 * Created by bastien on 05/01/2015.
 */
class AsyncCrawlActor(retryNumberOnError: Int, httpBasicAuthLogin: String, httpBasicAuthPwd: String, proxyUrl: String, proxyPort: Int) extends ACrawlActor(retryNumberOnError: Int, httpBasicAuthLogin: String, httpBasicAuthPwd: String, proxyUrl: String, proxyPort: Int) {

  override final def executeRequest(url: String, remainingDepth: Int, refererUrl: String, retry: Int, depthRedirect: Int = 5): Unit = {
    val aggregator = sender()
    val urlProperties = getUrlProperies(url)
    try {
      implicit val executionContext: ExecutionContext = context.system.dispatcher // TODO HAHAHAHA
      httpGet(url) onComplete {
        case Success(r) => {
          implicit val materializer: Materializer = ActorMaterializer()
          implicit val executionContext: ExecutionContext = context.system.dispatcher // TODO HAHAHAHA
          val bodyFuture: Future[Strict] = r.entity.toStrict(5 seconds)
          bodyFuture onComplete {
            case Success(body) => handleResponse(aggregator, r.status.intValue(), body.data.decodeString("UTF-8"), remainingDepth, url, refererUrl, retry, if (r.getHeader("Location").isDefined) r.getHeader("Location").get.value() else "", depthRedirect) //Pousser le futur dans un pool qui va supporter le wait
            case Failure(f) => println("Error during parsing of: " + url + ": " + f.getMessage) //TODO crawlActor ! Status.Failure(f)
          }
        }
        case Failure(f) => println("Error during crawl of: " + url + ": " + f.getMessage) //TODO crawlActor ! Status.Failure(f)
      }


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

  private final def httpGet(url: String): Future[HttpResponse] = {
    Http()(context.system).singleRequest(HttpRequest(uri = url))(ActorMaterializer())
  }

  //TODO
  /* def setProxy(request: HttpRequest) = {
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
   }*/
}


object AsyncCrawlActor {
  def props(retryNumberOnError: Int, httpBasicAuthLogin: String, httpBasicAuthPwd: String, proxyUrl: String, proxyPort: Int): Props = Props(new AsyncCrawlActor(retryNumberOnError, httpBasicAuthLogin, httpBasicAuthPwd, proxyUrl, proxyPort))
}


