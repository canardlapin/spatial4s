package spatial4s

import scala.compiletime.testing.typeCheckErrors

class XyzSuite extends munit.FunSuite:
  private def right[A](value: Either[SpatialError, A]): A = value.fold(e => fail(e.message), identity)

  test("raw triples retain structural values and exact coordinate bits"):
    val value = right(Xyz.fromVector(Vector(-0.0, 2.0, 3.0)))
    assertEquals(java.lang.Double.doubleToRawLongBits(value.x), Long.MinValue)
    assertEquals(value, Xyz(-0.0, 2.0, 3.0))
    assertEquals(value.toVector, Vector(-0.0, 2.0, 3.0))
    assertEquals(value.coordinate(2), Some(3.0))
    assertEquals(value.coordinate(-1), None)
    assertEquals(value.coordinate(3), None)

  test("all construction paths reject wrong shape and nonfinite coordinates"):
    assertEquals(Xyz.fromVector(Vector(1.0, 2.0)), Left(SpatialError.DimensionMismatch(3, 2)))
    assertEquals(Xyz.fromVector(Vector(1.0, 2.0, 3.0, 4.0)), Left(SpatialError.DimensionMismatch(3, 4)))
    for
      axis <- 0 until 3
      bad <- Vector(Double.NaN, Double.PositiveInfinity, Double.NegativeInfinity)
    do
      Xyz.fromVector(Vector(1.0, 2.0, 3.0).updated(axis, bad)) match
        case Left(SpatialError.NonFiniteCoordinate(actual, _)) => assertEquals(actual, axis)
        case other => fail(other.toString)
    intercept[IllegalArgumentException](Xyz(Double.NaN, 0.0, 0.0))
    intercept[IllegalArgumentException](Xyz.Zero.copy(z = Double.PositiveInfinity))

  test("raw arithmetic retains the literal surface interchange operations"):
    val a = Xyz(1.0, 2.0, 3.0)
    val b = Xyz(4.0, 5.0, 6.0)
    assertEquals(a + b, Xyz(5.0, 7.0, 9.0))
    assertEquals(b - a, Xyz(3.0, 3.0, 3.0))
    assertEquals(a * 2.0, Xyz(2.0, 4.0, 6.0))
    assertEqualsDouble(a.dot(b), 32.0, 0.0)
    assertEquals(a.cross(b), Xyz(-3.0, 6.0, -3.0))
    assertEqualsDouble(Xyz(3.0, 4.0, 0.0).norm, 5.0, 0.0)
    val unit = Xyz(3.0, 4.0, 0.0).normalized
    assertEqualsDouble(unit.x, 0.6, 1e-15)
    assertEqualsDouble(unit.y, 0.8, 1e-15)
    assertEquals(Xyz.Zero.normalized, Xyz.Zero)
    intercept[IllegalArgumentException](Xyz(Double.MaxValue, 0.0, 0.0) * 2.0)

  test("explicit point declaration retains the supplied owner without inferring alignment"):
    val left = right(Frame.named[D3]("left"))
    val other = right(Frame.named[D3]("other"))
    val raw = Xyz(1.0, 2.0, 3.0)
    val point: Point[left.type, D3] = right(raw.in(left))
    assert(point.belongsTo(left))
    assert(!point.belongsTo(other))
    assertEquals(right(Xyz.fromPoint(point)), raw)
    val independentlyDeclared = right(raw.in(other))
    assert(independentlyDeclared.belongsTo(other))
    assert(!independentlyDeclared.belongsTo(left))
    val errors = typeCheckErrors("""
      import spatial4s.*
      val owner = Frame.named[D3]("owner").toOption.get
      val point: Point[owner.type, D3] = Xyz(1.0, 2.0, 3.0)
    """)
    assert(errors.nonEmpty)

  test("owned arithmetic refuses overflow before raw extraction"):
    val frame = right(Frame.named[D3]("overflow"))
    val point = right(Point.in(frame)(Double.MaxValue, 0.0, 0.0))
    val vector = right(Vec.in(frame)(Double.MaxValue, 0.0, 0.0))
    assertEquals(right(Xyz.fromPoint(point)), Xyz(Double.MaxValue, 0.0, 0.0))
    intercept[ArithmeticException](point + vector)
