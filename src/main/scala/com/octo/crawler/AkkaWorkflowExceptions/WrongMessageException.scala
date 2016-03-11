package com.octo.crawler.AkkaWorkflowExceptions

import akka.actor.ActorRef

/**
 * Created by Bastien Fiorentino on 11/03/16.
 */
class WrongMessageException private (message: String) extends Exception(message) {

}

object WrongMessageException {
  def apply(akkaMessage:Any, sender: ActorRef) = new WrongMessageException(sender + " sends a message to the wrong actor: " + akkaMessage)
}

