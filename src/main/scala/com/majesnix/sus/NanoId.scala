package com.majesnix.sus

import java.security.SecureRandom
import scala.util.Random

object NanoId {
    private lazy val defaultNumberGenerator = new SecureRandom()

    private lazy val alphabet = "_~0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"

    private lazy val defaultAlphabet = alphabet.toCharArray.toVector

    private lazy val defaultSize = 21

  /**
   * Generates a nanoId with default size (21)
   * @return nanoId
   */
    def generateId: String =
      generateId(defaultNumberGenerator, defaultAlphabet, defaultSize)

  /**
   * Generates a nanoId with of the given size
   * @param size length of generated nanoId
   * @return nanoId
   */
    def generateId(size: Int): String =
      generateId(defaultNumberGenerator, defaultAlphabet, size)

  /**
   * Generates a nanoId with the given paramters
   * @param random number generator that will be used
   * @param alphabet alphabet that will be used
   * @param size length of generated nanoId
   * @return nanoId
   */
    def generateId(random: Random, alphabet: Vector[Char], size: Int): String =
      generateGeneric(size => {
        val bytes: Array[Byte] = new Array[Byte](size)
        random.nextBytes(bytes)
        bytes.toList
      }, alphabet, size).mkString("")

    private def generateGeneric(random: Int => List[Byte], alphabet: Vector[Char], size: Int): List[Char] = {
      val mask = (2 << (Math.log(alphabet.length - 1) / Math.log(2)).floor.round.toInt) - 1
      val step = (1.6 * mask * size / alphabet.length).ceil.round.toInt
      val bytes = random(step)

      bytes
        .map(_ & mask)
        .flatMap(a => alphabet.lift(a).toList)
        .take(size)
    }
}
