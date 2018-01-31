/*
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

/*
 *
 *
 *
 *
 *
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

package java.util.concurrent;

/**
 * An object that creates new threads on demand.  Using thread factories        // 根据需要创建新线程
 * removes hardwiring of calls to {@link Thread#Thread(Runnable) new Thread},   // 使用线程工厂避免使用new方法, 硬编码
 * enabling applications to use special thread subclasses, priorities, etc.     // 使应用使用特殊的线程子类, 优先顺序等.
 *
 * <p>
 * The simplest implementation of this interface is just:
 * <pre>
 * class SimpleThreadFactory implements ThreadFactory {
 *   public Thread newThread(Runnable r) {
 *     return new Thread(r);
 *   }
 * }
 * </pre>
 *
 * The {@link Executors#defaultThreadFactory} method provides a more            // 默认的线程工厂DefaultThreadFactory
 * useful simple implementation, that sets the created thread context
 * to known values before returning it.
 * @since 1.5
 * @author Doug Lea
 */
public interface ThreadFactory {

    /**
     * Constructs a new {@code Thread}.  Implementations may also initialize
     * priority, name, daemon status, {@code ThreadGroup}, etc.
     *
     * @param r a runnable to be executed by new thread instance
     * @return constructed thread, or {@code null} if the request to
     *         create a thread is rejected
     */
    Thread newThread(Runnable r);
}
