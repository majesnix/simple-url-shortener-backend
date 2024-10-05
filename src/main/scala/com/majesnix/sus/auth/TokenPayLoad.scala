package com.majesnix.sus.auth

import io.circe._

case class TokenPayLoad(user: String, level: String)

object TokenPayLoad()(implicit decoder: Decoder[TokenPayLoad] ) {
  Decoder.instance { h =>
    for {
      user <- h.get[String]("user")
      level <- h.get[String]("level")
    } yield TokenPayLoad(user,level)
  }
}