package com.majesnix.sus.models

import com.typesafe.config.ConfigFactory

case class UrlDTO(url: String)
case class ShortenResponse(short: String)
case class ShortUrl(short: String, url: String)

object UrlDTO {
  private lazy val config = ConfigFactory.load().getConfig("server")
  def valid(url: String): Boolean = url.matches(
    "(https|http|ftp).*"
  ) && !url.contains(config.getString("url"))
}
