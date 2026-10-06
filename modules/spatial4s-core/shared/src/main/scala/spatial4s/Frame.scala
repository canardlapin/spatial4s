package spatial4s

opaque type FrameId = String

object FrameId:
  def parse(value: String): Either[SpatialError, FrameId] =
    ValidatedText.identifier("frame id", value)

  extension (id: FrameId) def value: String = id

final class FrameMetadata private (
    val label: String
) derives CanEqual:
  override def equals(other: Any): Boolean =
    other match
      case that: FrameMetadata => label == that.label
      case _                   => false

  override def hashCode(): Int =
    label.##

object FrameMetadata:
  def named(label: String): Either[SpatialError, FrameMetadata] =
    if label.nonEmpty && label.trim == label then Right(new FrameMetadata(label))
    else Left(SpatialError.InvalidFrameLabel(label))

final case class FrameKey(
    id: FrameId,
    spatialRank: Int,
    unit: CoordinateUnit,
    convention: CoordinateConvention
) derives CanEqual

final case class FrameRecord(
    key: FrameKey,
    metadata: FrameMetadata
) derives CanEqual

final class Frame[D <: Dim] private (
    val metadata: FrameMetadata,
    val unit: CoordinateUnit,
    val convention: CoordinateConvention,
    val spatialRank: Int,
    val persistentKey: Option[FrameKey],
    private val runtimeToken: Frame.RuntimeToken
):
  def persistentId: Option[FrameId] =
    persistentKey.map(_.id)

  def record: Either[SpatialError, FrameRecord] =
    persistentKey
      .map(FrameRecord(_, metadata))
      .toRight(SpatialError.EphemeralFrameHasNoRecord)

  def sameRuntimeOwnerAs(other: Frame[?]): Boolean =
    runtimeToken.sameAs(other.runtimeToken)

  def samePersistentKeyAs(other: Frame[?]): Boolean =
    persistentKey.nonEmpty && persistentKey == other.persistentKey

  private[spatial4s] def identityDescription: String =
    persistentId.fold(s"ephemeral:${runtimeToken.hashCode()}")(_.value)

  private[spatial4s] def mismatchWith(other: Frame[?]): SpatialError =
    SpatialError.FrameMismatch(identityDescription, other.identityDescription)

  override def toString: String =
    s"Frame(${persistentId.fold("ephemeral")(_.value)}, ${metadata.label}, D$spatialRank, $unit, $convention)"

object Frame:
  type Registry = FrameRegistry
  object Registry:
    val empty: Registry = FrameRegistry.empty
  type Resolution[D <: Dim] = FrameResolution[D]
  private final class RuntimeToken:
    def sameAs(other: RuntimeToken): Boolean =
      this eq other

  def ephemeral[D <: Dim](
      metadata: FrameMetadata,
      unit: CoordinateUnit = CoordinateUnit.Millimeter,
      convention: CoordinateConvention = CoordinateConvention.Unspecified
  )(using dimension: Dimension[D]): Frame[D] =
    new Frame(
      metadata,
      unit,
      convention,
      dimension.rank,
      None,
      new RuntimeToken()
    )

  def named[D <: Dim](
      label: String,
      unit: CoordinateUnit = CoordinateUnit.Millimeter,
      convention: CoordinateConvention = CoordinateConvention.Unspecified
  )(using dimension: Dimension[D]): Either[SpatialError, Frame[D]] =
    FrameMetadata
      .named(label)
      .map(ephemeral(_, unit, convention))

  def persistent[D <: Dim](
      id: FrameId,
      metadata: FrameMetadata,
      unit: CoordinateUnit = CoordinateUnit.Millimeter,
      convention: CoordinateConvention = CoordinateConvention.Unspecified
  )(using dimension: Dimension[D]): Frame[D] =
    val key = FrameKey(id, dimension.rank, unit, convention)
    new Frame(
      metadata,
      unit,
      convention,
      dimension.rank,
      Some(key),
      new RuntimeToken()
    )

  def persistentNamed[D <: Dim](
      id: FrameId,
      label: String,
      unit: CoordinateUnit = CoordinateUnit.Millimeter,
      convention: CoordinateConvention = CoordinateConvention.Unspecified
  )(using dimension: Dimension[D]): Either[SpatialError, Frame[D]] =
    FrameMetadata
      .named(label)
      .map(persistent(id, _, unit, convention))

  def restore[D <: Dim](
      record: FrameRecord,
      registry: FrameRegistry
  )(using dimension: Dimension[D]): Either[SpatialError, FrameResolution[D]] =
    val key = record.key
    if key.spatialRank != dimension.rank then
      Left(SpatialError.DimensionMismatch(dimension.rank, key.spatialRank))
    else
      registry.entries.get(key.id) match
        case None =>
          val restored =
            new Frame[D](
              record.metadata,
              key.unit,
              key.convention,
              key.spatialRank,
              Some(key),
              new RuntimeToken()
            )
          Right(
            new FrameResolution(
              restored,
              new FrameRegistry(
                registry.entries.updated(
                  key.id,
                  FrameRegistry.Entry(key, restored)
                )
              )
            )
          )
        case Some(entry) if entry.key == key =>
          Right(
            new FrameResolution(
              // The requested witness and persisted key have the same checked
              // rank. Frame has no runtime data that depends on D beyond that
              // rank, so this is the one dynamic restoration boundary.
              entry.frame.asInstanceOf[Frame[D]],
              registry
            )
          )
        case Some(entry) =>
          Left(SpatialError.FrameKeyConflict(key.id, entry.key, key))

  def align[D <: Dim](
      left: Frame[D],
      right: Frame[D]
  ): Either[
    SpatialError,
    FrameAlignment[D, left.type, right.type]
  ] =
    FrameAlignment.check(left, right)

  /** Explicit evidence for generic or widened endpoint types. Runtime identity remains
    * checked on every transported value.
    */
  def alignOwners[D <: Dim, A <: Frame[D], B <: Frame[D]](
      left: A,
      right: B
  ): Either[SpatialError, FrameAlignment[D, A, B]] =
    FrameAlignment.checkOwners(left, right)

  def restoreDynamic(
      record: FrameRecord,
      registry: FrameRegistry
  ): Either[SpatialError, (SomeFrame, FrameRegistry)] =
    record.key.spatialRank match
      case 2 =>
        restore[D2](record, registry).map(r => SomeFrame.pack(r.frame) -> r.registry)
      case 3 =>
        restore[D3](record, registry).map(r => SomeFrame.pack(r.frame) -> r.registry)
      case rank => Left(SpatialError.UnsupportedSpatialRank(rank))

sealed trait SomeFrame:
  type D <: Dim
  val dimension: Dimension[D]
  val value: Frame[D]

object SomeFrame:
  private[spatial4s] def pack[A <: Dim](frame: Frame[A])(using
      witness: Dimension[A]
  ): SomeFrame { type D = A } =
    new SomeFrame:
      type D = A
      val dimension = witness
      val value = frame

final class FrameRegistry private[spatial4s] (
    private[spatial4s] val entries: Map[FrameId, FrameRegistry.Entry]
):
  def size: Int =
    entries.size

  def register[D <: Dim](
      frame: Frame[D]
  ): Either[SpatialError, FrameRegistry] =
    frame.persistentKey match
      case None =>
        Left(SpatialError.CannotRegisterEphemeralFrame)
      case Some(key) =>
        entries.get(key.id) match
          case None =>
            Right(
              new FrameRegistry(
                entries.updated(key.id, FrameRegistry.Entry(key, frame))
              )
            )
          case Some(entry) if entry.key != key =>
            Left(SpatialError.FrameKeyConflict(key.id, entry.key, key))
          case Some(entry) if entry.frame.sameRuntimeOwnerAs(frame) =>
            Right(this)
          case Some(_) =>
            Left(SpatialError.DuplicateFrameOwner(key.id))

object FrameRegistry:
  private[spatial4s] final case class Entry(
      key: FrameKey,
      frame: Frame[?]
  )

  val empty: FrameRegistry =
    new FrameRegistry(Map.empty)

final class FrameResolution[D <: Dim] private[spatial4s] (
    val frame: Frame[D],
    val registry: FrameRegistry
)

final class FrameAlignment[
    D <: Dim,
    A <: Frame[D],
    B <: Frame[D]
] private (
    val left: A,
    val right: B
):
  def sameRuntimeOwner: Boolean =
    left.sameRuntimeOwnerAs(right)

  def inverse: FrameAlignment[D, B, A] =
    new FrameAlignment(right, left)

  def andThen[C <: Frame[D]](
      next: FrameAlignment[D, B, C]
  ): Either[SpatialError, FrameAlignment[D, A, C]] =
    if right.sameRuntimeOwnerAs(next.left) then Right(new FrameAlignment(left, next.right))
    else
      Left(
        SpatialError.AlignmentCompositionMismatch(
          right.identityDescription,
          next.left.identityDescription
        )
      )

  def pointToRight(point: Point[A, D]): Either[SpatialError, Point[B, D]] =
    if point.belongsTo(left) then Right(Point.reowned(right, point.coordinates))
    else Left(left.mismatchWith(point.frame))

  def pointToLeft(point: Point[B, D]): Either[SpatialError, Point[A, D]] =
    inverse.pointToRight(point)

  def vectorToRight(vector: Vec[A, D]): Either[SpatialError, Vec[B, D]] =
    if vector.belongsTo(left) then Right(Vec.reowned(right, vector.coordinates))
    else Left(left.mismatchWith(vector.frame))

  def vectorToLeft(vector: Vec[B, D]): Either[SpatialError, Vec[A, D]] =
    inverse.vectorToRight(vector)

object FrameAlignment:
  private[spatial4s] def checkOwners[D <: Dim, A <: Frame[D], B <: Frame[D]](
      left: A,
      right: B
  ): Either[SpatialError, FrameAlignment[D, A, B]] =
    if left.sameRuntimeOwnerAs(right) || left.samePersistentKeyAs(right) then
      Right(new FrameAlignment(left, right))
    else Left(left.mismatchWith(right))
  def identity[D <: Dim, A <: Frame[D]](
      frame: A
  ): FrameAlignment[D, A, A] =
    new FrameAlignment(frame, frame)

  def check[D <: Dim](
      left: Frame[D],
      right: Frame[D]
  ): Either[
    SpatialError,
    FrameAlignment[D, left.type, right.type]
  ] =
    if left.sameRuntimeOwnerAs(right) || left.samePersistentKeyAs(right) then
      Right(new FrameAlignment(left, right))
    else Left(left.mismatchWith(right))
