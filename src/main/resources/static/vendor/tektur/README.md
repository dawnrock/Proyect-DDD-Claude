# Tektur 5.3.0 (variable weight, latin)

Copy of `files/tektur-latin-wght-normal.woff2` and `LICENSE` (SIL Open Font License 1.1, saved as `OFL.txt`) from the npm package [`@fontsource-variable/tektur@5.3.0`](https://www.npmjs.com/package/@fontsource-variable/tektur/v/5.3.0).
It is the typeface of the monA Bank brand (#178, ADR 0022): the intro animates its weight from 400 to 900. Tektur has no published WebJar, so it is kept here instead of in `pom.xml`, like Motion (ADR 0016).

The tarball was checked against the registry's `dist.integrity`:
`sha512-aTiP3FWurF3A7kIr3PyHib27A9PLurrkHfu6UwCv1eCHyi7k9AyPBhIspGH8n6fDWrpqFXqpWZ+zev/MbPZ/4A==`.

To upgrade: download the new tarball, check its integrity, replace both files, update this note and ADR 0022, and run `node scripts/build-brand.mjs` again if the outlines changed.
