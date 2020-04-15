package encry.analyzer.settings

final case class ConsumerSettings(
  serverUrl: String,
  groupId: String,
  concurrentSize: Int
)
