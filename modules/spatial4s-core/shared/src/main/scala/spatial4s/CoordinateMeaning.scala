package spatial4s

sealed trait CoordinateQuantity derives CanEqual:
  def id: String

object CoordinateQuantity:
  case object Length extends CoordinateQuantity:
    val id: String = "length"

  case object Dimensionless extends CoordinateQuantity:
    val id: String = "dimensionless"

  private final case class CustomValue(id: String) extends CoordinateQuantity

  def custom(id: String): Either[SpatialError, CoordinateQuantity] =
    ValidatedText
      .identifier("coordinate quantity id", id)
      .map(CustomValue.apply)

final class CoordinateUnit private (
    val id: String,
    val symbol: String,
    val quantity: CoordinateQuantity,
    val scaleToCanonical: Option[Double]
) derives CanEqual:
  override def equals(other: Any): Boolean =
    other match
      case that: CoordinateUnit =>
        id == that.id &&
        symbol == that.symbol &&
        quantity == that.quantity &&
        scaleToCanonical == that.scaleToCanonical
      case _ => false

  override def hashCode(): Int =
    (id, symbol, quantity, scaleToCanonical).##

  override def toString: String =
    s"CoordinateUnit($id, $symbol, ${quantity.id})"

object CoordinateUnit:
  val Millimeter: CoordinateUnit =
    new CoordinateUnit(
      "millimeter",
      "mm",
      CoordinateQuantity.Length,
      Some(0.001)
    )

  val Meter: CoordinateUnit =
    new CoordinateUnit(
      "meter",
      "m",
      CoordinateQuantity.Length,
      Some(1.0)
    )

  val Micrometer: CoordinateUnit =
    new CoordinateUnit(
      "micrometer",
      "um",
      CoordinateQuantity.Length,
      Some(0.000001)
    )

  val Dimensionless: CoordinateUnit =
    new CoordinateUnit(
      "dimensionless",
      "1",
      CoordinateQuantity.Dimensionless,
      Some(1.0)
    )

  def custom(
      id: String,
      symbol: String,
      quantity: CoordinateQuantity,
      scaleToCanonical: Option[Double]
  ): Either[SpatialError, CoordinateUnit] =
    for
      validId <- ValidatedText.identifier("coordinate unit id", id)
      validSymbol <- ValidatedText.symbol(symbol)
      validScale <- validateScale(scaleToCanonical)
    yield new CoordinateUnit(validId, validSymbol, quantity, validScale)

  private def validateScale(
      scale: Option[Double]
  ): Either[SpatialError, Option[Double]] =
    scale match
      case Some(value) if !value.isFinite || value <= 0.0 =>
        Left(SpatialError.InvalidScale(value))
      case _ => Right(scale)

final class CoordinateConvention private (
    val id: String
) derives CanEqual:
  override def equals(other: Any): Boolean =
    other match
      case that: CoordinateConvention => id == that.id
      case _                          => false

  override def hashCode(): Int =
    id.##

  override def toString: String =
    s"CoordinateConvention($id)"

object CoordinateConvention:
  val Unspecified: CoordinateConvention =
    new CoordinateConvention("unspecified")

  val RAS: CoordinateConvention =
    new CoordinateConvention("RAS")

  val LPS: CoordinateConvention =
    new CoordinateConvention("LPS")

  def custom(id: String): Either[SpatialError, CoordinateConvention] =
    ValidatedText
      .identifier("coordinate convention id", id)
      .map(new CoordinateConvention(_))
