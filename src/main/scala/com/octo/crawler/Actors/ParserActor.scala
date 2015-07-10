package com.octo.crawler.Actors

import akka.actor.Actor

import scala.util.matching.Regex

/**
 * Created by bastien on 06/01/2015.
 */
class ParserActor extends Actor {
  override def receive: Receive = {
    case (requestBody: String, remainingDepth: Int, refererUrl: String, (urlHost: String, urlPort: String, urlProtocolString)) => {

      sender() ! (ParserActor.regexes.foldLeft(Set[String]())(findUrls), remainingDepth, refererUrl)

      def findUrls(res: Set[String], elem: (Regex, Boolean)): Set[String] = {
        val foundUrls: Set[String] = (
          for (matchedString <- elem._1 findAllMatchIn requestBody)
            yield matchedString group 1
          ).toSet
        res ++ (
          if (elem._2) {
            foundUrls.map(url => urlProtocolString + urlHost + urlPort + "/" + url)
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
  val uriPattern = """[0-9A-z_./?=#\(\)\[\];,%&:-]*"""
  val regexes: Set[(Regex, Boolean)] = Set(( s"""href="(/$uriPattern)"""".r, true)) ++ System.getProperty("hosts").split(",").map(host => ( s"""["|']($host$uriPattern)["|']""".r, false))
}
