package com.octo.crawler.Actors

import akka.actor.{ActorRef, Props, Actor}
import com.octo.crawler.Actors.ParserActor.{ParserActorMessages, ParseUrlsFromThisHTTPResponse}
import com.octo.crawler.Actors.URLAggregatorActor.CrawlThisUrlSet
import com.octo.crawler.AkkaWorkflowExceptions.{WrongMessageException, NotImplementedMessageException}
import scala.util.matching.Regex

/**
 * Created by bastien on 06/01/2015.
 */
class ParserActor(val hostsToCrawl: Set[String]) extends Actor {

  val uriPattern = """[0-9A-z_./?=#\(\)\[\];,%&:-]*"""
  val regexes: Set[(Regex, Boolean)] = Set(( s"""href="(/$uriPattern)"""".r, true)) ++ hostsToCrawl.map(host => ( s"""["|']($host$uriPattern)["|']""".r, false))

  override def receive: Receive = {
    case ParseUrlsFromThisHTTPResponse(requestBody: String, remainingDepth: Int, refererUrl: String, (urlHost: String, urlPort: String, urlProtocol: String)) => {
      parseHTTPResponse(requestBody, remainingDepth, refererUrl, urlHost, urlPort, urlProtocol)
    }
    case x: ParserActorMessages => throw NotImplementedMessageException(x)
    case x => throw WrongMessageException(x, sender())
  }

  private def parseHTTPResponse(requestBody: String, remainingDepth: Int, refererUrl: String, urlHost: String, urlPort: String, urlProtocol: String): Unit = {
    val urlAggregator: ActorRef = sender()
    urlAggregator ! CrawlThisUrlSet(regexes.foldLeft(Set[String]())(findUrls), remainingDepth, refererUrl)

    def findUrls(res: Set[String], elem: (Regex, Boolean)): Set[String] = {
      val foundUrls: Set[String] = (
        for (matchedString <- elem._1 findAllMatchIn requestBody)
          yield matchedString group 1
        ).toSet
      res ++ (
        if (elem._2) {
          foundUrls.map(url => urlProtocol + urlHost + urlPort + (if (!url.isEmpty && url.charAt(0).equals('/')) "" else "/") + url)
        }
        else {
          foundUrls
        }
        )
    }
  }
}

object ParserActor {
  def props(hostsToCrawl: Set[String]): Props = Props(new ParserActor(hostsToCrawl))

  sealed trait ParserActorMessages
  case class ParseUrlsFromThisHTTPResponse(requestBody: String, remainingDepth: Int, refererUrl: String, urlProperties: (String, String, String))
}
