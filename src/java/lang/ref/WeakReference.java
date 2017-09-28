/*
 * @(#)WeakReference.java	1.18 03/12/19
 *
 * Copyright 2004 Sun Microsystems, Inc. All rights reserved.
 * SUN PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package java.lang.ref;


/**                                                                             // 软引用只在内存不够时GC回收, 弱引用在下一次GC时就会被回收.
 * Weak reference objects, which do not prevent their referents from being      //WeakReference不会阻止引用被标记为可回收的
 * made finalizable, finalized, and then reclaimed.  Weak references are most   //GC的过程是,先标记为finalizable(可回收的)-->再标记为finalized(已回收的)-->最后直接回收
 * often used to implement canonicalizing mappings.                             //常常用于canonicalizing mappings, 一种map,为了防止内存用满,map的key或者value使用弱引用,不影响GC.
 *
 * <p> Suppose that the garbage collector determines at a certain point in time
 * that an object is <a href="package-summary.html#reachability">weakly
 * reachable</a>.  At that time it will atomically clear all weak references to
 * that object and all weak references to any other weakly-reachable objects
 * from which that object is reachable through a chain of strong and soft
 * references.  At the same time it will declare all of the formerly
 * weakly-reachable objects to be finalizable.  At the same time or at some
 * later time it will enqueue those newly-cleared weak references that are
 * registered with reference queues.
 *
 * @version  1.18, 12/19/03
 * @author   Mark Reinhold
 * @since    1.2
 */

public class WeakReference<T> extends Reference<T> {

    /**
     * Creates a new weak reference that refers to the given object.  The new
     * reference is not registered with any queue.
     *
     * @param referent object the new weak reference will refer to
     */
    public WeakReference(T referent) {
	super(referent);
    }

    /**
     * Creates a new weak reference that refers to the given object and is
     * registered with the given queue.
     *
     * @param referent object the new weak reference will refer to
     * @param q the queue with which the reference is to be registered,
     *          or <tt>null</tt> if registration is not required
     */
    public WeakReference(T referent, ReferenceQueue<? super T> q) {
	super(referent, q);
    }

}
