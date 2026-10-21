# spatial4s

spatial4s is the neutral coordinate kernel for image4s, mesh4s, and reframe4s.

The 0.1 core implements typed dimensions, coordinate-frame identity, points,
vectors, units, conventions, immutable restoration registries, explicit frame
alignment, and validated affine coordinate coefficients. The authoritative
contract is [docs/contract.md](docs/contract.md).

`spatial4s.Frame` means a typed coordinate frame. It is unrelated to the
existing `frame4s` typed dataframe library.

Raw interchange coordinates use `Xyz`: a finite, structurally equal triple with
no frame, units or convention. `xyz.in(frame)` explicitly declares an owned
`Point[frame.type, D3]`; equal triples supply no alignment evidence. Mesh point
fields and image grids retain their own storage/domain contracts.
