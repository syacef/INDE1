/*
package srvc_io.domain

import io.prometheus.client.hotspot.DefaultExports
import io.prometheus.client.exporter.HTTPServer
import io.prometheus.client.Counter

object MetricsService {
  val eventsCounter = Counter.build()
    .name("parking_events_total")
    .help("Total parking events produced")
    .labelNames("type")
    .register()

  def init(): Unit = {
    DefaultExports.initialize()
    new HTTPServer(EnvConfig.metricsPort, true)
  }
}
 */
