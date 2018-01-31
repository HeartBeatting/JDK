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
 * A {@link BlockingQueue} in which producers may wait for consumers    // 这是一个阻塞队列, 生产者可能等待消费者来拿元素
 * to receive elements.  A {@code TransferQueue} may be useful for      // TransferQueue适合用于如下场景:
 * example in message passing applications in which producers           // 消息传递应用
 * sometimes (using method {@link #transfer}) await receipt of          // 生产者有时候(使用transfer方法),等待消费者调用take/poll方法来接收
 * elements by consumers invoking {@code take} or {@code poll}, while
 * at other times enqueue elements (via method {@code put}) without     // 其他时候,直接通过put方法将元素入队,不等待消费者来接收
 * waiting for receipt.
 * {@linkplain #tryTransfer(Object) Non-blocking} and                   // tryTransfer()方法是非阻塞的
 * {@linkplain #tryTransfer(Object,long,TimeUnit) time-out} versions of // 超时版本的tryTransfer方法也是可用的
 * {@code tryTransfer} are also available.
 * A {@code TransferQueue} may also be queried, via {@link              // 可以通过hasWaitingConsumer方法判断队列中,
 * #hasWaitingConsumer}, whether there are any threads waiting for      // 是否有线程在等待获取元素
 * items, which is a converse analogy to a {@code peek} operation.      // 和peek是相反的类比
 *
 * <p>Like other blocking queues, a {@code TransferQueue} may be        // 和其他的阻塞队列一样,TransferQueue可以设置容量上限的
 * capacity bounded.  If so, an attempted transfer operation may        // 如果这样,尝试转移操作可能最开始会阻塞等待有空间可用
 * initially block waiting for available space, and/or subsequently     // 且/或 随后阻塞等待消费者来获取
 * block waiting for reception by a consumer.  Note that in a queue     // 注意: 如果一个队列容量为0,比如SynchronousQueue
 * with zero capacity, such as {@link SynchronousQueue}, {@code put}    // put和transfer实际上意义是相同的.
 * and {@code transfer} are effectively synonymous.
 *
 * <p>This interface is a member of the
 * <a href="{@docRoot}/../technotes/guides/collections/index.html">
 * Java Collections Framework</a>.
 *
 * @since 1.7
 * @author Doug Lea
 * @param <E> the type of elements held in this collection
 */
public interface TransferQueue<E> extends BlockingQueue<E> {                    // 继承了Blocking,所以有阻塞队列的所有操作.
    /**
     * Transfers the element to a waiting consumer immediately, if possible.
     *
     * <p>More precisely, transfers the specified element immediately   // 更精确的说, 这个方法在有消费者线程
     * if there exists a consumer already waiting to receive it (in     // 通过take或者poll(超时获取)等待时
     * {@link #take} or timed {@link #poll(long,TimeUnit) poll}),       // 传输元素进去队列
     * otherwise returning {@code false} without enqueuing the element. // 否则直接返回false,不入队列
     *
     * @param e the element to transfer
     * @return {@code true} if the element was transferred, else
     *         {@code false}
     * @throws ClassCastException if the class of the specified element     // class类型不正确
     *         prevents it from being added to this queue
     * @throws NullPointerException if the specified element is null
     * @throws IllegalArgumentException if some property of the specified
     *         element prevents it from being added to this queue
     */
    boolean tryTransfer(E e);

    /**
     * Transfers the element to a consumer, waiting if necessary to do so.
     *
     * <p>More precisely, transfers the specified element immediately
     * if there exists a consumer already waiting to receive it (in
     * {@link #take} or timed {@link #poll(long,TimeUnit) poll}),
     * else waits until the element is received by a consumer.
     *
     * @param e the element to transfer
     * @throws InterruptedException if interrupted while waiting,
     *         in which case the element is not left enqueued
     * @throws ClassCastException if the class of the specified element
     *         prevents it from being added to this queue
     * @throws NullPointerException if the specified element is null
     * @throws IllegalArgumentException if some property of the specified
     *         element prevents it from being added to this queue
     */
    void transfer(E e) throws InterruptedException;

    /**
     * Transfers the element to a consumer if it is possible to do so
     * before the timeout elapses.
     *
     * <p>More precisely, transfers the specified element immediately
     * if there exists a consumer already waiting to receive it (in
     * {@link #take} or timed {@link #poll(long,TimeUnit) poll}),
     * else waits until the element is received by a consumer,
     * returning {@code false} if the specified wait time elapses
     * before the element can be transferred.
     *
     * @param e the element to transfer
     * @param timeout how long to wait before giving up, in units of
     *        {@code unit}
     * @param unit a {@code TimeUnit} determining how to interpret the
     *        {@code timeout} parameter
     * @return {@code true} if successful, or {@code false} if
     *         the specified waiting time elapses before completion,
     *         in which case the element is not left enqueued
     * @throws InterruptedException if interrupted while waiting,
     *         in which case the element is not left enqueued
     * @throws ClassCastException if the class of the specified element
     *         prevents it from being added to this queue
     * @throws NullPointerException if the specified element is null
     * @throws IllegalArgumentException if some property of the specified
     *         element prevents it from being added to this queue
     */
    boolean tryTransfer(E e, long timeout, TimeUnit unit)
        throws InterruptedException;

    /**
     * Returns {@code true} if there is at least one consumer waiting   // 判断是否至少有一个线程在等待获取
     * to receive an element via {@link #take} or
     * timed {@link #poll(long,TimeUnit) poll}.
     * The return value represents a momentary state of affairs.        // 返回值表示一个暂时状态
     *
     * @return {@code true} if there is at least one waiting consumer
     */
    boolean hasWaitingConsumer();

    /**
     * Returns an estimate of the number of consumers waiting to    // 返回等待获取元素的线程数
     * receive elements via {@link #take} or timed
     * {@link #poll(long,TimeUnit) poll}.  The return value is an   // 返回值是暂时状态的接近值
     * approximation of a momentary state of affairs, that may be
     * inaccurate if consumers have completed or given up waiting.  // 可能不太精确
     * The value may be useful for monitoring and heuristics, but   // 可以用于监控
     * not for synchronization control.  Implementations of this    // 不能用于同步控制
     * method are likely to be noticeably slower than those for     // 比hasWaitingConsumer方法慢
     * {@link #hasWaitingConsumer}.
     *
     * @return the number of consumers waiting to receive elements
     */
    int getWaitingConsumerCount();
}
