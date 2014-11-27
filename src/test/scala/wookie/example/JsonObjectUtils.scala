package wookie.example

import java.text.SimpleDateFormat
import java.util.TimeZone

import org.json4s._
import org.json4s.ext.EnumNameSerializer
import org.json4s.jackson.JsonMethods._
import org.json4s.jackson.Serialization

import scala.util.control.NonFatal

/**
 * @author Andrey Chaschev chaschev@gmail.com
 */
object JsonObjectUtils {
  implicit val formats: Formats =
    new DefaultFormats {
      override val dateFormatter = {
        val formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        formatter.setTimeZone(TimeZone.getTimeZone("UTC"))
        formatter
      }
    } + new EnumNameSerializer(SimpleCrawlerState)


  def toJson(obj: AnyRef, pretty:Boolean = false): String =
  {
    if(pretty) {
      Serialization.writePretty(obj)
    } else {
      Serialization.write(obj)
    }
  }

  /**
   * Converts JSON to Scala object (case class, Map, Seq etc.).
   * If you want to do more complicated things, you should use JSON4S directly:
   * https://github.com/json4s/json4s
   */
  def fromJson[T](jsonString: String)(implicit m: Manifest[T]): T = {
    // Serialization.read doesn't work without type hints.
    //
    // Serialization.read[Map[String, Any]]("""{"name": "X", "age": 45}""")
    // will throw:
    // org.json4s.package$MappingException: No information known about type
    //
    // JsonMethods.parse works for the above.
    if (m.runtimeClass.getName.startsWith("scala")) {
      try {
        parse(jsonString, useBigDecimalForDouble = true).extract[T]
      } catch {
        case NonFatal(e) =>
          Serialization.read[T](jsonString)
      }
    } else {
      try {
        Serialization.read[T](jsonString)
      } catch {
        case NonFatal(e) =>
          val p = parse(jsonString, useBigDecimalForDouble = true)
          p.extract
      }
    }
  }
}
