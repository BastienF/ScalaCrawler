package com.octo.crawler.Actors.messages

import com.octo.crawler.CrawledPage

/**
 * Created by bastien on 05/01/2015.
 */

sealed trait Message

sealed trait SubUnsub extends Message

case class Subscribe(onNext: CrawledPage => Unit) extends SubUnsub

case object Unsubscribe extends SubUnsub

case class CrawlActorResponse(responseCode : Int, responseBody : String, remainingDepth: Int, url: String, refererUrl: String, urlProperties: (String, String, String))

