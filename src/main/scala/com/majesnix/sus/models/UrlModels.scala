package com.majesnix.sus.models

import com.typesafe.config.ConfigFactory

import java.net.{InetAddress, URI}
import java.time.{OffsetDateTime, ZoneOffset}
import scala.util.Try

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
      // Unclaimed one-time links must not live forever; bound them to a month.
      case "1x" => Some(now.plusMonths(1))
      case _    => None
    }
  }
}

object UrlDTO {
  // The configured server url may be a bare hostname or include scheme/port
  // (e.g. "https://sus.example.com"); compare hosts, not raw strings.
  private[models] def hostOf(raw: String): String = {
    val withAuthority = if (raw.contains("://")) raw else s"//$raw"
    Try(Option(new URI(withAuthority).getHost)).toOption.flatten
      .getOrElse(raw)
      .toLowerCase
  }

  private lazy val serverHost = hostOf(
    ConfigFactory.load().getConfig("server").getString("url")
  )
  private val AllowedSchemes = Set("http", "https")
  private val MaxUrlLength = 2048

  def valid(url: String): Boolean =
    url.length <= MaxUrlLength &&
      Try {
        val uri = new URI(url)
        val scheme = Option(uri.getScheme).map(_.toLowerCase).getOrElse("")
        val host = Option(uri.getHost).getOrElse("")
        AllowedSchemes.contains(scheme) &&
        host.nonEmpty &&
        !host.equalsIgnoreCase(serverHost) &&
        !isPrivateHost(host)
      }.getOrElse(false)

  // Janitor sweep predicate: rows that are a hazard to keep serving (disallowed
  // scheme or a private/loopback target). Deliberately narrower than !valid so
  // stored links never disappear just because validation was tightened later.
  def dangerous(url: String): Boolean =
    Try {
      val uri = new URI(url)
      val scheme = Option(uri.getScheme).map(_.toLowerCase).getOrElse("")
      val host = Option(uri.getHost).getOrElse("")
      !AllowedSchemes.contains(scheme) || (host.nonEmpty && isPrivateHost(host))
    }.getOrElse(false)

  // Only resolves IP literals (no DNS) to avoid DNS-rebinding lookups.
  private def isPrivateHost(host: String): Boolean =
    if (host.equalsIgnoreCase("localhost")) true
    else if (host.contains(":"))
      // IPv6 literal (URI keeps the brackets); fail closed if it cannot be parsed.
      Try(InetAddress.getByName(host)).map(isPrivateAddress).getOrElse(true)
    else
      parseIpv4Literal(host) match {
        case Some(bytes) => isPrivateAddress(InetAddress.getByAddress(bytes))
        // Digits-and-dots hosts that are not a parseable IPv4 literal
        // (e.g. 999.999.999.999) are junk, never a resolvable hostname.
        case None => host.forall(c => c.isDigit || c == '.')
      }

  private def isPrivateAddress(addr: InetAddress): Boolean = {
    val bytes = addr.getAddress
    val uniqueLocalV6 =
      bytes.length == 16 && (bytes(0) & 0xfe) == 0xfc // fc00::/7
    val thisNetworkV4 = bytes.length == 4 && bytes(0) == 0 // 0.0.0.0/8
    addr.isLoopbackAddress || addr.isLinkLocalAddress || addr.isSiteLocalAddress ||
    addr.isAnyLocalAddress || uniqueLocalV6 || thisNetworkV4
  }

  // Parses IPv4 literals the way inet_aton does: 1-4 dot-separated parts, each
  // decimal, octal (leading 0) or hex (0x), the last part filling the remaining
  // bytes — so decimal (2130706433), shorthand (127.1) and hex (0x7f000001)
  // loopback spellings are all caught, without any DNS lookup.
  private def parseIpv4Literal(host: String): Option[Array[Byte]] = {
    def parsePart(p: String): Option[Long] =
      if (p.matches("0[xX][0-9a-fA-F]{1,8}"))
        Try(java.lang.Long.parseLong(p.drop(2), 16)).toOption
      else if (p.matches("0[0-7]{1,11}"))
        Try(java.lang.Long.parseLong(p.drop(1), 8)).toOption
      else if (p.matches("\\d{1,10}")) Try(p.toLong).toOption
      else None

    val parts = host.split("\\.", -1).toList
    if (parts.isEmpty || parts.length > 4 || parts.exists(_.isEmpty)) None
    else {
      val parsed = parts.map(parsePart)
      if (parsed.contains(None)) None
      else {
        val nums = parsed.flatten
        val leading = nums.init
        val last = nums.last
        val lastBytes = 4 - leading.length
        if (
          leading.exists(n =>
            n < 0 || n > 255
          ) || last < 0 || last >= (1L << (8 * lastBytes))
        ) None
        else {
          val value = (leading.foldLeft(0L)((acc, n) =>
            (acc << 8) | n
          ) << (8 * lastBytes)) | last
          Some(
            Array(
              (value >> 24).toByte,
              (value >> 16).toByte,
              (value >> 8).toByte,
              value.toByte
            )
          )
        }
      }
    }
  }
}
