import sun.util.logging.PlatformLogger

object SetLoggerLevel {
  def setHttpURLConnectionLoggerLevelToFine(): Unit = {
    PlatformLogger
      .getLogger("sun.net.www.protocol.http.HttpURLConnection")
      .setLevel(PlatformLogger.Level.FINE)
  }
}
