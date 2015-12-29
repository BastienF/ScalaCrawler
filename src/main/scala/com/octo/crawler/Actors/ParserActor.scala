package com.octo.crawler.Actors

import akka.actor.{Props, Actor}
import scala.util.matching.Regex

/**
 * Created by bastien on 06/01/2015.
 */
class ParserActor(val hostsToCrawl: Set[String]) extends Actor {

  val uriPattern = """[0-9A-z_./?=#\(\)\[\];,%&:-]*"""
  val regexes: Set[(Regex, Boolean)] = Set(( s"""href="(/$uriPattern)"""".r, true)) ++ hostsToCrawl.map(host => ( s"""["|']($host$uriPattern)["|']""".r, false))

  override def receive: Receive = {
    case (requestBody: String, remainingDepth: Int, refererUrl: String, (urlHost: String, urlPort: String, urlProtocolString)) => {

      sender() !(regexes.foldLeft(Set[String]())(findUrls), remainingDepth, refererUrl)

      def findUrls(res: Set[String], elem: (Regex, Boolean)): Set[String] = {
        val foundUrls: Set[String] = (
          for (matchedString <- elem._1 findAllMatchIn requestBody)
            yield matchedString group 1
          ).toSet
        res ++ (
          if (elem._2) {
            foundUrls.map(url => urlProtocolString + urlHost + urlPort + (if (!url.isEmpty && url.charAt(0).equals('/')) "" else "/") + url)
          }
          else {
            foundUrls
          }
          )
      }
    }
  }
}

object ParserActor {
  def props(hostsToCrawl: Set[String]): Props = Props(new ParserActor(hostsToCrawl))
}
