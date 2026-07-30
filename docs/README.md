# spatial4s

spatial4s gives scientific Scala libraries one shared meaning for dimensions,
coordinate frames, points, vectors, units, conventions, and validated affine
coordinates.

This avoids an unsafe ecosystem split where an image point and a surface point
use different frame-owner types despite describing the same physical space.

> `spatial4s.Frame` is a coordinate-frame owner. It is unrelated to the
> `frame4s` typed dataframe library.

The 0.1 public contract is frozen in the [contract reference](contract.md).
The core implementation and its JVM and Scala.js laws conform to that contract.

```scala mdoc
import spatial4s.*

val surfaceRas =
  Frame.named[D3](
    "surface RAS",
    unit = CoordinateUnit.Millimeter,
    convention = CoordinateConvention.RAS
  ).fold(error => sys.error(error.message), identity)

val point =
  Point.in(surfaceRas)(12.0, -4.5, 18.25)
    .fold(error => sys.error(error.message), identity)

point.coordinates
```

The stable `surfaceRas` path is part of the point type. A point from another
frame cannot enter a total same-frame operation without checked alignment.
