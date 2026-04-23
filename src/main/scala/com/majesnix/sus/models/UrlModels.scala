package com.majesnix.sus.models

import com.typesafe.config.ConfigFactory

import java.net.URI
import scala.util.Try

case class UrlDTO(url: String)
case class ShortenResponse(short: String)
case class ShortUrl(short: String, url: String)

object UrlDTO {
  private lazy val serverHost = ConfigFactory.load().getConfig("server").getString("url")
  private val AllowedSchemes = Set("http", "https", "ftp")

  def valid(url: String): Boolean =
    Try {
      val uri    = new URI(url)
      val scheme = Option(uri.getScheme).map(_.toLowerCase).getOrElse("")
      val host   = Option(uri.getHost).getOrElse("")
      AllowedSchemes.contains(scheme) &&
      host.nonEmpty &&
      !host.equalsIgnoreCase(serverHost)
    }.getOrElse(false)
}
