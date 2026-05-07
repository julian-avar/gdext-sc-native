package gdext.core.virtual

case class VirtualEntry(name: String, required: Boolean, default: () => Any)
