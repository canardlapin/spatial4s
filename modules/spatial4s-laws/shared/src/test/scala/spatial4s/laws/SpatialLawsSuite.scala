package spatial4s.laws

import org.scalacheck.Gen
import org.scalacheck.Prop.forAll
import spatial4s.*

class SpatialLawsSuite extends munit.ScalaCheckSuite:
  property("point translation and subtraction are inverse"):
    forAll(finite, finite, finite, finite) { (x, y, dx, dy) =>
      val frame = right(Frame.named[D2]("law-frame"))
      val point = right(Point.in(frame)(x, y))
      val vector = right(Vec.in(frame)(dx, dy))
      val recovered = ((point + vector) - point).coordinates

      assertApproximately(
        recovered,
        vector.coordinates,
        Vector(x, y)
      )
    }

  property("identity affine leaves coordinates unchanged"):
    forAll(finite, finite, finite) { (x, y, z) =>
      val coordinates = Vector(x, y, z)
      assertEquals(
        right(AffineCoordinates.identity[D3](coordinates)),
        coordinates
      )
    }

  test("alignment identity, inverse, and composition preserve coordinates"):
    val id = right(FrameId.parse("alignment-law"))
    val seed = right(Frame.persistentNamed[D3](id, "alignment"))
    val record = right(seed.record)
    val a = right(Frame.restore[D3](record, FrameRegistry.empty)).frame
    val b = right(Frame.restore[D3](record, FrameRegistry.empty)).frame
    val c = right(Frame.restore[D3](record, FrameRegistry.empty)).frame
    val ab = right(Frame.align(a, b))
    val bc = right(Frame.align(b, c))
    val ac = right(ab.andThen(bc))
    val point = right(Point.in(a)(1.0, 2.0, 3.0))

    val viaComposition = right(ac.pointToRight(point))
    val viaStages = right(bc.pointToRight(right(ab.pointToRight(point))))
    val roundTrip = right(ac.inverse.pointToRight(viaComposition))

    assertEquals(viaComposition.coordinates, viaStages.coordinates)
    assertEquals(roundTrip.coordinates, point.coordinates)
    assertEquals(
      right(FrameAlignment.identity(a).pointToRight(point)).coordinates,
      point.coordinates
    )

  private val finite: Gen[Double] =
    Gen.chooseNum(-1000000.0, 1000000.0)

  private def assertApproximately(
      obtained: Vector[Double],
      expected: Vector[Double],
      base: Vector[Double]
  ): Unit =
    obtained.indices.foreach { axis =>
      val scale =
        1.0 + math.abs(base(axis)) + math.abs(expected(axis))
      val error = math.abs(obtained(axis) - expected(axis))
      assert(
        error <= 1e-12 * scale,
        s"axis $axis error $error exceeded scale-aware tolerance ${1e-12 * scale}"
      )
    }

  private def right[A](value: Either[SpatialError, A]): A =
    value.fold(error => fail(error.message), identity)
