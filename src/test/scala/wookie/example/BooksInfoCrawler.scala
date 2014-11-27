package wookie.example

import java.io.File
import java.net.URLEncoder

import org.apache.commons.io.FilenameUtils
import org.apache.commons.lang3.StringUtils
import wookie.example.SimpleCrawlerState._
import wookie.{WookiePanel, WookieSandboxApp, WookieSimpleScenario}


/**
 * @author Andrey Chaschev chaschev@gmail.com
 */

class BooksInfoOptions (
  folder: File
)

case class BookLink (
  link: String,
  rating: Double,
  raters: Int,
  title: String
)

case class BooksEntry(
  file: String,
  amazonBook: Option[BookLink],
  goodReadsBook: Option[BookLink],
  _state: SimpleCrawlerState = NOT_STARTED,
  _comment: String = ""
) extends SimpleCrawlerEntry(_state, _comment) {

}

class CrawlException(msg: String) extends RuntimeException(msg)

object BooksInfoCrawler {
  val defaultPanel = SearchAndStarWookieSimple.defaultPanel
  def newPanel: WookiePanel = defaultPanel.apply()

//  implicit lazy val formats = DefaultFormats +

  def main(args: Array[String])
  {
    val crawler = new SimpleCrawler[BooksEntry](new SimpleCrawlerOptions[BooksEntry](
      folder = "/Users/andrey/Google Drive/books/gelato",
      projectName = "gelato"
    ))

    val app = WookieSandboxApp.start()

    app.runOnStage(
      new WookieSimpleScenario("Get Books Ratings", defaultPanel) {
        override def run(): Unit = {
          val bookFiles = new File(crawler.options.folder).listFiles().toList.filterNot( f=>
            f.isHidden ||
            f.getName.endsWith("jpg") ||
            f.getName.endsWith("opf")
          )

          for(bookFile <- bookFiles) {
            val crawledBookOpt: Option[BooksEntry] = crawler.state.entries.find { x=>
              new File(x.file).getCanonicalFile.equals(bookFile.getCanonicalFile)
            }

            def getRatings: BooksEntry = {
              load(s"http://www.amazon.com/s/ref=nb_sb_noss_2?url=search-alias%3Daps&field-keywords=${URLEncoder.encode(FilenameUtils.getBaseName(bookFile.getName), "UTF-8")}")

              val amazonLink = {


                val amazonResults = $("li.s-result-item").asResultList()
                if (amazonResults.isEmpty || $("body").html().contains("did not match any products")) {
                  throw new CrawlException("no results for " + bookFile.getName)
                } else {
                  amazonResults.find {
                    _.find("i.a-icon-star > .a-icon-alt").nonEmpty
                  } .map {
                    amazonResultDiv =>
                      new BookLink(
                        title = amazonResultDiv.find("h2.s-access-title")(0).trimmedText(),
                        link = amazonResultDiv.find("a.s-access-detail-page")(0).trimmedText(),
                        rating = StringUtils.substringBefore(amazonResultDiv.find("i.a-icon-star > .a-icon-alt")(0).text(), " ").toDouble,
                        raters = amazonResultDiv.find(".a-span5 > .a-spacing-mini a.a-size-small.a-link-normal")(0).trimmedText().toInt
                      )
                  }
                }
              }

              new BooksEntry(bookFile.getCanonicalPath, amazonLink, None, FINISHED)
            }

            try {
              if (crawledBookOpt.isDefined) {
                val entry = crawledBookOpt.get
                entry.state match {
                  case NOT_STARTED =>
                  case IN_PROGRESS =>
                    crawler.state.entries -= entry

                    val newEntry = getRatings


                    crawler.state.entries += newEntry
                  case _ =>
                }
              } else {
                crawler.state.entries += getRatings
                crawler.saveState()
              }
            }
            catch {
              case e: CrawlException =>
                crawler.state.entries += new BooksEntry(bookFile.getCanonicalPath, None, None, FAILED, e.toString)
            } finally {
              crawler.saveState()
            }
          }
        }
    }.asNotSimpleScenario)

  }
}
