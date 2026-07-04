package com.julianavar.gdext.core.virtual

import scala.scalanative.unsafe.*

object VirtualStub:
    val noop: () => Unit = () => ()

    val returnFalse: () => Boolean = () => false

    val returnZeroInt: () => Int = () => 0

    val returnZeroFloat: () => Float = () => 0.0f

    val emptyString: () => CString = () => null

    val emptyStringName: () => Ptr[Byte] = () => null

    val emptyPackedStringArray: () => Ptr[Byte] = () => null

    val emptyDictionary: () => Ptr[Byte] = () => null

    val emptyArray: () => Ptr[Byte] = () => null

    val nullObject: () => Ptr[Byte] = () => null

    val nullVoidPtr: () => Ptr[Byte] = () => null

    val nilVariant: () => Ptr[Byte] = () => null
end VirtualStub
