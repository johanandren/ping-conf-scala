package controllers

import play.api.mvc._
import play.api.libs.ws.WS
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json._

object Application extends Controller {

  case class Kitten(name: String)

  implicit val readsKitten: Reads[Kitten] = (__ \ "name").read[String].map(Kitten)

  def kittenProfile(kittenId: Long) = Action.async {
    WS.url(s"http://kittenapi.com/kitten/$kittenId").get()
      // if I had a web service response I would:
      .map { response =>
        if (response.status == OK) {
          val kitten = response.json.as[Kitten]
          Ok(views.html.kittenDetails(kitten))
        } else {
          throw new RuntimeException("Kitten server error")
        }
      }
  }

}