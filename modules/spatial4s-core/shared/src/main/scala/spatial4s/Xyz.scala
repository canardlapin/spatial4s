package spatial4s

/** Finite raw XYZ coordinates for interchange and geometry storage.
  *
  * This value has no frame, convention or units. Equal triples do not establish
  * alignment. `in` explicitly declares an owner; no implicit conversion from
  * raw coordinates to an owned point exists.
  */
final case class Xyz(x: Double, y: Double, z: Double) derives CanEqual:
  require(x.isFinite && y.isFinite && z.isFinite, "raw XYZ coordinates must be finite")

  def toVector: Vector[Double] = Vector(x, y, z)

  def coordinate(axis: Int): Option[Double] = axis match
    case 0 => Some(x)
    case 1 => Some(y)
    case 2 => Some(z)
    case _ => None

  /** Declare these raw numbers in the supplied frame without converting units. */
  def in(frame: Frame[D3]): Either[SpatialError, Point[frame.type, D3]] =
    Point.fromVector[D3](frame, toVector)

  def +(that: Xyz): Xyz = Xyz(x + that.x, y + that.y, z + that.z)
  def -(that: Xyz): Xyz = Xyz(x - that.x, y - that.y, z - that.z)
  def *(scale: Double): Xyz = Xyz(x * scale, y * scale, z * scale)
  def dot(that: Xyz): Double = x * that.x + y * that.y + z * that.z
  def cross(that: Xyz): Xyz =
    Xyz(y * that.z - z * that.y, z * that.x - x * that.z, x * that.y - y * that.x)
  def norm: Double = math.sqrt(dot(this))
  def normalized: Xyz =
    val n = norm
    if n == 0.0 then Xyz.Zero else this * (1.0 / n)

object Xyz:
  val Zero: Xyz = Xyz(0.0, 0.0, 0.0)

  def make(x: Double, y: Double, z: Double): Either[SpatialError, Xyz] =
    if !x.isFinite then Left(SpatialError.NonFiniteCoordinate(0, x))
    else if !y.isFinite then Left(SpatialError.NonFiniteCoordinate(1, y))
    else if !z.isFinite then Left(SpatialError.NonFiniteCoordinate(2, z))
    else Right(Xyz(x, y, z))

  def fromVector(values: Vector[Double]): Either[SpatialError, Xyz] =
    if values.length != 3 then Left(SpatialError.DimensionMismatch(3, values.length))
    else make(values(0), values(1), values(2))

  /** Extract raw numbers; this deliberately discards the point's frame evidence. */
  def fromPoint[F <: Frame[D3]](point: Point[F, D3]): Either[SpatialError, Xyz] =
    fromVector(point.coordinates)
