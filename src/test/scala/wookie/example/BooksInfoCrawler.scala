package wookie.example

import java.io.{File, FileOutputStream}
import java.net.URLEncoder
import java.util.{ArrayList => JList}

import chaschev.lang.LangUtils.toConciseString
import de.neuland.jade4j.Jade4J
import org.apache.commons.io.FilenameUtils.getBaseName
import org.apache.commons.io.{FileUtils, IOUtils}
import org.apache.commons.lang3.StringUtils.{substringBefore, substringBetween}
import wookie.example.SimpleCrawlerState._
import wookie.{WookiePanel, WookieSandboxApp, WookieSimpleScenario}

import scala.collection.JavaConversions._

/**
 * This application will fetch ratings for the books stored in a folder from Amazon, Good Reads, B&N.
 *
 * It will render results into books.html in the same folder by using Jade.
 *
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
  barnesBook: Option[BookLink],
  _state: SimpleCrawlerState = NOT_STARTED,
  _comment: String = ""
) extends SimpleCrawlerEntry(_state, _comment) {
  def weighedRating: Double = {
    val d =
      amazonBook.map{_.rating}.getOrElse(0d) * 0.8 +
      goodReadsBook.map{_.rating}.getOrElse(0d) * 1.1 +
      barnesBook.map{_.rating}.getOrElse(0d) * 1.1

    toConciseString(if (bookCount == 0) 0 else d / bookCount, 2).toDouble
  }

  def bookCount: Int = {
    amazonBook.map(_ => 1).getOrElse(0) +
      goodReadsBook.map(_ => 1).getOrElse(0) +
      barnesBook.map(_ => 1).getOrElse(0)
  }

  def totalVotes: Int = {
    amazonBook.map{_.raters}.getOrElse(0) +
      goodReadsBook.map{_.raters}.getOrElse(0) +
      barnesBook.map{_.raters}.getOrElse(0)
  }

  def title: String = {
    amazonBook.map(_.title).getOrElse(
      goodReadsBook.map(_.title).getOrElse(
        barnesBook.map(_.title).getOrElse("<no-title>")
      )
    )
  }

  def goodReadsRef = goodReadsBook.map(_.link).getOrElse()
}

class CrawlException(msg: String) extends RuntimeException(msg)

object BooksInfoCrawler {
  val FOLDER = "/Users/andrey/Google Drive/books/gelato"

  val defaultPanel = SearchAndStarWookieSimple.defaultPanel
  def newPanel: WookiePanel = defaultPanel.apply()

//  implicit lazy val formats = DefaultFormats +

  def main(args: Array[String])
  {
    val crawler = new SimpleCrawler[BooksEntry](new SimpleCrawlerOptions[BooksEntry](
      folder = FOLDER,
      projectName = getBaseName(FOLDER)
    ))

    val app = WookieSandboxApp.start()

    app.runOnStage(
      new WookieSimpleScenario("Get Books Ratings", defaultPanel) {
        override def run(): Unit = {
          crawler.start()

          val bookFiles = new File(crawler.options.folder).listFiles().toList.filterNot( f=>
            f.isHidden ||
            f.getName.endsWith("jpg") ||
            f.getName.endsWith("opf") ||
            f.getName.startsWith("books")
          )

          for(bookFile <- bookFiles) {
            val crawledBookOpt: Option[BooksEntry] = crawler.state.entries.find { x=>
              new File(x.file).getCanonicalFile.equals(bookFile.getCanonicalFile)
            }

            def getRatings: BooksEntry = {
              val encodedRequest = URLEncoder.encode(getBaseName(bookFile.getName), "UTF-8")

              load(s"http://www.amazon.com/s/ref=nb_sb_noss_2?url=search-alias%3Daps&field-keywords=${encodedRequest}")

              val amazonLink = {
                val amazonResults = $("li.s-result-item").asResultList()

                if (amazonResults.isEmpty || $("body").html().contains("did not match any products")) {
                  None
                } else {
                  amazonResults.find {
                    _.find("i.a-icon-star > .a-icon-alt").nonEmpty
                  } .map {
                    amazonResultDiv =>
                      new BookLink(
                        title = amazonResultDiv.find("h2.s-access-title")(0).trimmedText(),
                        link = amazonResultDiv.find("a.s-access-detail-page")(0).trimmedText(),
                        rating = substringBefore(amazonResultDiv.find("i.a-icon-star > .a-icon-alt")(0).text(), " ").toDouble,
                        raters = amazonResultDiv.find(".a-span5 > .a-spacing-mini a.a-size-small.a-link-normal")(0).trimmedText().toInt
                      )
                  }
                }
              }

//              val amazonLink = None

              load(s"https://www.goodreads.com/search?utf8=%E2%9C%93&query=$encodedRequest")

              val goodreadsLink = {
                val goodreadsResults = $(".tableList > tbody > tr").asResultList()

                if (goodreadsResults.isEmpty || $("body").html().contains("No results found - sorry")) {
                  None
                } else {
                  goodreadsResults.find { goodreadsResultDiv =>
                    val miniRating = goodreadsResultDiv.find(".minirating")

                    miniRating.nonEmpty && !miniRating(0).trimmedText().startsWith("0.0")
                  } .map {
                    goodreadsResultDiv =>
                      val ratingString = goodreadsResultDiv.find(".minirating")(0).trimmedText()

                      val rating = substringBefore(ratingString, " ").toDouble
                      val raters = substringBetween(ratingString, "— ", " rating").replaceAll(",", "").toInt

                      new BookLink(
                        title = goodreadsResultDiv.find("a.bookTitle")(0).trimmedText(),
                        link = goodreadsResultDiv.find("a.bookTitle")(0).attr("href"),
                        rating = rating,
                        raters = raters
                      )
                  }
                }

              }

//              val goodreadsLink = None

              load(s"http://www.barnesandnoble.com/s/$encodedRequest")

              if(wookie.getCurrentDocUri.getOrElse("noresults").contains("noresults")
                && !wookie.isPageReady) {
                waitForPageReady()
              }

              val barnesLink = {
                val barnesResults = $(".result.box").asResultList()

                val body = $("body").html()
                if (barnesResults.isEmpty
                  || body.contains("Sorry, we could not find what you were looking for.")
                  || body.contains("Please try another search or browse")) {
                  None
                } else {
                  barnesResults.find {
                    _.find(".stars-small").nonEmpty
                  } .map {
                    barnesResultDiv =>
                      val ratingString = barnesResultDiv.find(".stars-small")(0).attr("title")

                      val rating = toConciseString(substringBetween(ratingString, "of ", " ").toDouble, 2) .toDouble

                      val titleLink = barnesResultDiv.find("a.title")(0)

                      val title = titleLink.trimmedText()
                      val link = titleLink.attr("href")

                      titleLink.followLink()

                      val raters = substringBefore($(".starDisplay > .total > a").trimmedText(), "\n").trim.toInt

                      new BookLink(
                        link = link,
                        title = title,
                        rating = rating,
                        raters = raters
                      )
                  }
                }
              }

              new BooksEntry(bookFile.getCanonicalPath, amazonLink, goodreadsLink, barnesLink, FINISHED)
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
                crawler.state.entries += new BooksEntry(bookFile.getCanonicalPath, None, None, None, FAILED, e.toString)
            } finally {
              crawler.saveState()
            }
          }

          crawler.complete()

          val sortedEntries = crawler.state.entries.toList.sortWith((e1, e2) => e1.weighedRating - e2.weighedRating > 0)
          val sortedEntriesJava = new JList[BooksEntry]()

          sortedEntries.foreach(sortedEntriesJava.add)

          val indexFileHtml = new File(crawler.options.folder, "books.html")

          IOUtils.copy(getClass.getResourceAsStream("books.css"), new FileOutputStream(new File(crawler.options.folder, "books.css")))

          FileUtils.writeStringToFile(indexFileHtml,
            Jade4J.render(getClass.getResource("books.jade"), Map("books" -> sortedEntriesJava)))

          load(indexFileHtml.toURI.toURL.toString)
        }


    }.asNotSimpleScenario)

  }
}
