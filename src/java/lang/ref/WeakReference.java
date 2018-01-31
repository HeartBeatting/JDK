/*
 * Copyright (c) 1997, 2003, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */

package java.lang.ref;


/**
 * Weak reference objects, which do not prevent their referents from being          // 弱引用对象, 不会阻止引用被标记为finalizable,
 * made finalizable, finalized, and then reclaimed.  Weak references are most       // 然后标记为finalized, 然后回收掉 (这里其实是新生代GC的三步)
 * often used to implement canonicalizing mappings.                                 // 弱引用大多数时候是用来实现标准的map的 (这种map在内存不够时会被回收掉)
 *
 * <p> Suppose that the garbage collector determines at a certain point in time     // 假设垃圾回收器,在某个时间点确定 一个对象是弱引用的.
 * that an object is <a href="package-summary.html#reachability">weakly
 * reachable</a>.  At that time it will atomically clear all weak references to     // 这时gc会自动清理那个对象所有的弱引用.
 * that object and all weak references to any other weakly-reachable objects        // 所有指向其他任何一个弱引用的 弱引用
 * from which that object is reachable through a chain of strong and soft
 * references.  At the same time it will declare all of the formerly                // 同时,它会申明前面所有的弱引用对象 标记为可回收的 finalizable
 * weakly-reachable objects to be finalizable.  At the same time or at some         // 同时或者稍后时候,它会将那些新清理的弱引用 入队到reference queues中
 * later time it will enqueue those newly-cleared weak references that are
 * registered with reference queues.
 *
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
