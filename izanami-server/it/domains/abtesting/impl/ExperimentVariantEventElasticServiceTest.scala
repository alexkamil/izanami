package domains.abtesting.impl

import cats.effect.IO
import domains.abtesting.{AbstractExperimentServiceTest, ExperimentVariantEventService}
import domains.events.impl.BasicEventStore
import elastic.api.Elastic
import env.{DbDomainConfig, DbDomainConfigDetails, ElasticConfig}
import org.scalactic.source.Position
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll}
import play.api.libs.json.JsValue
import store.elastic.ElasticClient

class ExperimentVariantEventElasticServiceTest extends AbstractExperimentServiceTest("Elastic") with BeforeAndAfter with BeforeAndAfterAll {

  private val config = ElasticConfig("localhost", 9210, "http", None, None, true)
  val elastic: Elastic[JsValue] = ElasticClient(config, system)

  override def dataStore(name: String): ExperimentVariantEventService[IO] = ExperimentVariantEventElasticService[IO](
    elastic, config, DbDomainConfig(env.Elastic, DbDomainConfigDetails(name, None), None), new BasicEventStore[IO]
  )

  override protected def before(fun: => Any)(implicit pos: Position): Unit = {
    super.before(fun)
    cleanUpElastic
  }

  private def cleanUpElastic = {
    import _root_.elastic.codec.PlayJson._
    elastic.deleteIndex("*").futureValue
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    cleanUpElastic
  }

}