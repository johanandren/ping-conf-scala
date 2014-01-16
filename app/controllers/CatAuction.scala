package controllers

import scala.concurrent.Future
import scala.concurrent.duration._
import akka.actor.{ActorRef, Props, Actor}
import akka.pattern.ask
import akka.util.Timeout
import play.api.mvc.{Action, Controller}
import play.api.Play.current
import play.api.libs.concurrent.Akka
import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.json.Json
import controllers.CatAuction.Auction.{WhoIsWinning, Bid}

object CatAuction extends Controller {

  object Auction {
    case class Bid(catName: String, amount: Int)
    case object WhoIsWinning
  }

  class Auction extends Actor {
    var highestBid = Bid("nobody", 0)
    def receive = {
      case bid: Bid if bid.amount > highestBid.amount =>
        highestBid = bid

      case WhoIsWinning => sender ! highestBid
    }
  }

  val catnipAuction: ActorRef = Akka.system.actorOf(Props(new Auction))

  def placeCatnipBid(catName: String, amount: Int) = Action {
    catnipAuction ! Bid(catName, amount)
    Ok("bid placed")
  }

  def highestBidForCatnip() = Action.async {
    implicit val timeout = Timeout(5.seconds)
    val eventualReply: Future[Bid] = (catnipAuction ? WhoIsWinning).mapTo[Bid]

    eventualReply.map { bid =>
      Ok(Json.obj("catName" -> bid.catName, "amount" -> bid.amount))
    }
  }

}