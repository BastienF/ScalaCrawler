package com.octo.crawler

import akka.actor._

/**
 * Created by bastien on 05/01/2015.
 */
object DemoMain {

  var running: Boolean = true

  def main(args: Array[String]) {
    val system = ActorSystem("CrawlerSystem")

    def httpBasicAuthFormatter(httpBasicAuth: String): (String, String) = {
      if (httpBasicAuth == null || httpBasicAuth.isEmpty)
        ("", "")
      else {
        val basicAuthSplited: Array[String] = httpBasicAuth.split(":")
        (basicAuthSplited(0), basicAuthSplited(1))
      }
    }

    val httpBasicAuth: (String, String) = httpBasicAuthFormatter(System.getProperty("basicAuth"))

    val proxyPortString: String = System.getProperty("proxyPort");
    val proxyPort: Int = if (proxyPortString == null || proxyPortString.isEmpty) 0 else proxyPortString.toInt
    val webCrawler: ModulableWebCrawler = new ModulableWebCrawler(System.getProperty("hosts").split(",").toSet,
      System.getProperty("depth").toInt, System.getProperty("retryNumber").toInt, httpBasicAuth._1, httpBasicAuth._2,
      System.getProperty("proxyUrl"), proxyPort)

    webCrawler.addObservable().filter(crawledPage => crawledPage.errorCode < 200 || crawledPage.errorCode >= 400).subscribe(crawledPage => handleCrawledPage(crawledPage))

    webCrawler.startCrawling(System.getProperty("startUrl"))


  }

  def handleCrawledPage(crawledPage: CrawledPage): Unit = {
    println(crawledPage.errorCode + ": " + crawledPage.url + " , " + crawledPage.refererUrl)
  }
}
