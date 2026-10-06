# The spatial4s 0.1 contract

Status: frozen for implementation. This document is the PH0 compatibility
boundary for spatial4s-core.

## Authority and scope

spatial4s-core is the sole ecosystem authority for:

- `Dim`, `D2`, `D3`, and `Dimension`;
- `Frame`, frame identity, records, restoration, registries, and alignment;
- `Point` and `Vec`;
- `CoordinateUnit` and `CoordinateConvention`;
- validated, frame-neutral affine coordinate coefficients.

image4s owns grids, discrete and continuous grid indices, and grid-to-frame
geometry. mesh4s owns surface incidence, realizations, and intrinsic surface
geometry. reframe4s owns typed transformations, registration, resampling, and
deformation policy.

`spatial4s.Frame` means a coordinate frame. It is unrelated to the `frame4s`
typed dataframe library.

## Dimensions

The public dimension contract is:

```scala
sealed trait Dim
sealed trait D2 extends Dim
sealed trait D3 extends Dim

sealed abstract class Dimension[D <: Dim]:
  def rank: Int
```

The core supplies exactly one `Dimension[D2]` and one `Dimension[D3]`.
Extending the sealed set is a reviewed spatial4s change.

## Units and conventions

`CoordinateUnit` is an immutable validated value with a stable identifier,
display symbol, quantity kind, and optional positive finite scale to the
quantity's canonical unit.

```scala
sealed trait CoordinateQuantity
object CoordinateQuantity:
  case object Length extends CoordinateQuantity
  case object Dimensionless extends CoordinateQuantity
  final class Custom private (...) extends CoordinateQuantity

final class CoordinateUnit private (...):
  def id: String
  def symbol: String
  def quantity: CoordinateQuantity
  def scaleToCanonical: Option[Double]

object CoordinateUnit:
  val Millimeter: CoordinateUnit
  val Meter: CoordinateUnit
  val Micrometer: CoordinateUnit
  val Dimensionless: CoordinateUnit
  def custom(...): Either[SpatialError, CoordinateUnit]
```

Custom quantity identifiers, unit identifiers, and symbols must be nonempty
normalized values. A supplied scale must be positive and finite. Equality is
structural.

`CoordinateConvention` is deliberately not an enum. It is an immutable
validated identifier with standard values and an extension boundary:

```scala
final class CoordinateConvention private (...):
  def id: String

object CoordinateConvention:
  val Unspecified: CoordinateConvention
  val RAS: CoordinateConvention
  val LPS: CoordinateConvention
  def custom(id: String): Either[SpatialError, CoordinateConvention]
```

## Frame identity

```scala
opaque type FrameId = String

final case class FrameKey(
    id: FrameId,
    spatialRank: Int,
    unit: CoordinateUnit,
    convention: CoordinateConvention
)

final case class FrameRecord(
    key: FrameKey,
    metadata: FrameMetadata
)

final class Frame[D <: Dim] private (...):
  def metadata: FrameMetadata
  def unit: CoordinateUnit
  def convention: CoordinateConvention
  def persistentKey: Option[FrameKey]
  def record: Either[SpatialError, FrameRecord]
  def sameRuntimeOwnerAs(other: Frame[?]): Boolean
```

`Frame.ephemeral` creates a process-local owner with no persistence record.
`Frame.persistent` uses a caller-supplied `FrameId`; spatial4s never derives
persistent identity from labels or hashes.

A registry is a persistent threaded value, not a mutable global or concurrent
memo:

```scala
final class FrameRegistry private (...):
  def register[D <: Dim](
      frame: Frame[D]
  ): Either[SpatialError, FrameRegistry]

final class FrameResolution[D <: Dim] private (...):
  def frame: Frame[D]
  def registry: FrameRegistry

def restore[D <: Dim](
    record: FrameRecord,
    registry: FrameRegistry
)(using Dimension[D]): Either[SpatialError, FrameResolution[D]]
```

Restoration returns the updated registry and the canonical owner already held
by that registry when the key matches. Restoring the same record in independent
registries creates independent runtime owners. Crossing that boundary requires
explicit `FrameAlignment` evidence, even when persistent keys match.

`FrameAlignment[D, A, B]` supports identity, inverse, and associative
composition. It transports points and vectors without changing coordinates.
Alignment checks dimension, unit, convention, and persistent identity; it does
not perform registration or a coordinate transform.

## Points and vectors

The frame value directly owns every point and vector type:

```scala
final class Point[F <: Frame[D], D <: Dim] private (...):
  def frame: F
  def coordinate(axis: Int): Option[Double]
  def coordinates: Vector[Double]
  def +(vector: Vec[F, D]): Point[F, D]
  def -(other: Point[F, D]): Vec[F, D]

final class Vec[F <: Frame[D], D <: Dim] private (...):
  def frame: F
  def coordinate(axis: Int): Option[Double]
  def coordinates: Vector[Double]
  def +(other: Vec[F, D]): Vec[F, D]

Point.in(frame)(coordinates*)
Vec.in(frame)(coordinates*)
```

Construction validates rank and finiteness. Operations with the same stable
frame path are total. Widened or independently restored owners require checked
operations or explicit alignment. Point and Vec are general coordinate values;
packed domain fields store their frame once rather than storing a frame token
per element.

Computed coordinates retain the finite-coordinate invariant. Checked arithmetic
and affine evaluation return `NonFiniteCoordinate` when Double arithmetic cannot
represent a result. Direct arithmetic throws `ArithmeticException` for that
case, and rejects mixed runtime owners with `IllegalArgumentException` if a
generic frame refinement was widened. `Frame.alignOwners` provides explicit
checked evidence for generic endpoint types; it never infers identity from labels.
`Frame.restoreDynamic` returns a dimension witness and canonical registry owner
for D2/D3 records, with an explicit unsupported-rank failure.

## Affine coordinate coefficients

spatial4s owns a validated numeric primitive but not a typed transformation
system:

```scala
final class AffineCoordinates[D <: Dim] private (...):
  def linear(row: Int, column: Int): Option[Double]
  def translation(axis: Int): Option[Double]
  def apply(coordinates: Vector[Double]): Either[SpatialError, Vector[Double]]

object AffineCoordinates:
  def fromRows[D <: Dim](
      rows: Vector[Vector[Double]]
  )(using Dimension[D]): Either[SpatialError, AffineCoordinates[D]]
```

The input has `D` rows and `D + 1` columns and every coefficient is finite.
This type has no source or target frame and makes no registration claim.
reframe4s wraps it in typed frame-to-frame transformations and owns
composition, inversion policy, estimation, resampling, and deformation.

## Migration boundary

`spatial4s.Frame`, `Point`, `Vec`, units, conventions, records, and alignments
are the migration target for image4s and reframe4s. mesh4s-geometry may consume
spatial4s as soon as this contract is implemented and pinned; mesh4s 0.1 does
not wait for image4s re-exports or the reframe4s migration.

Source compatibility aliases, if needed, live in image4s. They do not create a
second frame authority.
