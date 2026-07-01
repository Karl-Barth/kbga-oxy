# oxygen-stubs (compile-only)

Minimal declarations of the oXygen API used by anton-oxy, with signatures matching
the real oXygen classes. They exist ONLY so CI can compile without the (non-
redistributable) `oxygen.jar`. They are NOT packaged into the plugin jar — at runtime
oXygen provides the real classes. The local build (`build.sh`) uses the real
`oxygen.jar` instead and is the source of truth.
