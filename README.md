# spatial4s

spatial4s is the neutral coordinate kernel for image4s, mesh4s, and reframe4s.

The 0.1 core implements typed dimensions, coordinate-frame identity, points,
vectors, units, conventions, immutable restoration registries, explicit frame
alignment, and validated affine coordinate coefficients. The authoritative
contract is [docs/contract.md](docs/contract.md).

`spatial4s.Frame` means a typed coordinate frame. It is unrelated to the
existing `frame4s` typed dataframe library.
