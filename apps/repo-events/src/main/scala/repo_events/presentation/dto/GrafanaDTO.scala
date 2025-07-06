package repo_events.presentation.dto

case class MetricOption(
  label: String,
  value: String,
  payloads: List[PayloadConfig] = List.empty
)

case class PayloadConfig(
  label: String,
  name: String,
  `type`: String = "select",
  placeholder: String = "",
  reloadMetric: Boolean = false,
  width: Int = 10,
  options: List[SelectOption] = List.empty
)

case class SelectOption(
  label: String,
  value: String
)

case class QueryRequest(
  panelId: String,
  range: TimeRange,
  interval: String,
  intervalMs: Int,
  maxDataPoints: Int,
  targets: List[QueryTarget]
)

case class TimeRange(
  from: String,
  to: String
)

case class QueryTarget(
  target: String,
  refId: String,
  payload: Option[Map[String, String]] = None
)

case class TimeSeriesResponse(
  target: String,
  datapoints: List[List[Double]]
)

case class MetricPayloadRequest(
  metric: String,
  payload: Map[String, String],
  name: String
)
