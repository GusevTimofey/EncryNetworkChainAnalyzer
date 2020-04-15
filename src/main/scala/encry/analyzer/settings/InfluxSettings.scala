package encry.analyzer.settings

final case class InfluxSettings(
  dbName: String,
  url: String,
  port: Int,
  password: String,
  login: String
)
