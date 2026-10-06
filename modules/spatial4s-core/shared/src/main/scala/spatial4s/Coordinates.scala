package spatial4s

final class Point[F <: Frame[D], D <: Dim] private[spatial4s] (
    val frame: F,
    private val values: Vector[Double]
):
  def coordinate(axis: Int): Option[Double] =
    values.lift(axis)

  def coordinates: Vector[Double] =
    values

  def belongsTo(other: Frame[?]): Boolean =
    frame.sameRuntimeOwnerAs(other)

  def +(vector: Vec[F, D]): Point[F, D] =
    Point.reowned(
      frame,
      CoordinateValidation.total(frame, vector.frame)(
        values.indices.map(axis => values(axis) + vector.valueAt(axis)).toVector
      )
    )

  def -(other: Point[F, D]): Vec[F, D] =
    Vec.reowned(
      frame,
      CoordinateValidation.total(frame, other.frame)(
        values.indices.map(axis => values(axis) - other.valueAt(axis)).toVector
      )
    )

  def addChecked[OtherF <: Frame[D]](
      vector: Vec[OtherF, D]
  ): Either[SpatialError, Point[F, D]] =
    if vector.belongsTo(frame) then
      CoordinateValidation
        .computed(
          values.indices
            .map(axis => values(axis) + vector.valueAt(axis))
            .toVector
        )
        .map(Point.reowned(frame, _))
    else Left(frame.mismatchWith(vector.frame))

  def subtractChecked[OtherF <: Frame[D]](
      other: Point[OtherF, D]
  ): Either[SpatialError, Vec[F, D]] =
    if other.belongsTo(frame) then
      CoordinateValidation
        .computed(
          values.indices
            .map(axis => values(axis) - other.valueAt(axis))
            .toVector
        )
        .map(Vec.reowned(frame, _))
    else Left(frame.mismatchWith(other.frame))

  private[spatial4s] def valueAt(axis: Int): Double =
    values(axis)

object Point:
  def in[D <: Dim](
      frame: Frame[D]
  )(coordinates: Double*)(using
      dimension: Dimension[D]
  ): Either[SpatialError, Point[frame.type, D]] =
    fromVector(frame, coordinates.toVector)

  def fromVector[D <: Dim](
      frame: Frame[D],
      coordinates: Vector[Double]
  )(using
      dimension: Dimension[D]
  ): Either[SpatialError, Point[frame.type, D]] =
    CoordinateValidation
      .validate(coordinates)
      .map(new Point(frame, _))

  private[spatial4s] def reowned[D <: Dim, F <: Frame[D]](
      frame: F,
      coordinates: Vector[Double]
  ): Point[F, D] =
    new Point(frame, coordinates)

final class Vec[F <: Frame[D], D <: Dim] private[spatial4s] (
    val frame: F,
    private val values: Vector[Double]
):
  def coordinate(axis: Int): Option[Double] =
    values.lift(axis)

  def coordinates: Vector[Double] =
    values

  def belongsTo(other: Frame[?]): Boolean =
    frame.sameRuntimeOwnerAs(other)

  def +(other: Vec[F, D]): Vec[F, D] =
    Vec.reowned(
      frame,
      CoordinateValidation.total(frame, other.frame)(
        values.indices.map(axis => values(axis) + other.valueAt(axis)).toVector
      )
    )

  def addChecked[OtherF <: Frame[D]](
      other: Vec[OtherF, D]
  ): Either[SpatialError, Vec[F, D]] =
    if other.belongsTo(frame) then
      CoordinateValidation
        .computed(
          values.indices
            .map(axis => values(axis) + other.valueAt(axis))
            .toVector
        )
        .map(Vec.reowned(frame, _))
    else Left(frame.mismatchWith(other.frame))

  private[spatial4s] def valueAt(axis: Int): Double =
    values(axis)

object Vec:
  def in[D <: Dim](
      frame: Frame[D]
  )(coordinates: Double*)(using
      dimension: Dimension[D]
  ): Either[SpatialError, Vec[frame.type, D]] =
    fromVector(frame, coordinates.toVector)

  def fromVector[D <: Dim](
      frame: Frame[D],
      coordinates: Vector[Double]
  )(using
      dimension: Dimension[D]
  ): Either[SpatialError, Vec[frame.type, D]] =
    CoordinateValidation
      .validate(coordinates)
      .map(new Vec(frame, _))

  private[spatial4s] def reowned[D <: Dim, F <: Frame[D]](
      frame: F,
      coordinates: Vector[Double]
  ): Vec[F, D] =
    new Vec(frame, coordinates)

private[spatial4s] object CoordinateValidation:
  def computed(values: Vector[Double]): Either[SpatialError, Vector[Double]] =
    values.zipWithIndex.find((value, _) => !value.isFinite) match
      case Some((value, axis)) => Left(SpatialError.NonFiniteCoordinate(axis, value))
      case None                => Right(values)

  /** Stable-owner operations return values directly; invalid widening or unrepresentable
    * arithmetic cannot manufacture invalid coordinate values.
    */
  def total(left: Frame[?], right: Frame[?])(values: => Vector[Double]): Vector[Double] =
    if !left.sameRuntimeOwnerAs(right) then
      throw new IllegalArgumentException(left.mismatchWith(right).message)
    computed(values).fold(error => throw new ArithmeticException(error.message), identity)

  def validate[D <: Dim](
      coordinates: Vector[Double]
  )(using dimension: Dimension[D]): Either[SpatialError, Vector[Double]] =
    if coordinates.length != dimension.rank then
      Left(SpatialError.DimensionMismatch(dimension.rank, coordinates.length))
    else
      coordinates.zipWithIndex.find((value, _) => !value.isFinite) match
        case Some((value, axis)) =>
          Left(SpatialError.NonFiniteCoordinate(axis, value))
        case None => Right(coordinates)
