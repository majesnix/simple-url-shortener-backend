package com.majesnix.sus.models

case class UrlDTO(url: String)
case class ShortenResponse(short: String)
case class ShortUrl(short: String, url: String)

object UrlDTO {
  def valid(request: UrlDTO): Boolean = request.url.matches(
    "(https|http|ftp).*"
  ) && request.url.matches("^((?!dcl\\.re).)*$")
}
