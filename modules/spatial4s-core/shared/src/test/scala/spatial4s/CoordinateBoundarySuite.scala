package spatial4s

class CoordinateBoundarySuite extends munit.FunSuite:
  private def right[A](value: Either[SpatialError, A]): A =
    value.fold(e => fail(e.message), identity)

  test(
    "checked coordinate arithmetic refuses nonfinite output and keeps owner refusal first"
  ):
    val frame = right(Frame.named[D3]("head"))
    val other = right(Frame.named[D3]("head"))
    val p = right(Point.in(frame)(Double.MaxValue, 0.0, 0.0))
    val v = right(Vec.in(frame)(Double.MaxValue, 0.0, 0.0))
    assert(p.addChecked(v).left.exists(_.isInstanceOf[SpatialError.NonFiniteCoordinate]))
    assert(v.addChecked(v).left.exists(_.isInstanceOf[SpatialError.NonFiniteCoordinate]))
    assert(
      p.addChecked(right(Vec.in(other)(Double.MaxValue, 0.0, 0.0)))
        .left
        .exists(_.isInstanceOf[SpatialError.FrameMismatch])
    )
    intercept[ArithmeticException](p + v)
    val negative = right(Point.in(frame)(-Double.MaxValue, 0.0, 0.0))
    assert(p.subtractChecked(negative).isLeft)
    intercept[ArithmeticException](p - negative)

  test(
    "generic transport preserves live ownership and widened total arithmetic cannot mix owners"
  ):
    val a = right(Frame.named[D3]("a"))
    val b = right(Frame.named[D3]("b"))
    val pa = right(Point.in(a)(1.0, 2.0, 3.0))
    val vb = right(Vec.in(b)(1.0, 1.0, 1.0))
    val aa = right(Frame.alignOwners[D3, a.type, Frame[D3]](a, a))
    val bb = right(Frame.alignOwners[D3, b.type, Frame[D3]](b, b))
    val widenedPoint = right(aa.pointToRight(pa))
    val widenedVector = right(bb.vectorToRight(vb))
    assert(widenedPoint.addChecked(widenedVector).isLeft)
    intercept[IllegalArgumentException](widenedPoint + widenedVector)

  test("affine output overflow returns a typed coordinate error"):
    val op = right(
      AffineCoordinates.fromRows[D3](
        Vector(
          Vector(2.0, 0.0, 0.0, 0.0),
          Vector(0.0, 1.0, 0.0, 0.0),
          Vector(0.0, 0.0, 1.0, 0.0)
        )
      )
    )
    assert(
      op(Vector(Double.MaxValue, 0.0, 0.0)).left
        .exists(_.isInstanceOf[SpatialError.NonFiniteCoordinate])
    )
    assertEquals(right(op(Vector(1.0, 2.0, 3.0))), Vector(2.0, 2.0, 3.0))

  test("dynamic restoration preserves the checked dimension and rejects unsupported ranks"):
    val f = right(Frame.persistentNamed[D3](right(FrameId.parse("head")), "head"))
    val saved = right(f.record)
    val (some, registry) = right(Frame.restoreDynamic(saved, FrameRegistry.empty))
    assertEquals(some.dimension.rank, 3)
    assertEquals(some.value.persistentKey, Some(saved.key))
    assertEquals(registry.size, 1)
    assert(
      Frame
        .restoreDynamic(saved.copy(key = saved.key.copy(spatialRank = 4)), registry)
        .isLeft
    )
