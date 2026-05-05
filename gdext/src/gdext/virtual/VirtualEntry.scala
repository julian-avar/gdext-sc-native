package gdext.virtual

case class VirtualEntry(name: String, required: Boolean, default: () => Unit)
