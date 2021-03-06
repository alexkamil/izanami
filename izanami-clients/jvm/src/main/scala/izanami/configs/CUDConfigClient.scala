package izanami.configs
import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.util.FastFuture
import izanami.commons.{HttpClient, IzanamiException}
import izanami.IzanamiDispatcher
import izanami.scaladsl.Config
import izanami.scaladsl.Config._
import play.api.libs.json.Json

import scala.concurrent.Future
import play.api.libs.json.JsValue
import scala.concurrent.ExecutionContext
import akka.http.scaladsl.model.HttpEntity
import akka.http.scaladsl.model.MediaType
import akka.http.scaladsl.model.HttpCharsets

object CUDConfigClient {
  def apply(client: HttpClient)(implicit izanamiDispatcher: IzanamiDispatcher, actorSystem: ActorSystem) =
    new CUDConfigClientImpl(client)
}

trait CUDConfigClient {

  implicit def ec: ExecutionContext

  def createConfig(config: Config): Future[Config] =
    createConfig(config.id, config.value)
      .flatMap { json =>
        json
          .validate[Config]
          .fold(
            { err =>
              val message = s"Error creating config ${config.id}, parsing error=$err"
              FastFuture.failed(IzanamiException(message))
            }, { FastFuture.successful }
          )
      }

  def createConfig(id: String, config: JsValue): Future[JsValue]

  def importConfigs(configs: Seq[Config]): Future[Unit]

  def updateConfig(id: String, config: Config): Future[Config] =
    updateConfig(id, config.id, config.value).flatMap { json =>
      json
        .validate[Config]
        .fold(
          { err =>
            val message = s"Error updating config ${config.id}, parsing error=$err"
            FastFuture.failed(IzanamiException(message))
          }, { FastFuture.successful }
        )
    }

  def updateConfig(oldId: String, id: String, config: JsValue): Future[JsValue]

  def deleteConfig(id: String): Future[Unit]
}

class CUDConfigClientImpl(client: HttpClient)(implicit val izanamiDispatcher: IzanamiDispatcher,
                                              actorSystem: ActorSystem)
    extends CUDConfigClient {

  private val logger = Logging(actorSystem, this.getClass.getName)

  override implicit val ec: ExecutionContext = izanamiDispatcher.ec

  override def createConfig(id: String, config: JsValue): Future[JsValue] = {
    val payload = Json.obj("id" -> id, "value" -> config)
    client
      .post("/api/configs", payload)
      .flatMap {
        case (status, json) if status == StatusCodes.Created =>
          FastFuture.successful(Json.parse(json))
        case (status, body) => {
          val message = s"Error creating config $id : status=$status, body=$payload response=$body"
          logger.error(message)
          FastFuture.failed(IzanamiException(message))
        }
      }(izanamiDispatcher.ec)
  }

  def importConfigs(configs: Seq[Config]): Future[Unit] = {
    val payload = configs.map(f => Json.toJsObject(f)).map(Json.stringify).mkString("\n")
    client
      .rawPost("/api/configs.ndjson",
               HttpEntity(MediaType.applicationWithFixedCharset("nd-json", HttpCharsets.`UTF-8`), payload))
      .map {
        case (status, body) =>
          if (status != StatusCodes.OK) {
            logger.debug(s"Fail to import feature $body")
          }
          ()
      }(izanamiDispatcher.ec)
  }

  override def updateConfig(oldId: String, id: String, config: JsValue): Future[JsValue] = {
    val payload = Json.obj("id" -> id, "value" -> config)
    client
      .put(s"/api/configs/$oldId", payload)
      .flatMap {
        case (status, json) if status == StatusCodes.OK =>
          FastFuture.successful(Json.parse(json))
        case (status, body) => {
          val message = s"Error updating config $id : status=$status, body=$payload response=$body"
          logger.error(message)
          FastFuture.failed(IzanamiException(message))
        }
      }(izanamiDispatcher.ec)
  }

  override def deleteConfig(id: String): Future[Unit] =
    client
      .delete(s"/api/configs/$id")
      .flatMap {
        case (status, json) if status == StatusCodes.OK || status == StatusCodes.NoContent =>
          FastFuture.successful(())
        case (status, body) => {
          val message = s"Error deleting config $id : status=$status, response=$body"
          logger.error(message)
          FastFuture.failed(IzanamiException(message))
        }
      }(izanamiDispatcher.ec)
}
