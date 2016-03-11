package com.octo.crawler.AkkaWorkflowExceptions

/**
 * Created by Bastien Fiorentino on 11/03/16.
 */
class NotImplementedMessageException private (message: String) extends Exception(message) {

}

object NotImplementedMessageException {
  def apply(akkaMessage:Object) = new NotImplementedMessageException("Handeling of this message is not implemented in the current actor context: " + akkaMessage.toString)
}
