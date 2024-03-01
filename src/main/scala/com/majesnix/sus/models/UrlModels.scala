package com.majesnix.sus.models

case class Url(url: String)
case class CreateShortUrlResponse(short: String)
case class ShortUrl(short: String, url: String)

object Url {
  def valid(url: Url): Boolean = url.url.matches(
    "(https|http|ftp).*"
  ) && url.url.matches("^((?!dcl\\.re).)*$")
}
