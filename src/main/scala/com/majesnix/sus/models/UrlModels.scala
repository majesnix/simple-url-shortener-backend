package com.majesnix.sus.models

import com.typesafe.config.ConfigFactory

import java.net.{InetAddress, URI}
import scala.util.Try
import scala.util.matching.Regex

case class UrlDTO(url: String)
case class ShortenResponse(short: String)
case class ShortUrl(short: String, url: String)

object UrlDTO {
  private lazy val serverHost = ConfigFactory.load().getConfig("server").getString("url")
  private val AllowedSchemes  = Set("http", "https")
  private val MaxUrlLength    = 2048
  // Matches bare IPv4 literals so we can inspect them without DNS resolution.
  private val Ipv4Literal: Regex = """^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$""".r

  def valid(url: String): Boolean =
    url.length <= MaxUrlLength &&
      Try {
        val uri    = new URI(url)
        val scheme = Option(uri.getScheme).map(_.toLowerCase).getOrElse("")
        val host   = Option(uri.getHost).getOrElse("")
        AllowedSchemes.contains(scheme) &&
        host.nonEmpty &&
        !host.equalsIgnoreCase(serverHost) &&
        !isPrivateHost(host)
      }.getOrElse(false)

  // Only resolves IP literals (no DNS) to avoid DNS-rebinding lookups.
  private def isPrivateHost(host: String): Boolean = {
    val isIpLiteral = Ipv4Literal.matches(host) || host.contains(":")
    if (host.equalsIgnoreCase("localhost")) true
    else if (isIpLiteral)
      Try(InetAddress.getByName(host))
        .map { addr =>
          addr.isLoopbackAddress || addr.isLinkLocalAddress || addr.isSiteLocalAddress ||
          host.startsWith("0.")
        }
        .getOrElse(false)
    else false
  }
}
