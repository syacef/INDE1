package srvc_stats.domain.entity

case class TimeSeriesPoint(
  timestamp: String,
  value: Double
)

case class TimeSeriesData(
  attribute: String,
  dataPoints: List[TimeSeriesPoint]
)

case class FileWithTimestamp(
  objectName: String,
  timestamp: String
)
