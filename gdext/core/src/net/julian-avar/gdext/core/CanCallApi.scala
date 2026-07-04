package net.`julian-avar`.gdext.core

/** Evidence that the caller is the engine's own virtual-dispatch machinery, not user code.
  *
  * Required by every "paired" virtual override point (e.g. `_getMinimumSize`) whose
  * underscore-prefixed name was kept because it collides with an existing public method
  * (`getMinimumSize()`). Generated dispatch code lives inside the `gdext` package tree and picks up
  * the internal `given` automatically; user code outside `gdext` has no `given` in scope and no way
  * to construct one (the trait is `sealed`), so calling one of these methods directly is always a
  * compile error, not just a naming convention.
  */
sealed trait CanCallApi
object CanCallApi:
    private[gdext] given CanCallApi = new CanCallApi {}
end CanCallApi
