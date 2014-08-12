# Maven Fetcher

This library provides a greatly simplified interface to Maven's Aether repository system, allowing
one to download Maven artifacts into a user's local Maven repository with a minimum degree of fuss.

It is adapted from the [Capsule] project. Capsule is designed for all wrinkles to be ironed out up
front and thus tends to handle any errors by blowing everything up. This library expects to be used
in situations where developers will be editing Maven dependencies and may make mistakes, and thus it
provides better failure handling for unresolvable dependencies.

[Capsule]: https://github.com/puniverse/capsule
