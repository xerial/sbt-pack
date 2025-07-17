package xerial.sbt

import wvlet.airspec.AirSpec
import xerial.sbt.pack.{DefaultVersionStringOrdering, VersionString}

import scala.math.Ordered.orderingToOrdered

class VersionStringSpec extends AirSpec {
  implicit val versionStringOrdering: Ordering[VersionString] = DefaultVersionStringOrdering

  test("VersionString") {
    test("accept any string") {
      VersionString("1.0")
      VersionString("1.0-alpha")
      VersionString("1")
      VersionString("-alpha")
      VersionString("1231892")
      VersionString("asd;.a2,.-")
    }

    test("properly deconstruct arbitrary string") {
      VersionString("1") shouldBe VersionString.fromNumbers(1 :: Nil, None)
      VersionString("1.2") shouldBe VersionString.fromNumbers(1 :: 2 :: Nil, None)
      VersionString("1.2.3") shouldBe VersionString.fromNumbers(1 :: 2 :: 3 :: Nil, None)
      VersionString("1.2.3.4") shouldBe VersionString.fromNumbers(1 :: 2 :: 3 :: 4 :: Nil, None)
      VersionString("1.2.3.4-alpha") shouldBe VersionString.fromNumbers(1 :: 2 :: 3 :: 4 :: Nil, Some("alpha"))
      VersionString("1.2.3.4-alpha-beta") shouldBe VersionString.fromNumbers(
        1 :: 2 :: 3 :: 4 :: Nil,
        Some("alpha-beta")
      )
      VersionString("foo") shouldBe VersionString(List.empty[String], Some("foo"))
      VersionString("foo.bar") shouldBe VersionString(List.empty[String], Some("foo.bar"))
      VersionString("foo.bar-alpha") shouldBe VersionString(List.empty[String], Some("foo.bar-alpha"))
    }

    test("properly sort") {
      VersionString("1") < VersionString("1.2.3.4") shouldBe true
      VersionString("2") > VersionString("1.2.3.4") shouldBe true
      VersionString("1.2.2") < VersionString("1.2.3.4") shouldBe true
      VersionString("1.2.4") > VersionString("1.2.3.4") shouldBe true
      VersionString("1.2.3.4.5") > VersionString("1.2.3.4") shouldBe true

      VersionString("2.9.2") < VersionString("2.10.4") shouldBe true

      VersionString("1.2") > VersionString("1.2-alpha") shouldBe true
      VersionString("1.2-beta") > VersionString("1.2-alpha") shouldBe true

      VersionString("apple") < VersionString("pie") shouldBe true
    }

    test("preserve 0-padding in version strings") {
      val v = VersionString("1.09")
      v.major shouldBe "1"
      v.minor shouldBe Some("09")
    }
  }
}
