package spatial4s

final class AffineCoordinates[D <: Dim] private (
    private val rows: Vector[Vector[Double]]
):
  def linear(row: Int, column: Int): Option[Double] =
    Option.when(
      row >= 0 &&
        row < rows.length &&
        column >= 0 &&
        column < rows.length
    )(rows(row)(column))

  def translation(axis: Int): Option[Double] =
    rows.lift(axis).map(_.last)

  def apply(
      coordinates: Vector[Double]
  )(using dimension: Dimension[D]): Either[SpatialError, Vector[Double]] =
    CoordinateValidation
      .validate(coordinates)
      .map(values =>
        rows.map(row =>
          values.indices
            .map(axis => row(axis) * values(axis))
            .sum + row.last
        )
      )

  def coefficients: Vector[Vector[Double]] =
    rows

object AffineCoordinates:
  def fromRows[D <: Dim](
      rows: Vector[Vector[Double]]
  )(using dimension: Dimension[D]): Either[SpatialError, AffineCoordinates[D]] =
    val expectedColumns = dimension.rank + 1
    val rowLengths = rows.map(_.length)
    if rows.length != dimension.rank ||
      rowLengths.exists(_ != expectedColumns)
    then
      Left(
        SpatialError.AffineShapeMismatch(
          dimension.rank,
          expectedColumns,
          rows.length,
          rowLengths
        )
      )
    else
      val invalid =
        rows.zipWithIndex.iterator
          .flatMap((row, rowIndex) =>
            row.zipWithIndex.iterator
              .map((value, columnIndex) => (rowIndex, columnIndex, value))
          )
          .find((_, _, value) => !value.isFinite)
      invalid match
        case Some((row, column, value)) =>
          Left(SpatialError.NonFiniteAffineCoefficient(row, column, value))
        case None =>
          Right(new AffineCoordinates(rows))

  def identity[D <: Dim](using
      dimension: Dimension[D]
  ): AffineCoordinates[D] =
    val rows =
      Vector.tabulate(dimension.rank)(row =>
        Vector.tabulate(dimension.rank + 1)(column => if row == column then 1.0 else 0.0)
      )
    new AffineCoordinates(rows)
