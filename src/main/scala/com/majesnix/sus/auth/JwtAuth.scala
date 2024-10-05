package com.majesnix.sus.auth

import pdi.jwt._
import java.time.Instant

object JwtAuth {

  val claim = JwtClaim(content = """{"user":"John", "level":"basic"}""", expiration =
    Some(Instant.now.plusSeconds(157784760).getEpochSecond), issuedAt = Some(Instant.now.getEpochSecond))

  private val key = "secretKey"

  val algo = JwtAlgorithm.HS256

  val token = JwtCirce.encode(claim, key, algo)

}
