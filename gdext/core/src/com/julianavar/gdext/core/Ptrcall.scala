package com.julianavar.gdext.core

import scala.scalanative.unsafe.*
import scala.scalanative.unsigned.*
import scala.scalanative.libc.string.memcpy

private[gdext] trait PtrArg[A]:
    def size: CSize
    def write(a: A, buf: Ptr[Byte]): Unit

private[gdext] trait PtrRet[R]:
    def size: CSize
    def read(buf: Ptr[Byte]): R

private[gdext] object PtrArg:
    given PtrArg[Boolean]:
        def size                              = 1.toUSize
        def write(a: Boolean, buf: Ptr[Byte]) = !buf = if a then 1.toByte else 0.toByte

    given PtrArg[Int]:
        def size                          = 8.toUSize
        def write(a: Int, buf: Ptr[Byte]) = !buf.asInstanceOf[Ptr[Long]] = a.toLong

    given PtrArg[Long]:
        def size                           = 8.toUSize
        def write(a: Long, buf: Ptr[Byte]) = !buf.asInstanceOf[Ptr[Long]] = a

    given PtrArg[Float]:
        def size                            = 8.toUSize
        def write(a: Float, buf: Ptr[Byte]) = !buf.asInstanceOf[Ptr[Double]] = a.toDouble

    given PtrArg[Double]:
        def size                             = 8.toUSize
        def write(a: Double, buf: Ptr[Byte]) = !buf.asInstanceOf[Ptr[Double]] = a

    // For raw pointer passthrough — stores the pointer value, not a copy of the data.
    // Used by generated code for Variant / void* / Array args.
    given PtrArg[Ptr[Byte]]:
        def size                                = 8.toUSize
        def write(a: Ptr[Byte], buf: Ptr[Byte]) = !buf.asInstanceOf[Ptr[Ptr[Byte]]] = a

    given PtrArg[RID]:
        def size                                = 8.toUSize
        def write(a: RID, buf: Ptr[Byte]): Unit =
            import scala.scalanative.libc.string.{memcpy, memset}
            if a != null && a.ptr != null then memcpy(buf, a.ptr, 8.toUSize)
            else memset(buf, 0, 8.toUSize)
        end write
    end given
end PtrArg

private[gdext] object PtrRet:
    given PtrRet[Boolean]:
        def size                 = 1.toUSize
        def read(buf: Ptr[Byte]) = !buf != 0.toByte

    given PtrRet[Int]:
        def size                 = 8.toUSize
        def read(buf: Ptr[Byte]) = (!buf.asInstanceOf[Ptr[Long]]).toInt

    given PtrRet[Long]:
        def size                 = 8.toUSize
        def read(buf: Ptr[Byte]) = !buf.asInstanceOf[Ptr[Long]]

    given PtrRet[Float]:
        def size                 = 8.toUSize
        def read(buf: Ptr[Byte]) = (!buf.asInstanceOf[Ptr[Double]]).toFloat

    given PtrRet[Double]:
        def size                 = 8.toUSize
        def read(buf: Ptr[Byte]) = !buf.asInstanceOf[Ptr[Double]]

    given PtrRet[RID]:
        def size                      = 8.toUSize
        def read(buf: Ptr[Byte]): RID =
            val local = stackalloc[Byte](8.toUSize)
            memcpy(local, buf, 8.toUSize)
            new RID(local)
        end read
    end given
end PtrRet

/** // Fixed-arity ptrcall dispatchers. NOT inline — inlining these crashes dotty. */
private[gdext] object Ptrcall:
    private inline def noArgs: Ptr[Ptr[Byte]] = null.asInstanceOf[Ptr[Ptr[Byte]]]
    private inline def noRet: Ptr[Byte]       = null.asInstanceOf[Ptr[Byte]]

    def callVoid0(bind: Ptr[Byte], self: Ptr[Byte]): Unit = GdxApi
        .ptrcall(bind, self, noArgs, noRet)

    def callVoid1[A](bind: Ptr[Byte], self: Ptr[Byte], a: A)(using pa: PtrArg[A]): Unit =
        val bufA = stackalloc[Byte](pa.size)
        pa.write(a, bufA)
        val args = stackalloc[Ptr[Byte]](1)
        args(0) = bufA
        GdxApi.ptrcall(bind, self, args, noRet)
    end callVoid1

    def callVoid2[A, B](bind: Ptr[Byte], self: Ptr[Byte], a: A, b: B)(using
        pa: PtrArg[A],
        pb: PtrArg[B]
    ): Unit =
        val bufA = stackalloc[Byte](pa.size); pa.write(a, bufA)
        val bufB = stackalloc[Byte](pb.size); pb.write(b, bufB)
        val args = stackalloc[Ptr[Byte]](2)
        args(0) = bufA; args(1) = bufB
        GdxApi.ptrcall(bind, self, args, noRet)
    end callVoid2

    def callVoid3[A, B, C](bind: Ptr[Byte], self: Ptr[Byte], a: A, b: B, c: C)(using
        pa: PtrArg[A],
        pb: PtrArg[B],
        pc: PtrArg[C]
    ): Unit =
        val bufA = stackalloc[Byte](pa.size); pa.write(a, bufA)
        val bufB = stackalloc[Byte](pb.size); pb.write(b, bufB)
        val bufC = stackalloc[Byte](pc.size); pc.write(c, bufC)
        val args = stackalloc[Ptr[Byte]](3)
        args(0) = bufA; args(1) = bufB; args(2) = bufC
        GdxApi.ptrcall(bind, self, args, noRet)
    end callVoid3

    def callVoid4[A, B, C, D](bind: Ptr[Byte], self: Ptr[Byte], a: A, b: B, c: C, d: D)(using
        pa: PtrArg[A],
        pb: PtrArg[B],
        pc: PtrArg[C],
        pd: PtrArg[D]
    ): Unit =
        val bufA = stackalloc[Byte](pa.size); pa.write(a, bufA)
        val bufB = stackalloc[Byte](pb.size); pb.write(b, bufB)
        val bufC = stackalloc[Byte](pc.size); pc.write(c, bufC)
        val bufD = stackalloc[Byte](pd.size); pd.write(d, bufD)
        val args = stackalloc[Ptr[Byte]](4)
        args(0) = bufA; args(1) = bufB; args(2) = bufC; args(3) = bufD
        GdxApi.ptrcall(bind, self, args, noRet)
    end callVoid4

    def callVoid5[A, B, C, D, E](bind: Ptr[Byte], self: Ptr[Byte], a: A, b: B, c: C, d: D, e: E)(
        using
        pa: PtrArg[A],
        pb: PtrArg[B],
        pc: PtrArg[C],
        pd: PtrArg[D],
        pe: PtrArg[E]
    ): Unit =
        val bufA = stackalloc[Byte](pa.size); pa.write(a, bufA)
        val bufB = stackalloc[Byte](pb.size); pb.write(b, bufB)
        val bufC = stackalloc[Byte](pc.size); pc.write(c, bufC)
        val bufD = stackalloc[Byte](pd.size); pd.write(d, bufD)
        val bufE = stackalloc[Byte](pe.size); pe.write(e, bufE)
        val args = stackalloc[Ptr[Byte]](5)
        args(0) = bufA; args(1) = bufB; args(2) = bufC; args(3) = bufD; args(4) = bufE
        GdxApi.ptrcall(bind, self, args, noRet)
    end callVoid5

    def callVoid6[A, B, C, D, E, F](
        bind: Ptr[Byte],
        self: Ptr[Byte],
        a: A,
        b: B,
        c: C,
        d: D,
        e: E,
        f: F
    )(using
        pa: PtrArg[A],
        pb: PtrArg[B],
        pc: PtrArg[C],
        pd: PtrArg[D],
        pe: PtrArg[E],
        pf: PtrArg[F]
    ): Unit =
        val bufA = stackalloc[Byte](pa.size); pa.write(a, bufA)
        val bufB = stackalloc[Byte](pb.size); pb.write(b, bufB)
        val bufC = stackalloc[Byte](pc.size); pc.write(c, bufC)
        val bufD = stackalloc[Byte](pd.size); pd.write(d, bufD)
        val bufE = stackalloc[Byte](pe.size); pe.write(e, bufE)
        val bufF = stackalloc[Byte](pf.size); pf.write(f, bufF)
        val args = stackalloc[Ptr[Byte]](6)
        args(0) = bufA; args(1) = bufB; args(2) = bufC
        args(3) = bufD; args(4) = bufE; args(5) = bufF
        GdxApi.ptrcall(bind, self, args, noRet)
    end callVoid6

    def call0[R](bind: Ptr[Byte], self: Ptr[Byte])(using pr: PtrRet[R]): R =
        val ret = stackalloc[Byte](pr.size)
        GdxApi.ptrcall(bind, self, noArgs, ret)
        pr.read(ret)
    end call0

    def call1[R, A](bind: Ptr[Byte], self: Ptr[Byte], a: A)(using pr: PtrRet[R], pa: PtrArg[A]): R =
        val bufA = stackalloc[Byte](pa.size); pa.write(a, bufA)
        val args = stackalloc[Ptr[Byte]](1)
        args(0) = bufA
        val ret = stackalloc[Byte](pr.size)
        GdxApi.ptrcall(bind, self, args, ret)
        pr.read(ret)
    end call1

    def call2[R, A, B](bind: Ptr[Byte], self: Ptr[Byte], a: A, b: B)(using
        pr: PtrRet[R],
        pa: PtrArg[A],
        pb: PtrArg[B]
    ): R =
        val bufA = stackalloc[Byte](pa.size); pa.write(a, bufA)
        val bufB = stackalloc[Byte](pb.size); pb.write(b, bufB)
        val args = stackalloc[Ptr[Byte]](2)
        args(0) = bufA; args(1) = bufB
        val ret = stackalloc[Byte](pr.size)
        GdxApi.ptrcall(bind, self, args, ret)
        pr.read(ret)
    end call2

    def call3[R, A, B, C](bind: Ptr[Byte], self: Ptr[Byte], a: A, b: B, c: C)(using
        pr: PtrRet[R],
        pa: PtrArg[A],
        pb: PtrArg[B],
        pc: PtrArg[C]
    ): R =
        val bufA = stackalloc[Byte](pa.size); pa.write(a, bufA)
        val bufB = stackalloc[Byte](pb.size); pb.write(b, bufB)
        val bufC = stackalloc[Byte](pc.size); pc.write(c, bufC)
        val args = stackalloc[Ptr[Byte]](3)
        args(0) = bufA; args(1) = bufB; args(2) = bufC
        val ret = stackalloc[Byte](pr.size)
        GdxApi.ptrcall(bind, self, args, ret)
        pr.read(ret)
    end call3

    def call4[R, A, B, C, D](bind: Ptr[Byte], self: Ptr[Byte], a: A, b: B, c: C, d: D)(using
        pr: PtrRet[R],
        pa: PtrArg[A],
        pb: PtrArg[B],
        pc: PtrArg[C],
        pd: PtrArg[D]
    ): R =
        val bufA = stackalloc[Byte](pa.size); pa.write(a, bufA)
        val bufB = stackalloc[Byte](pb.size); pb.write(b, bufB)
        val bufC = stackalloc[Byte](pc.size); pc.write(c, bufC)
        val bufD = stackalloc[Byte](pd.size); pd.write(d, bufD)
        val args = stackalloc[Ptr[Byte]](4)
        args(0) = bufA; args(1) = bufB; args(2) = bufC; args(3) = bufD
        val ret = stackalloc[Byte](pr.size)
        GdxApi.ptrcall(bind, self, args, ret)
        pr.read(ret)
    end call4

    def call5[R, A, B, C, D, E](bind: Ptr[Byte], self: Ptr[Byte], a: A, b: B, c: C, d: D, e: E)(
        using
        pr: PtrRet[R],
        pa: PtrArg[A],
        pb: PtrArg[B],
        pc: PtrArg[C],
        pd: PtrArg[D],
        pe: PtrArg[E]
    ): R =
        val bufA = stackalloc[Byte](pa.size); pa.write(a, bufA)
        val bufB = stackalloc[Byte](pb.size); pb.write(b, bufB)
        val bufC = stackalloc[Byte](pc.size); pc.write(c, bufC)
        val bufD = stackalloc[Byte](pd.size); pd.write(d, bufD)
        val bufE = stackalloc[Byte](pe.size); pe.write(e, bufE)
        val args = stackalloc[Ptr[Byte]](5)
        args(0) = bufA; args(1) = bufB; args(2) = bufC; args(3) = bufD; args(4) = bufE
        val ret = stackalloc[Byte](pr.size)
        GdxApi.ptrcall(bind, self, args, ret)
        pr.read(ret)
    end call5

    def call6[R, A, B, C, D, E, F](
        bind: Ptr[Byte],
        self: Ptr[Byte],
        a: A,
        b: B,
        c: C,
        d: D,
        e: E,
        f: F
    )(using
        pr: PtrRet[R],
        pa: PtrArg[A],
        pb: PtrArg[B],
        pc: PtrArg[C],
        pd: PtrArg[D],
        pe: PtrArg[E],
        pf: PtrArg[F]
    ): R =
        val bufA = stackalloc[Byte](pa.size); pa.write(a, bufA)
        val bufB = stackalloc[Byte](pb.size); pb.write(b, bufB)
        val bufC = stackalloc[Byte](pc.size); pc.write(c, bufC)
        val bufD = stackalloc[Byte](pd.size); pd.write(d, bufD)
        val bufE = stackalloc[Byte](pe.size); pe.write(e, bufE)
        val bufF = stackalloc[Byte](pf.size); pf.write(f, bufF)
        val args = stackalloc[Ptr[Byte]](6)
        args(0) = bufA; args(1) = bufB; args(2) = bufC
        args(3) = bufD; args(4) = bufE; args(5) = bufF
        val ret = stackalloc[Byte](pr.size)
        GdxApi.ptrcall(bind, self, args, ret)
        pr.read(ret)
    end call6
end Ptrcall
