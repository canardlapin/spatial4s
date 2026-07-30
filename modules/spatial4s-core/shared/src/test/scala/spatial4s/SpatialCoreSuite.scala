package spatial4s

import scala.compiletime.testing.typeCheckErrors

class SpatialCoreSuite extends munit.FunSuite:
  test("units and conventions admit validated extensions"):
    val quantity = right(CoordinateQuantity.custom("frequency"))
    val unit =
      right(CoordinateUnit.custom("hertz", "Hz", quantity, Some(1.0)))
    val convention = right(CoordinateConvention.custom("scanner-native"))

    assertEquals(unit.quantity, quantity)
    assertEquals(unit.scaleToCanonical, Some(1.0))
    assertEquals(convention.id, "scanner-native")
    assert(CoordinateUnit.custom("bad unit", "x", quantity, None).isLeft)
    assert(CoordinateConvention.custom(" scanner").isLeft)

  test("ephemeral and persistent frame identity remain distinct"):
    val ephemeral = right(Frame.named[D3]("ephemeral"))
    assertEquals(ephemeral.record, Left(SpatialError.EphemeralFrameHasNoRecord))

    val id = right(FrameId.parse("surface-ras"))
    val persistent =
      right(
        Frame.persistentNamed[D3](
          id,
          "surface RAS",
          convention = CoordinateConvention.RAS
        )
      )
    assertEquals(right(persistent.record).key.id, id)
    assert(!ephemeral.sameRuntimeOwnerAs(persistent))

  test("restoration canonicalizes only within a threaded registry"):
    val original = persistentFrame()
    val record = right(original.record)
    val first = right(Frame.restore[D3](record, FrameRegistry.empty))
    val repeated = right(Frame.restore[D3](record, first.registry))
    val independent = right(Frame.restore[D3](record, FrameRegistry.empty))

    assert(first.frame eq repeated.frame)
    assert(!first.frame.sameRuntimeOwnerAs(independent.frame))

    val alignment = right(Frame.align(first.frame, independent.frame))
    val point = right(Point.in(first.frame)(1.0, 2.0, 3.0))
    val transported = right(alignment.pointToRight(point))
    assertEquals(transported.coordinates, point.coordinates)
    assert(transported.belongsTo(independent.frame))

  test("restoration rejects rank conflicts and registries reject duplicate owners"):
    val first = persistentFrame()
    val record = right(first.record)
    assert(Frame.restore[D2](record, FrameRegistry.empty).isLeft)

    val registered = right(FrameRegistry.empty.register(first))
    val second =
      right(
        Frame.persistentNamed[D3](
          record.key.id,
          "independently created owner"
        )
      )
    assert(registered.register(second).isLeft)

  test("point and vector operations retain one stable owner"):
    val frame = right(Frame.named[D2]("plane"))
    val point = right(Point.in(frame)(1.0, 2.0))
    val vector = right(Vec.in(frame)(3.0, 5.0))
    val moved = point + vector

    assertEquals(moved.coordinates, Vector(4.0, 7.0))
    assertEquals((moved - point).coordinates, vector.coordinates)
    assert(Point.in(frame)(Double.NaN, 0.0).isLeft)

  test("different frame owners cannot use total coordinate operations"):
    val errors = typeCheckErrors("""
      import spatial4s.*
      val left = Frame.named[D2]("left").toOption.get
      val right = Frame.named[D2]("right").toOption.get
      val point = Point.in(left)(0.0, 0.0).toOption.get
      val vector = Vec.in(right)(1.0, 1.0).toOption.get
      point + vector
    """)
    assert(errors.nonEmpty)

  test("affine coefficients validate shape, finiteness, and application"):
    val affine =
      right(
        AffineCoordinates.fromRows[D2](
          Vector(
            Vector(2.0, 0.0, 3.0),
            Vector(0.0, 4.0, 5.0)
          )
        )
      )
    assertEquals(right(affine(Vector(7.0, 11.0))), Vector(17.0, 49.0))
    assertEquals(
      right(AffineCoordinates.identity[D2](Vector(7.0, 11.0))),
      Vector(7.0, 11.0)
    )
    assert(
      AffineCoordinates
        .fromRows[D2](Vector(Vector(1.0, 0.0), Vector(0.0, 1.0)))
        .isLeft
    )
    assert(
      AffineCoordinates
        .fromRows[D2](
          Vector(
            Vector(1.0, 0.0, Double.PositiveInfinity),
            Vector(0.0, 1.0, 0.0)
          )
        )
        .isLeft
    )

  private def persistentFrame(): Frame[D3] =
    val id = right(FrameId.parse("canonical-frame"))
    right(Frame.persistentNamed[D3](id, "canonical"))

  private def right[A](value: Either[SpatialError, A]): A =
    value.fold(error => fail(error.message), identity)
