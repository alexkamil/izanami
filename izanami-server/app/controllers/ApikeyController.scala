package controllers

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import akka.util.ByteString
import cats.effect.Effect
import controllers.actions.SecuredAuthContext
import domains.apikey.{Apikey, ApikeyInstances, ApikeyService}
import domains.{Import, IsAllowed, Key}
import libs.functional.EitherTSyntax
import libs.patch.Patch
import libs.logs.IzanamiLogger
import play.api.http.HttpEntity
import play.api.libs.json.{JsValue, Json}
import play.api.mvc._
import store.Result.AppErrors

class ApikeyController[F[_]: Effect](apikeyStore: ApikeyService[F],
                                     system: ActorSystem,
                                     AuthAction: ActionBuilder[SecuredAuthContext, AnyContent],
                                     val cc: ControllerComponents)
    extends AbstractController(cc)
    with EitherTSyntax[F] {

  import cats.implicits._
  import libs.functional.syntax._
  import libs.http._
  import system.dispatcher

  implicit val materializer = ActorMaterializer()(system)

  def list(pattern: String, page: Int = 1, nbElementPerPage: Int = 15): Action[AnyContent] =
    AuthAction.asyncF[F] { ctx =>
      import ApikeyInstances._
      val patternsSeq: Seq[String] = ctx.authorizedPatterns :+ pattern

      apikeyStore
        .getByIdLike(patternsSeq, page, nbElementPerPage)
        .map { r =>
          Ok(
            Json.obj(
              "results" -> Json.toJson(r.results),
              "metadata" -> Json.obj(
                "page"     -> page,
                "pageSize" -> nbElementPerPage,
                "count"    -> r.count,
                "nbPages"  -> r.nbPages
              )
            )
          )
        }
    }

  def create(): Action[JsValue] = AuthAction.asyncEitherT(parse.json) { ctx =>
    import ApikeyInstances._

    for {
      apikey <- ctx.request.body.validate[Apikey] |> liftJsResult(err => BadRequest(AppErrors.fromJsError(err).toJson))
      _ <- IsAllowed[Apikey].isAllowed(apikey)(ctx.auth) |> liftBooleanTrue(
            Forbidden(AppErrors.error("error.forbidden").toJson)
          )
      event <- apikeyStore.create(Key(apikey.clientId), apikey) |> mapLeft(err => BadRequest(err.toJson))
    } yield Created(Json.toJson(apikey))

  }

  def get(id: String): Action[AnyContent] = AuthAction.asyncEitherT { ctx =>
    import ApikeyInstances._

    val key = Key(id)
    for {
      apikey <- apikeyStore.getById(key) |> liftFOption[Result, Apikey](NotFound)
      _ <- IsAllowed[Apikey].isAllowed(apikey)(ctx.auth) |> liftBooleanTrue(
            Forbidden(AppErrors.error("error.forbidden").toJson)
          )
    } yield Ok(Json.toJson(apikey))

  }

  def update(id: String): Action[JsValue] = AuthAction.asyncEitherT(parse.json) { ctx =>
    import ApikeyInstances._

    for {
      apikey <- ctx.request.body.validate[Apikey] |> liftJsResult(err => BadRequest(AppErrors.fromJsError(err).toJson))
      _ <- IsAllowed[Apikey].isAllowed(apikey)(ctx.auth) |> liftBooleanTrue(
            Forbidden(AppErrors.error("error.forbidden").toJson)
          )
      event <- apikeyStore
                .update(Key(id), Key(apikey.clientId), apikey) |> mapLeft(err => BadRequest(err.toJson))
    } yield Ok(Json.toJson(apikey))

  }

  def patch(id: String): Action[JsValue] = AuthAction.asyncEitherT(parse.json) { ctx =>
    import ApikeyInstances._

    val key = Key(id)
    for {
      current <- apikeyStore.getById(key) |> liftFOption[Result, Apikey](NotFound)
      _ <- IsAllowed[Apikey].isAllowed(current)(ctx.auth) |> liftBooleanTrue(
            Forbidden(AppErrors.error("error.forbidden").toJson)
          )
      updated <- Patch.patch(ctx.request.body, current) |> liftJsResult(
                  err => BadRequest(AppErrors.fromJsError(err).toJson)
                )
      event <- apikeyStore
                .update(key, Key(current.clientId), updated) |> mapLeft(err => BadRequest(err.toJson))
    } yield Ok(Json.toJson(updated))

  }

  def delete(id: String): Action[AnyContent] = AuthAction.asyncEitherT { ctx =>
    import ApikeyInstances._

    val key = Key(id)
    for {
      apikey <- apikeyStore.getById(key) |> liftFOption[Result, Apikey](NotFound)
      _ <- IsAllowed[Apikey].isAllowed(apikey)(ctx.auth) |> liftBooleanTrue(
            Forbidden(AppErrors.error("error.forbidden").toJson)
          )
      deleted <- apikeyStore.delete(key) |> mapLeft(err => BadRequest(err.toJson))
    } yield Ok(Json.toJson(apikey))

  }

  def deleteAll(patterns: Option[String]): Action[AnyContent] =
    AuthAction.asyncEitherT { ctx =>
      val allPatterns = patterns.toList.flatMap(_.split(","))

      for {
        deletes <- apikeyStore.deleteAll(allPatterns) |> mapLeft(err => BadRequest(err.toJson))
      } yield Ok

    }

  def count(): Action[AnyContent] = AuthAction.asyncF[F] { ctx =>
    val patterns: Seq[String] = ctx.authorizedPatterns
    apikeyStore.count(patterns).map { count =>
      Ok(Json.obj("count" -> count))
    }
  }

  def download(): Action[AnyContent] = AuthAction { ctx =>
    import ApikeyInstances._
    val source = apikeyStore
      .getByIdLike(ctx.authorizedPatterns)
      .map { case (_, data) => Json.toJson(data) }
      .map(Json.stringify _)
      .intersperse("", "\n", "\n")
      .map(ByteString.apply)
    Result(
      header = ResponseHeader(200, Map("Content-Disposition" -> "attachment", "filename" -> "apikeys.dnjson")),
      body = HttpEntity.Streamed(source, None, Some("application/json"))
    )
  }

  def upload() = AuthAction.async(Import.ndJson) { ctx =>
    ctx.body
      .via(apikeyStore.importData)
      .map {
        case r if r.isError => BadRequest(Json.toJson(r))
        case r              => Ok(Json.toJson(r))
      }
      .recover {
        case e: Throwable =>
          IzanamiLogger.error("Error importing file", e)
          InternalServerError
      }
      .runWith(Sink.head)
  }

}
