package com.wonder_soft.mcp.redmine.util

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object Logger {
  private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

  private val logLevel: String = sys.env.getOrElse("LOG_LEVEL", "INFO").toUpperCase

  private val levels = Map(
    "DEBUG" -> 0,
    "INFO" -> 1,
    "WARN" -> 2,
    "ERROR" -> 3
  )

  private def currentLevel: Int = levels.getOrElse(logLevel, 1)

  private def log(level: String, message: String): Unit = {
    if (levels.getOrElse(level, 1) >= currentLevel) {
      val timestamp = LocalDateTime.now().format(formatter)
      System.err.println(s"[$timestamp] [$level] $message")
    }
  }

  def debug(message: String): Unit = log("DEBUG", message)
  def info(message: String): Unit = log("INFO", message)
  def warn(message: String): Unit = log("WARN", message)
  def error(message: String): Unit = log("ERROR", message)
  def error(message: String, throwable: Throwable): Unit = {
    log("ERROR", s"$message: ${throwable.getMessage}")
    if (currentLevel == 0) {
      throwable.printStackTrace(System.err)
    }
  }
}
