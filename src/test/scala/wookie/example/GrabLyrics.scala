package wookie.example

import java.io.File
import java.net.URLEncoder

import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.StringUtils
import wookie._

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
object GrabLyrics {
  val defaultPanel = SearchAndStarWookieSimple.defaultPanel

  val FROM_LANGUAGE = "Spanish"
  val TO_LANGUAGE = "English"

  val HOST = "http://lyricstranslate.com"

  val LYRICS_JSON = new File("lyrics.json")

  def newPanel: WookiePanel = defaultPanel.apply()

  def main(args: Array[String])
  {
    val app = WookieSandboxApp.start()

    app.runOnStage(
      new WookieSimpleScenario("Find Popular Foreign Lyrics!", defaultPanel) {
        override def run(): Unit = {
          var grabState = if(!LYRICS_JSON.exists()) {
            new GrabLyricsState()
          } else {
            JsonObjectUtils.fromJson[GrabLyricsState](FileUtils.readFileToString(LYRICS_JSON))
          }

          if(grabState.state == "finished") {
            return
          }

          val continueFromPage = grabState.lastUrl

          if(!continueFromPage.isDefined) {
            load(s"$HOST/en/translations")

            $("#edit-formtree-ltsearchlanguagefrom-hierarchical-select-selects-0").asSelect()
              .findOptionByText(FROM_LANGUAGE).get.select()

            $("#edit-formtree-ltsearchlanguageto-hierarchical-select-selects-0").asSelect()
              .findOptionByText(TO_LANGUAGE).get.select()

            $("#edit-formtree-submit").submit()
          } else {
            load(continueFromPage.get)
          }

          var hasNextPage = true

          while(hasNextPage) {
            val table = $("#lyricstranslatesearch-searchtranslate-form .ltsearch-results-line .sticky-table")

            val rows = table.find("tr")

            for(row <- rows.tail) {
              val thanksText = row.find("td.ltsearch-translatetotalview")(0).text()

              if(thanksText.contains("thanked")) {
                val thanks = StringUtils.substringBetween(thanksText, "thanked ", " ").toInt

                if(thanks >= 10) {
                  val artistLink = row.find("td.ltsearch-translatenameoriginal")(0).find("a")(0)
                  val songLink = row.find("td.ltsearch-translatenameoriginal")(1).find("a")(0)

                  val song = SongState(
                    song = songLink.text().trim,
                    songUrl = s"$HOST/${songLink.attr("href")}",
                    artist = artistLink.text().trim,
                    artistUrl = s"$HOST/${artistLink.attr("href")}",
                    thanked = thanks
                  )

                  grabState = grabState.copy(
                    songs =  grabState.songs :+ song
                  )
                }
              }
            }

            val nextLink = $("#lyricstranslatesearch-searchtranslate-form ul.pager li").findResultWithText("next")

            hasNextPage = nextLink.isDefined

            if(hasNextPage) {
              val nextLinkA = nextLink.get.find("a")(0)
              grabState = grabState.copy(lastUrl = Some(s"$HOST/${nextLinkA.attr("href")}"))
              nextLinkA.followLink()
            }

            FileUtils.writeStringToFile(LYRICS_JSON, JsonObjectUtils.toJson(grabState, pretty = true))

            writeHtml(grabState)
          }
        }
      }.asNotSimpleScenario)
  }

  def writeHtml(grabState: GrabLyricsState) = {
    val htmlStr = grabState.songs.map { song =>
      s"""
        |(${song.thanked})
        | <a href="${song.songUrl}">${song.song}</a>
        | <a href="${song.artistUrl}">${song.artist}</a>
        | <a href="http://www.youtube.com/results?search_query=${URLEncoder.encode(s"${song.artist} ${song.song}", "UTF-8")}">youtube</a>
      """.stripMargin
    }
      .mkString("<br>\n")

    FileUtils.writeStringToFile(new File("lyrics-2.html"), htmlStr)
  }
}

case class GrabLyricsState(
  lastUrl: Option[String] = None,
  songs: List[SongState] = List(),
  state: String = "running"
)

case class SongState(
  song: String,
  songUrl: String,
  artist: String,
  artistUrl: String,
  thanked: Int
)

