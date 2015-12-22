# ScalaCrawler
Scala implementation of basic HTTP crawler

```
sbt assembly
java -jar -DretryNumber=<numberOfRetryOnFail=5> -Ddepth=<linkFollowedDepth=6> -DstartUrl=<startingCrawlURL=https://en.wikipedia.org/wiki/Scala_(programming_language)> -Dhosts=<hostsToCrawl=https://en.wikipedia.org,https://fr.wikipedia.org> -DcsvResultPath=./result.csv [-DbasicAuth=<username:password> -DnotTTY] target/scala-2.11/ScalaCrawler-assembly-1.0.jar
```
