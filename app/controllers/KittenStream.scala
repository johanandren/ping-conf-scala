package controllers

import scala.concurrent.duration._
import akka.actor.{Props, Actor}
import play.api.mvc.{Action, Controller}
import play.api.libs.iteratee.{Concurrent, Enumerator, Enumeratee}
import play.api.libs.json.{Json, JsValue}
import play.api.libs.concurrent.Akka
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.Play.current
import play.api.libs.EventSource
import Concurrent.Channel

object KittenStream extends Controller {

  case class SendMeKittenEvents(channel: Channel[KittenEvent])
  case class StopSendingMeKittenEvents(channel: Channel[KittenEvent])
  case class KittenEvent(kind: String, text: String)

  class KittenEventStreamActor extends Actor {

    context.system.scheduler.schedule(10.seconds, 10.seconds, self, KittenEvent("sound", "mjau"))

    var listeners = Set[Channel[KittenEvent]]()

    def receive = {
      case SendMeKittenEvents(channel) => listeners += channel
      case StopSendingMeKittenEvents(channel) => listeners -= channel
      case evt: KittenEvent => listeners.foreach(_.push(evt))
    }
  }

  val kittenStreamActor = Akka.system.actorOf(Props(new KittenEventStreamActor))

  val eventToJson: Enumeratee[KittenEvent, JsValue] =
    Enumeratee.map { kittenEvent: KittenEvent =>
      Json.obj(
        "type" -> kittenEvent.kind,
        "text" -> kittenEvent.text
      ): JsValue
    }

  def streamKittenEvents() = Action {

    var channelToClose: Option[Channel[KittenEvent]] = None
    def unSubscribe() {
      channelToClose.foreach { channel =>
        kittenStreamActor ! StopSendingMeKittenEvents(channel)
      }
      channelToClose = None
    }

    val kittenStream: Enumerator[KittenEvent] = Concurrent.unicast(
      onStart = { channel =>
        channelToClose = Some(channel)
        kittenStreamActor ! SendMeKittenEvents(channel)
      },
      onError = (_, _) => unSubscribe(),
      onComplete = unSubscribe()
    )

    Ok.chunked(kittenStream through eventToJson through EventSource())
      .as(EVENT_STREAM)
  }

}
