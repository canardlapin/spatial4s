# spatial4s

spatial4s gives scientific Scala libraries one shared meaning for dimensions,
coordinate frames, points, vectors, units, conventions, and validated affine
coordinates.

This avoids an unsafe ecosystem split where an image point and a surface point
use different frame-owner types despite describing the same physical space.

> `spatial4s.Frame` is a coordinate-frame owner. It is unrelated to the
> `frame4s` typed dataframe library.

The 0.1 public contract is frozen in the [contract reference](contract.md).
Implementation and law suites follow without changing those signatures
incompatibly.
