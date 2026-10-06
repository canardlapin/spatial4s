package spatial4s

sealed trait SpatialError derives CanEqual:
  def message: String

object SpatialError:
  final case class UnsupportedSpatialRank(actual: Int) extends SpatialError:
    def message: String = s"only spatial ranks 2 and 3 are supported, got $actual"
  final case class InvalidIdentifier(kind: String, value: String) extends SpatialError:
    def message: String =
      s"$kind must be nonempty, normalized, and contain only stable identifier characters: '$value'"

  final case class InvalidSymbol(value: String) extends SpatialError:
    def message: String =
      s"unit symbol must be nonempty and normalized: '$value'"

  final case class InvalidScale(value: Double) extends SpatialError:
    def message: String =
      s"unit scale must be positive and finite: $value"

  final case class InvalidFrameLabel(value: String) extends SpatialError:
    def message: String =
      s"frame label must be nonempty and normalized: '$value'"

  final case class DimensionMismatch(expected: Int, actual: Int) extends SpatialError:
    def message: String =
      s"expected spatial rank $expected but found $actual"

  final case class NonFiniteCoordinate(axis: Int, value: Double) extends SpatialError:
    def message: String =
      s"coordinate $axis is not finite: $value"

  final case class AffineShapeMismatch(
      expectedRows: Int,
      expectedColumns: Int,
      actualRows: Int,
      actualColumns: Vector[Int]
  ) extends SpatialError:
    def message: String =
      s"expected affine shape $expectedRows x $expectedColumns but found $actualRows rows with lengths ${actualColumns.mkString("[", ", ", "]")}"

  final case class NonFiniteAffineCoefficient(
      row: Int,
      column: Int,
      value: Double
  ) extends SpatialError:
    def message: String =
      s"affine coefficient ($row, $column) is not finite: $value"

  case object EphemeralFrameHasNoRecord extends SpatialError:
    def message: String =
      "an ephemeral frame has no persistence record"

  case object CannotRegisterEphemeralFrame extends SpatialError:
    def message: String =
      "an ephemeral frame cannot be registered for persistent restoration"

  final case class FrameKeyConflict(
      id: FrameId,
      registered: FrameKey,
      requested: FrameKey
  ) extends SpatialError:
    def message: String =
      s"frame id '${id.value}' is already registered with a different structural key"

  final case class DuplicateFrameOwner(id: FrameId) extends SpatialError:
    def message: String =
      s"frame id '${id.value}' already has a different live owner in this registry"

  final case class FrameMismatch(expected: String, actual: String) extends SpatialError:
    def message: String =
      s"frame owner mismatch: expected $expected but found $actual"

  final case class AlignmentCompositionMismatch(
      leftRight: String,
      rightLeft: String
  ) extends SpatialError:
    def message: String =
      s"alignment composition boundary mismatch: $leftRight does not align with $rightLeft"

private[spatial4s] object ValidatedText:
  private val StableIdentifier =
    "^[A-Za-z][A-Za-z0-9._:-]*$".r

  def identifier(
      kind: String,
      value: String
  ): Either[SpatialError, String] =
    value match
      case StableIdentifier() => Right(value)
      case _                  => Left(SpatialError.InvalidIdentifier(kind, value))

  def symbol(value: String): Either[SpatialError, String] =
    if value.nonEmpty && value.trim == value then Right(value)
    else Left(SpatialError.InvalidSymbol(value))
