package com.octo.crawler.Actors.messages

import com.octo.crawler.CrawledPage
import rx.lang.scala.Subscriber

/**
 * Created by bastien on 05/01/2015.
 */

sealed trait Message

sealed trait SubUnsub extends Message

case class Subscribe(subscriber: Subscriber[CrawledPage]) extends SubUnsub

case object Unsubscribe extends SubUnsub

case class CrawlActorResponse(responseCode : Int, responseBody : String, remainingDepth: Int, url: String, refererUrl: String, urlProperties: (String, String, String))

