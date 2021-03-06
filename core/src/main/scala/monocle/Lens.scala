package monocle

import scalaz.{Applicative, Choice, Functor, Maybe, Monoid, Split, \/}

/**
 * A [[PLens]] can be seen as a pair of functions:
 *  - `get: S      => A` i.e. from an `S`, we can extract an `A`
 *  - `set: (B, S) => T` i.e. if we replace an `A` by a `B` in an `S`, we obtain a `T`
 *
 * A [[PLens]] could also be defined as a weaker [[PIso]] where set requires
 * an additional parameter than reverseGet.
 *
 * [[PLens]] stands for Polymorphic Lens as it set and modify methods change
 * a type `A` to `B` and `S` to `T`.
 * [[Lens]] is a type alias for [[PLens]] restricted to monomorphic updates:
 * {{{
 * type Lens[S, A] = PLens[S, S, A, A]
 * }}}
 *
 * A [[PLens]] is also a valid [[Getter]], [[Fold]], [[POptional]],
 * [[PTraversal]] and [[PSetter]]
 *
 * Typically a [[PLens]] or [[Lens]] can be defined between a [[Product]]
 * (e.g. case class, tuple, HList) and one of it is component.
 *
 * @see [[monocle.law.LensLaws]]
 *
 * @tparam S the source of a [[PLens]]
 * @tparam T the modified source of a [[PLens]]
 * @tparam A the target of a [[PLens]]
 * @tparam B the modified target of a [[PLens]]
 */
abstract class PLens[S, T, A, B] extends Serializable { self =>

  /** get the target of a [[PLens]] */
  def get(s: S): A

  /** set polymorphically the target of a [[PLens]] using a function */
  def set(b: B): S => T

  /** modify polymorphically the target of a [[PLens]] using [[Functor]] function */
  def modifyF[F[_]: Functor](f: A => F[B])(s: S): F[T]

  /** modify polymorphically the target of a [[PLens]] using a function */
  def modify(f: A => B): S => T

  /** join two [[PLens]] with the same target */
  @inline final def sum[S1, T1](other: PLens[S1, T1, A, B]): PLens[S \/ S1, T \/ T1, A, B] =
    PLens[S \/ S1, T \/ T1, A, B](_.fold(self.get, other.get)){
      b => _.bimap(self.set(b), other.set(b))
    }

  /** pair two disjoint [[PLens]] */
  @inline final def product[S1, T1, A1, B1](other: PLens[S1, T1, A1, B1]): PLens[(S, S1), (T, T1), (A, A1), (B, B1)] =
    PLens[(S, S1), (T, T1), (A, A1), (B, B1)]{
      case (s, s1) => (self.get(s), other.get(s1))
    }{ case (b, b1) => {
        case (s, s1) => (self.set(b)(s), other.set(b1)(s1))
      }
    }

  @inline final def first[C]: PLens[(S, C), (T, C), (A, C), (B, C)] =
    PLens[(S, C), (T, C), (A, C), (B, C)]{
      case (s, c) => (get(s), c)
    }{ case (b, c) => {
        case (s, _) => (set(b)(s), c)
      }
    }

  @inline final def second[C]: PLens[(C, S), (C, T), (C, A), (C, B)] =
    PLens[(C, S), (C, T), (C, A), (C, B)]{
      case (c, s) => (c, get(s))
    }{ case (c, b) => {
        case (_, s) => (c, set(b)(s))
      }
    }

  /***********************************************************/
  /** Compose methods between a [[PLens]] and another Optics */
  /***********************************************************/

  /** compose a [[PLens]] with a [[Fold]] */
  @inline final def composeFold[C](other: Fold[A, C]): Fold[S, C] =
    asFold composeFold other

  /** compose a [[PLens]] with a [[Getter]] */
  @inline final def composeGetter[C](other: Getter[A, C]): Getter[S, C] =
    asGetter composeGetter other

  /** compose a [[PLens]] with a [[PSetter]] */
  @inline final def composeSetter[C, D](other: PSetter[A, B, C, D]): PSetter[S, T, C, D] =
    asSetter composeSetter other

  /** compose a [[PLens]] with a [[PTraversal]] */
  @inline final def composeTraversal[C, D](other: PTraversal[A, B, C, D]): PTraversal[S, T, C, D] =
    asTraversal composeTraversal other

  /** compose a [[PLens]] with an [[POptional]] */
  @inline final def composeOptional[C, D](other: POptional[A, B, C, D]): POptional[S, T, C, D] =
    asOptional composeOptional other

  /** compose a [[PLens]] with a [[PPrism]] */
  @inline final def composePrism[C, D](other: PPrism[A, B, C, D]): POptional[S, T, C, D] =
    asOptional composeOptional other.asOptional

  /** compose a [[PLens]] with a [[PLens]] */
  @inline final def composeLens[C, D](other: PLens[A, B, C, D]): PLens[S, T, C, D] =
    new PLens[S, T, C, D]{
      def get(s: S): C =
        other.get(self.get(s))

      def set(d: D): S => T =
        self.modify(other.set(d))

      def modifyF[F[_]: Functor](f: C => F[D])(s: S): F[T] =
        self.modifyF(other.modifyF(f))(s)

      def modify(f: C => D): S => T =
        self.modify(other.modify(f))
    }

  /** compose a [[PLens]] with an [[PIso]] */
  @inline final def composeIso[C, D](other: PIso[A, B, C, D]): PLens[S, T, C, D] =
    composeLens(other.asLens)

  /********************************************/
  /** Experimental aliases of compose methods */
  /********************************************/

  /** alias to composeTraversal */
  @inline final def ^|->>[C, D](other: PTraversal[A, B, C, D]): PTraversal[S, T, C, D] =
    composeTraversal(other)

  /** alias to composeOptional */
  @inline final def ^|-?[C, D](other: POptional[A, B, C, D]): POptional[S, T, C, D] =
    composeOptional(other)

  /** alias to composePrism */
  @inline final def ^<-?[C, D](other: PPrism[A, B, C, D]): POptional[S, T, C, D] =
    composePrism(other)

  /** alias to composeLens */
  @inline final def ^|->[C, D](other: PLens[A, B, C, D]): PLens[S, T, C, D] =
    composeLens(other)

  /** alias to composeIso */
  @inline final def ^<->[C, D](other: PIso[A, B, C, D]): PLens[S, T, C, D] =
    composeIso(other)

  /************************************************************************************************/
  /** Transformation methods to view a [[PLens]] as another Optics                                */
  /************************************************************************************************/

  /** view a [[PLens]] as a [[Fold]] */
  @inline final def asFold: Fold[S, A] =
    new Fold[S, A] {
      def foldMap[M: Monoid](f: A => M)(s: S): M =
        f(get(s))
    }

  /** view a [[PLens]] as a [[Getter]] */
  @inline final def asGetter: Getter[S, A] =
    new Getter[S, A]{
      def get(s: S): A =
        self.get(s)
    }

  /** view a [[PLens]] as a [[PSetter]] */
  @inline final def asSetter: PSetter[S, T, A, B] =
    new PSetter[S, T, A, B]{
      def modify(f: A => B): S => T =
        self.modify(f)

      def set(b: B): S => T =
        self.set(b)
    }

  /** view a [[PLens]] as a [[PTraversal]] */
  @inline final def asTraversal: PTraversal[S, T, A, B] =
    new PTraversal[S, T, A, B] {
      def modifyF[F[_]: Applicative](f: A => F[B])(s: S): F[T] =
        self.modifyF(f)(s)
    }

  /** view a [[PLens]] as an [[POptional]] */
  @inline final def asOptional: POptional[S, T, A, B] =
    new POptional[S, T, A, B] {
      def getOrModify(s: S): T \/ A =
        \/.right(get(s))

      def set(b: B): S => T =
        self.set(b)

      def getOption(s: S): Option[A] =
        Some(self.get(s))

      def modify(f: A => B): S => T =
        self.modify(f)

      def modifyF[F[_]: Applicative](f: A => F[B])(s: S): F[T] =
        self.modifyF(f)(s)
    }

}

object PLens extends LensInstances {
  def id[S, T]: PLens[S, T, S, T] =
    PIso.id[S, T].asLens

  def codiagonal[S, T]: PLens[S \/ S, T \/ T, S, T] =
    PLens[S \/ S, T \/ T, S, T](
      _.fold(identity, identity)
    )(t => _.bimap(_ => t, _ => t))

  /**
   * create a [[PLens]] using a pair of functions: one to get the target, one to set the target.
   * @see macro module for methods generating [[PLens]] with less boiler plate
   */
  def apply[S, T, A, B](_get: S => A)(_set: B => S => T): PLens[S, T, A, B] =
    new PLens[S, T, A, B]{
      def get(s: S): A =
        _get(s)

      def set(b: B): S => T =
        _set(b)

      def modifyF[F[_]: Functor](f: A => F[B])(s: S): F[T] =
        Functor[F].map(f(_get(s)))(_set(_)(s))

      def modify(f: A => B): S => T =
       s => _set(f(_get(s)))(s)
    }

}

object Lens {
  def id[A]: Lens[A, A] =
    Iso.id[A].asLens

  def codiagonal[S]: Lens[S \/ S, S] =
    PLens.codiagonal

  /** alias for [[PLens]] apply with a monomorphic set function */
  def apply[S, A](get: S => A)(set: A => S => S): Lens[S, A] =
    PLens(get)(set)
}

sealed abstract class LensInstances extends LensInstances0 {
  implicit val lensChoice: Choice[Lens] = new Choice[Lens] {
    def choice[A, B, C](f: => Lens[A, C], g: => Lens[B, C]): Lens[A \/ B, C] =
      f sum g

    def id[A]: Lens[A, A] =
      Lens.id

    def compose[A, B, C](f: Lens[B, C], g: Lens[A, B]): Lens[A, C] =
      g composeLens f
  }
}

sealed abstract class LensInstances0 {
  implicit val lensSplit: Split[Lens]  = new Split[Lens] {
    def split[A, B, C, D](f: Lens[A, B], g: Lens[C, D]): Lens[(A, C), (B, D)] =
      f product g

    def compose[A, B, C](f: Lens[B, C], g: Lens[A, B]): Lens[A, C] =
      g composeLens f
  }
}