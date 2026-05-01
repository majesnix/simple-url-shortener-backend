package com.majesnix.sus.models

import com.typesafe.config.ConfigFactory

import java.net.{InetAddress, URI}
import java.time.{OffsetDateTime, ZoneOffset}
import scala.util.Try
import scala.util.matching.Regex

case class UrlDTO(url: String)
case class ShortenResponse(short: String)
case class CreateUrlRequest(url: String, expiry: Option[String] = None)

object CreateUrlRequest {
  private val AllowedExpiry = Set("1d", "1w", "1m", "1y", "unlimited", "1x")

  def validExpiry(expiry: Option[String]): Boolean =
    expiry.forall(AllowedExpiry.contains)

  def isOneTime(expiry: Option[String]): Boolean = expiry.contains("1x")

  def toExpiresAt(expiry: Option[String]): Option[OffsetDateTime] = {
    val now = OffsetDateTime.now(ZoneOffset.UTC)
    expiry.flatMap {
      case "1d"        => Some(now.plusDays(1))
      case "1w"        => Some(now.plusWeeks(1))
      case "1m"        => Some(now.plusMonths(1))
      case "1y"        => Some(now.plusYears(1))
      case "unlimited" => None
      case "1x"        => None
      case _           => None
    }
  }
}

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
