package wookie.example

import java.io.{FileWriter, PrintWriter}
import java.util.Date

import org.apache.commons.lang3.StringUtils
import wookie._

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
object GrabLyrics {
  val defaultPanel = SearchAndStarWookieSimple.defaultPanel

  def newPanel: WookiePanel = defaultPanel.apply()

  def main(args: Array[String])
  {
    val app = WookieSandboxApp.start()

    val writer = new PrintWriter(new FileWriter("lyrics.log", true))

    writer.println(s"session started at ${new Date()}")

    app.runOnStage(
      new WookieSimpleScenario("Find Popular Foreign Lyrics!", defaultPanel) {
        override def run(): Unit = {
          load("http://lyricstranslate.com/en/translations")

          $("#edit-formtree-ltsearchlanguagefrom-hierarchical-select-selects-0").asSelect()
            .findOptionByText("Spanish").get.select()

          $("#edit-formtree-ltsearchlanguageto-hierarchical-select-selects-0").asSelect()
            .findOptionByText("English").get.select()

          $("#edit-formtree-submit").submit()

          var hasNextPage = true

          while(hasNextPage) {
            val table = $("#lyricstranslatesearch-searchtranslate-form .ltsearch-results-line .sticky-table")

            val rows = table.find("tr")

            for(row <- rows.tail) {
              val thanksText = row.find("td.ltsearch-translatetotalview")(0).text()

              if(thanksText.contains("thanked")) {
                val thanks = StringUtils.substringBetween(thanksText, "thanked ", " ").toInt

                if(thanks >= 10) {
                  val artistLink = row.find("td.ltsearch-translatenameoriginal")(0).find("a")(0).html()
                  val songLink = row.find("td.ltsearch-translatenameoriginal")(1).find("a")(0).html()

                  writer.println(s"""$songLink ($thanks) by $artistLink""")
                }
              }
            }

            writer.flush()

            val nextLink = $("#lyricstranslatesearch-searchtranslate-form ul.pager li").findResultWithText("next")

            hasNextPage = nextLink.isDefined

            if(hasNextPage) {
              nextLink.get.find("a")(0).followLink()
            }
          }
        }
      }.asNotSimpleScenario)
  }
}
