/*
 * @(#)AbstractQueuedSynchronizer.java	1.4 07/01/04
 *
 * Copyright 2004 Sun Microsystems, Inc. All rights reserved.
 * SUN PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package java.util.concurrent.locks;

import sun.misc.Unsafe;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Provides a framework for implementing blocking locks and related
 * synchronizers (semaphores, events, etc) that rely on
 * first-in-first-out (FIFO) wait queues.  This class is designed to
 * be a useful basis for most kinds of synchronizers that rely on a
 * single atomic <tt>int</tt> value to represent state. Subclasses
 * must define the protected methods that change this state, and which
 * define what that state means in terms of this object being acquired
 * or released.  Given these, the other methods in this class carry
 * out all queuing and blocking mechanics. Subclasses can maintain
 * other state fields, but only the atomically updated <tt>int</tt>
 * value manipulated using methods {@link #getState}, {@link
 * #setState} and {@link #compareAndSetState} is tracked with respect
 * to synchronization.
 * <p/>
 * <p>Subclasses should be defined as non-public internal helper
 * classes that are used to implement the synchronization properties
 * of their enclosing class.  Class
 * <tt>AbstractQueuedSynchronizer</tt> does not implement any
 * synchronization interface.  Instead it defines methods such as
 * {@link #acquireInterruptibly} that can be invoked as
 * appropriate by concrete locks and related synchronizers to
 * implement their public methods.
 * <p/>
 * <p>This class supports either or both a default <em>exclusive</em>
 * mode and a <em>shared</em> mode. When acquired in exclusive mode,
 * attempted acquires by other threads cannot succeed. Shared mode
 * acquires by multiple threads may (but need not) succeed. This class
 * does not &quot;understand&quot; these differences except in the
 * mechanical sense that when a shared mode acquire succeeds, the next
 * waiting thread (if one exists) must also determine whether it can
 * acquire as well. Threads waiting in the different modes share the
 * same FIFO queue. Usually, implementation subclasses support only
 * one of these modes, but both can come into play for example in a
 * {@link ReadWriteLock}. Subclasses that support only exclusive or
 * only shared modes need not define the methods supporting the unused mode.
 * <p/>
 * <p>This class defines a nested {@link ConditionObject} class that
 * can be used as a {@link Condition} implementation by subclasses
 * supporting exclusive mode for which method {@link
 * #isHeldExclusively} reports whether synchronization is exclusively
 * held with respect to the current thread, method {@link #release}
 * invoked with the current {@link #getState} value fully releases
 * this object, and {@link #acquire}, given this saved state value,
 * eventually restores this object to its previous acquired state.  No
 * <tt>AbstractQueuedSynchronizer</tt> method otherwise creates such a
 * condition, so if this constraint cannot be met, do not use it.  The
 * behavior of {@link ConditionObject} depends of course on the
 * semantics of its synchronizer implementation.
 * <p/>
 * <p> This class provides inspection, instrumentation, and monitoring
 * methods for the internal queue, as well as similar methods for
 * condition objects. These can be exported as desired into classes
 * using an <tt>AbstractQueuedSynchronizer</tt> for their
 * synchronization mechanics.
 * <p/>
 * <p> Serialization of this class stores only the underlying atomic
 * integer maintaining state, so deserialized objects have empty
 * thread queues. Typical subclasses requiring serializability will
 * define a <tt>readObject</tt> method that restores this to a known
 * initial state upon deserialization.
 * <p/>
 * <h3>Usage</h3>
 * <p/>
 * <p> To use this class as the basis of a synchronizer, redefine the
 * following methods, as applicable, by inspecting and/or modifying
 * the synchronization state using {@link #getState}, {@link
 * #setState} and/or {@link #compareAndSetState}:
 * <p/>
 * <ul>
 * <li> {@link #tryAcquire}
 * <li> {@link #tryRelease}
 * <li> {@link #tryAcquireShared}
 * <li> {@link #tryReleaseShared}
 * <li> {@link #isHeldExclusively}
 * </ul>
 * <p/>
 * Each of these methods by default throws {@link
 * UnsupportedOperationException}.  Implementations of these methods
 * must be internally thread-safe, and should in general be short and
 * not block. Defining these methods is the <em>only</em> supported
 * means of using this class. All other methods are declared
 * <tt>final</tt> because they cannot be independently varied.
 * <p/>
 * <p> Even though this class is based on an internal FIFO queue, it
 * does not automatically enforce FIFO acquisition policies.  The core
 * of exclusive synchronization takes the form:
 * <p/>
 * <pre>
 * Acquire:
 *     while (!tryAcquire(arg)) {
 *        <em>enqueue thread if it is not already queued</em>;
 *        <em>possibly block current thread</em>;
 *     }
 *
 * Release:
 *     if (tryRelease(arg))
 *        <em>unblock the first queued thread</em>;
 * </pre>
 * <p/>
 * (Shared mode is similar but may involve cascading signals.)
 * <p/>
 * <p> Because checks in acquire are invoked before enqueuing, a newly
 * acquiring thread may <em>barge</em> ahead of others that are
 * blocked and queued. However, you can, if desired, define
 * <tt>tryAcquire</tt> and/or <tt>tryAcquireShared</tt> to disable
 * barging by internally invoking one or more of the inspection
 * methods. In particular, a strict FIFO lock can define
 * <tt>tryAcquire</tt> to immediately return <tt>false</tt> if {@link
 * #getFirstQueuedThread} does not return the current thread.  A
 * normally preferable non-strict fair version can immediately return
 * <tt>false</tt> only if {@link #hasQueuedThreads} returns
 * <tt>true</tt> and <tt>getFirstQueuedThread</tt> is not the current
 * thread; or equivalently, that <tt>getFirstQueuedThread</tt> is both
 * non-null and not the current thread.  Further variations are
 * possible.
 * <p/>
 * <p> Throughput and scalability are generally highest for the
 * default barging (also known as <em>greedy</em>,
 * <em>renouncement</em>, and <em>convoy-avoidance</em>) strategy.
 * While this is not guaranteed to be fair or starvation-free, earlier
 * queued threads are allowed to recontend before later queued
 * threads, and each recontention has an unbiased chance to succeed
 * against incoming threads.  Also, while acquires do not
 * &quot;spin&quot; in the usual sense, they may perform multiple
 * invocations of <tt>tryAcquire</tt> interspersed with other
 * computations before blocking.  This gives most of the benefits of
 * spins when exclusive synchronization is only briefly held, without
 * most of the liabilities when it isn't. If so desired, you can
 * augment this by preceding calls to acquire methods with
 * "fast-path" checks, possibly prechecking {@link #hasContended}
 * and/or {@link #hasQueuedThreads} to only do so if the synchronizer
 * is likely not to be contended.
 * <p/>
 * <p> This class provides an efficient and scalable basis for
 * synchronization in part by specializing its range of use to
 * synchronizers that can rely on <tt>int</tt> state, acquire, and
 * release parameters, and an internal FIFO wait queue. When this does
 * not suffice, you can build synchronizers from a lower level using
 * {@link java.util.concurrent.atomic atomic} classes, your own custom
 * {@link java.util.Queue} classes, and {@link LockSupport} blocking
 * support.
 * <p/>
 * <h3>Usage Examples</h3>
 * <p/>
 * <p>Here is a non-reentrant mutual exclusion lock class that uses
 * the value zero to represent the unlocked state, and one to
 * represent the locked state. It also supports conditions and exposes
 * one of the instrumentation methods:
 * <p/>
 * <pre>
 * class Mutex implements Lock, java.io.Serializable {
 *
 *    // Our internal helper class
 *    private static class Sync extends AbstractQueuedSynchronizer {
 *      // Report whether in locked state
 *      protected boolean isHeldExclusively() {
 *        return getState() == 1;
 *      }
 *
 *      // Acquire the lock if state is zero
 *      public boolean tryAcquire(int acquires) {
 *        assert acquires == 1; // Otherwise unused
 *        return compareAndSetState(0, 1);
 *      }
 *
 *      // Release the lock by setting state to zero
 *      protected boolean tryRelease(int releases) {
 *        assert releases == 1; // Otherwise unused
 *        if (getState() == 0) throw new IllegalMonitorStateException();
 *        setState(0);
 *        return true;
 *      }
 *
 *      // Provide a Condition
 *      Condition newCondition() { return new ConditionObject(); }
 *
 *      // Deserialize properly
 *      private void readObject(ObjectInputStream s) throws IOException, ClassNotFoundException {
 *        s.defaultReadObject();
 *        setState(0); // reset to unlocked state
 *      }
 *    }
 *
 *    // The sync object does all the hard work. We just forward to it.
 *    private final Sync sync = new Sync();
 *
 *    public void lock()                { sync.acquire(1); }
 *    public boolean tryLock()          { return sync.tryAcquire(1); }
 *    public void unlock()              { sync.release(1); }
 *    public Condition newCondition()   { return sync.newCondition(); }
 *    public boolean isLocked()         { return sync.isHeldExclusively(); }
 *    public boolean hasQueuedThreads() { return sync.hasQueuedThreads(); }
 *    public void lockInterruptibly() throws InterruptedException {
 *      sync.acquireInterruptibly(1);
 *    }
 *    public boolean tryLock(long timeout, TimeUnit unit) throws InterruptedException {
 *      return sync.tryAcquireNanos(1, unit.toNanos(timeout));
 *    }
 * }
 * </pre>
 * <p/>
 * <p> Here is a latch class that is like a {@link CountDownLatch}
 * except that it only requires a single <tt>signal</tt> to
 * fire. Because a latch is non-exclusive, it uses the <tt>shared</tt>
 * acquire and release methods.
 * <p/>
 * <pre>
 * class BooleanLatch {
 *
 *    private static class Sync extends AbstractQueuedSynchronizer {
 *      boolean isSignalled() { return getState() != 0; }
 *
 *      protected int tryAcquireShared(int ignore) {
 *        return isSignalled()? 1 : -1;
 *      }
 *
 *      protected boolean tryReleaseShared(int ignore) {
 *        setState(1);
 *        return true;
 *      }
 *    }
 *
 *    private final Sync sync = new Sync();
 *    public boolean isSignalled() { return sync.isSignalled(); }
 *    public void signal()         { sync.releaseShared(1); }
 *    public void await() throws InterruptedException {
 *      sync.acquireSharedInterruptibly(1);
 *    }
 * }
 *
 * </pre>
 *
 * @author Doug Lea
 * @since 1.5
 */
public abstract class AbstractQueuedSynchronizer implements java.io.Serializable {
    private static final long serialVersionUID = 7373984972572414691L;

    /**
     * Creates a new <tt>AbstractQueuedSynchronizer</tt> instance
     * with initial synchronization state of zero.
     */
    protected AbstractQueuedSynchronizer() {
    }

    /**
     * Wait queue node class.
     * <p/>
     * <p> The wait queue is a variant of a "CLH" (Craig, Landin, and
     * Hagersten) lock queue. CLH locks are normally used for
     * spinlocks.  We instead use them for blocking synchronizers, but
     * use the same basic tactic of holding some of the control
     * information about a thread in the predecessor of its node.  A
     * "status" field in each node keeps track of whether a thread
     * should block.  A node is signalled when its predecessor
     * releases.  Each node of the queue otherwise serves as a
     * specific-notification-style monitor holding a single waiting
     * thread. The status field does NOT control whether threads are
     * granted locks etc though.  A thread may try to acquire if it is
     * first in the queue. But being first does not guarantee success;
     * it only gives the right to contend.  So the currently released
     * contender thread may need to rewait.
     * <p/>
     * <p>To enqueue into a CLH lock, you atomically splice it in as new
     * tail. To dequeue, you just set the head field.
     * <pre>
     *      +------+  prev +-----+       +-----+
     * head |      | <---- |     | <---- |     |  tail
     *      +------+       +-----+       +-----+
     * </pre>
     * <p/>
     * <p>Insertion into a CLH queue requires only a single atomic
     * operation on "tail", so there is a simple atomic point of
     * demarcation from unqueued to queued. Similarly, dequeing
     * involves only updating the "head". However, it takes a bit
     * more work for nodes to determine who their successors are,
     * in part to deal with possible cancellation due to timeouts
     * and interrupts.
     * <p/>
     * <p>The "prev" links (not used in original CLH locks), are mainly
     * needed to handle cancellation. If a node is cancelled, its
     * successor is (normally) relinked to a non-cancelled
     * predecessor. For explanation of similar mechanics in the case
     * of spin locks, see the papers by Scott and Scherer at
     * http://www.cs.rochester.edu/u/scott/synchronization/
     * <p/>
     * <p>We also use "next" links to implement blocking mechanics.
     * The thread id for each node is kept in its own node, so a
     * predecessor signals the next node to wake up by traversing
     * next link to determine which thread it is.  Determination of
     * successor must avoid races with newly queued nodes to set
     * the "next" fields of their predecessors.  This is solved
     * when necessary by checking backwards from the atomically
     * updated "tail" when a node's successor appears to be null.
     * (Or, said differently, the next-links are an optimization
     * so that we don't usually need a backward scan.)
     * <p/>
     * <p>Cancellation introduces some conservatism to the basic
     * algorithms.  Since we must poll for cancellation of other
     * nodes, we can miss noticing whether a cancelled node is
     * ahead or behind us. This is dealt with by always unparking
     * successors upon cancellation, allowing them to stabilize on
     * a new predecessor.
     * <p/>
     * <p>CLH queues need a dummy header node to get started. But
     * we don't create them on construction, because it would be wasted
     * effort if there is never contention. Instead, the node
     * is constructed and head and tail pointers are set upon first
     * contention.
     * <p/>
     * <p>Threads waiting on Conditions use the same nodes, but
     * use an additional link. Conditions only need to link nodes
     * in simple (non-concurrent) linked queues because they are
     * only accessed when exclusively held.  Upon await, a node is
     * inserted into a condition queue.  Upon signal, the node is
     * transferred to the main queue.  A special value of status
     * field is used to mark which queue a node is on.
     * <p/>
     * <p>Thanks go to Dave Dice, Mark Moir, Victor Luchangco, Bill
     * Scherer and Michael Scott, along with members of JSR-166
     * expert group, for helpful ideas, discussions, and critiques
     * on the design of this class.
     */
    static final class Node {   // A special value of status field is used to mark which queue a node is on
        /**
         * waitStatus value to indicate thread has cancelled
         */
        static final int CANCELLED = 1;
        /**
         * waitStatus value to indicate thread needs unparking
         */
        static final int SIGNAL = -1;
        /**
         * waitStatus value to indicate thread is waiting on condition
         */
        static final int CONDITION = -2;
        /**
         * Marker to indicate a node is waiting in shared mode
         */  //共享锁的作用? 肯定是比独占锁吞吐量高的
        static final Node SHARED = new Node();                      //这个空的固定的Node是作为共享的锁, 相对应的等待的节点也是Node, 但是结构是Node(Thread thread, Node mode),会持有线程的引用
        /**
         * Marker to indicate a node is waiting in exclusive mode
         */   //排他锁
        static final Node EXCLUSIVE = null;

        /**
         * Status field, taking on only the values:
         * SIGNAL:     The successor of this node is (or will soon be)    //后续节点已经被阻塞(通过park挂起)或者将要被挂起
         * blocked (via park), so the current node must       //所以当前节点必须唤醒他的后续节点,在他释放或者取消时
         * unpark its successor when it releases or
         * cancels. To avoid races, acquire methods must      //为了避免竞争,获取方法必须先表示他们需要通知signal
         * first indicate they need a signal,
         * then retry the atomic acquire, and then,           //然后重试原子的获取,如果获取失败就继续阻塞
         * on failure, block.
         * CANCELLED:  Node is cancelled due to timeout or interrupt
         * Nodes never leave this state. In particular,
         * a thread with cancelled node never again blocks.
         * CONDITION:  Node is currently on a condition queue
         * It will not be used as a sync queue node until
         * transferred. (Use of this value here
         * has nothing to do with the other uses
         * of the field, but simplifies mechanics.)
         * 0:          None of the above
         * <p/>
         * The values are arranged numerically to simplify use.
         * Non-negative values mean that a node doesn't need to         //非负数的节点不需要通知
         * signal. So, most code doesn't need to check for particular
         * values, just for sign.
         * <p/>
         * The field is initialized to 0 for normal sync nodes, and     //waitStatus初始状态是0,用于正常的sync nodes
         * CONDITION for condition nodes.  It is modified only using    //初始状态是CONDITION 就用于condition nodes, 修改这个值,必须用CAS
         * CAS.
         */
        volatile int waitStatus;

        /**
         * Link to predecessor node that current node/thread relies on
         * for checking waitStatus. Assigned during enqueing, and nulled
         * out (for sake of GC) only upon dequeuing.  Also, upon
         * cancellation of a predecessor, we short-circuit while
         * finding a non-cancelled one, which will always exist
         * because the head node is never cancelled: A node becomes
         * head only as a result of successful acquire. A
         * cancelled thread never succeeds in acquiring, and a thread only
         * cancels itself, not any other node.
         */
        volatile Node prev;

        /**
         * Link to the successor node that the current node/thread
         * unparks upon release. Assigned once during enqueuing, and
         * nulled out (for sake of GC) when no longer needed.  Upon
         * cancellation, we cannot adjust this field, but can notice
         * status and bypass the node if cancelled.  The enq operation
         * does not assign next field of a predecessor until after
         * attachment, so seeing a null next field does not
         * necessarily mean that node is at end of queue. However, if
         * a next field appears to be null, we can scan prev's from
         * the tail to double-check.
         */
        volatile Node next;

        /**
         * The thread that enqueued this node.  Initialized on
         * construction and nulled out after use.
         */
        volatile Thread thread;

        /**
         * Link to next node waiting on condition, or the special
         * value SHARED.  Because condition queues are accessed only
         * when holding in exclusive mode, we just need a simple
         * linked queue to hold nodes while they are waiting on
         * conditions. They are then transferred to the queue to
         * re-acquire. And because conditions can only be exclusive,
         * we save a field by using special value to indicate shared
         * mode.
         */
        Node nextWaiter;

        /**
         * Returns true if node is waiting in shared mode
         */
        final boolean isShared() {
            return nextWaiter == SHARED;
        }

        /**
         * Returns previous node, or throws NullPointerException if
         * null.  Use when predecessor cannot be null.
         *
         * @return the predecessor of this node
         */
        final Node predecessor() throws NullPointerException {
            Node p = prev;
            if (p == null)
                throw new NullPointerException();
            else
                return p;
        }

        Node() {    // Used to establish initial head or SHARED marker
        }

        Node(Thread thread, Node mode) {     // Used by addWaiter
            this.nextWaiter = mode;         //新创建一个node时,指定node的模式:独占锁或者共享锁
            this.thread = thread;
        }

        Node(Thread thread, int waitStatus) { // Used by Condition
            this.waitStatus = waitStatus;
            this.thread = thread;
        }
    }

    /**
     * Head of the wait queue, lazily initialized.  Except for
     * initialization, it is modified only via method setHead.  Note:
     * If head exists, its waitStatus is guaranteed not to be
     * CANCELLED.
     */
    private transient volatile Node head;

    /**
     * Tail of the wait queue, lazily initialized.  Modified only via
     * method enq to add new wait node.
     */
    private transient volatile Node tail;

    /**
     * The synchronization state.
     */
    private volatile int state;

    /**
     * Returns the current value of synchronization state.
     * This operation has memory semantics of a <tt>volatile</tt> read.
     *
     * @return current state value
     */
    protected final int getState() {
        return state;
    }

    /**
     * Sets the value of synchronization state.
     * This operation has memory semantics of a <tt>volatile</tt> write.
     *
     * @param newState the new state value
     */
    protected final void setState(int newState) {
        state = newState;
    }

    /**
     * Atomically sets synchronization state to the given updated
     * value if the current state value equals the expected value.
     * This operation has memory semantics of a <tt>volatile</tt> read
     * and write.
     *
     * @param expect the expected value
     * @param update the new value
     * @return true if successful. False return indicates that
     * the actual value was not equal to the expected value.
     */
    protected final boolean compareAndSetState(int expect, int update) {
        // See below for intrinsics setup to support this
        return unsafe.compareAndSwapInt(this, stateOffset, expect, update);
    }

    // Queuing utilities

    /**
     * Insert node into queue, initializing if necessary. See picture above.
     *
     * @param node the node to insert
     * @return node's predecessor
     */
    private Node enq(final Node node) {     //这种算法好像队列不能为空,为空时头尾指针都指向这个空节点; enq相对应的就是出队,出队怎么操作的?
        for (; ; ) {      //这里用到了自旋
            Node t = tail;  //t指向当前的tail
            if (t == null) { // Must initialize --> 如果当前tail还是空,就需要初始化了
                Node h = new Node(); // Dummy header    --> 创建一个空的节点,作为虚拟的header, 为什么是一个Dummy??
                h.next = node;      //h后缀指向node
                node.prev = h;      //node前缀指向h,这时这个队列就只有h和node两个节点了
                if (compareAndSetHead(h)) {     //CAS设置队列的头,失败则会继续重试; 下一次进来,如果t!=null,则进入到下面的else;
                    tail = node;                //如果CAS成功了,则将tail指向当前node
                    return h;                   //返回h,就是返回空的头节点
                }
            } else {
                node.prev = t;     //如果t!=null,也就是末尾节点指向某个节点了则将node的前缀指向t
                if (compareAndSetTail(t, node)) {   //CAS设置末尾节点tail指向当前节点,这里失败会一直重试,直到成功为止;
                    t.next = node;                  //成功后,末尾节点的后缀指向当前节点,所以当前节点就成为末尾节点了.
                    return t;                       //这里的想法是如果多线程出现竞争导致CAS失败,就继续重试,最终保证node会插入到队列中.
                }                                   //可能失败了多次,最后插入进去成为末尾节点;也可能一次就成功了,但是又被其他重试的线程替换了
            }
        }
    }

    /**
     * Create and enq node for given thread and mode
     *
     * @param mode    Node.EXCLUSIVE for exclusive, Node.SHARED for shared  (共享锁和独占锁的node在一个队列里面)
     * @return the new node
     */
    private Node addWaiter(Node mode) {
        Node node = new Node(Thread.currentThread(), mode);
        // Try the fast path of enq; backup to full enq on failure
        Node pred = tail;   //pred指向当前的tail
        if (pred != null) { //判断等待队列中有节点,也就是pred不为空;
            node.prev = pred;      //新创建的节点,前缀指向pred; 也就是新创建的node,在当前末尾节点之后;
            if (compareAndSetTail(pred, node)) {    //CAS, 设置末尾节点tail 指向新创建的node, (整个Node链表是通过各自前后缀相连的;tail只需要一直指向末尾节点就行了)
                // 这个unsafe的CAS操作可能失败吗? 需要重试吗??? 毕竟CAS都是伴随着自旋重试的
                // 不需要重试:如果CAS成功了,就继续下面的流程; 如果CAS失败了,就放弃本次CAS,后果就是tail会被另外一个线程指向他指向的另外一个节点
                // 放弃本次CAS后, 会继续执行下面的enq(node), 继续往下看
                pred.next = node;       //设置末尾节点的后缀,是当前节点; 现在node就已经放置到末尾了
                return node;            //返回新的末尾节点node;
            }
        }
        enq(node);      //走到这里的话,如果是因为pred != null过来的,则会初始化等待队列; 如果是因为CAS失败的,会做什么操作呢?
        return node;    //返回刚放进去的新创建的节点
    }

    /**
     * Set head of queue to be node, thus dequeuing. Called only by
     * acquire methods.  Also nulls out unused fields for sake of GC
     * and to suppress unnecessary signals and traversals.
     *
     * @param node the node
     */
    private void setHead(Node node) {   //设置head指向 node
        head = node;
        node.thread = null;             //node的当前线程设置空
        node.prev = null;               //node的前缀指向空
    }

    /**
     * Wake up node's successor, if one exists.     //如果node有后续节点的话,唤醒他们
     *
     * @param node the node
     */
    private void unparkSuccessor(Node node) {
        /*
         * Try to clear status in anticipation of signalling.  It is    //为了signal 先清理node的 waitStatus,设置为0
         * OK if this fails or if status is changed by waiting thread.  //清理失败 或者 waitStatus 被等待线程修改了,都是OK的?
         */
        compareAndSetWaitStatus(node, Node.SIGNAL, 0);  //修改node waitStatus 从SIGNAL 到 0
        
        /*
         * Thread to unpark is held in successor, which is normally
         * just the next node.  But if cancelled or apparently null,
         * traverse backwards from tail to find the actual
         * non-cancelled successor.
         */
        Thread thread;
        Node s = node.next;
        if (s != null && s.waitStatus <= 0)     //下一个结点非空 并且 状态<=0 (大于0表示被取消了)
            thread = s.thread;                  //thread就指向下一个结点
        else {
            thread = null;
            for (s = tail; s != null && s != node; s = s.prev)  //否则从tail开始依次寻找<=0的节点,找到最前面的一个线程(队列里的第一个) 为什么从尾部开始找? 用这种办法可以跳过取消的节点
                if (s.waitStatus <= 0)
                    thread = s.thread;
        }
        LockSupport.unpark(thread);     //唤醒找到的第一个线程,给他一个许可,线程立马就被唤醒了
    }

    /**
     * Set head of queue, and check if successor may be waiting
     * in shared mode, if so propagating if propagate > 0.
     *
     * @param node      the node
     * @param propagate the return value from a tryAcquireShared
     */
    private void setHeadAndPropagate(Node node, int propagate) {
        setHead(node);
        if (propagate > 0 && node.waitStatus != 0) {    //propagate 是 tryAcquireShared的结果,获取成功后,并且waitStatus非0
            /*
             * Don't bother fully figuring out successor.  If it
             * looks null, call unparkSuccessor anyway to be safe.
             */
            Node s = node.next;
            if (s == null || s.isShared())  //node的后缀指向空或者node后一个是共享mode;
                unparkSuccessor(node);      //后面一个是空的或者是共享的,就需要唤醒Condition队列中的独占锁 (比如CountDown计数??)
        }
    }

    // Utilities for various versions of acquire

    /**
     * Cancel an ongoing attempt to acquire.
     *
     * @param node the node
     */
    private void cancelAcquire(Node node) {
        // Ignore if node doesn't exist
        if (node == null)
            return;

        node.thread = null;

        // Skip cancelled predecessors
        Node pred = node.prev;
        while (pred.waitStatus > 0)
            node.prev = pred = pred.prev;

        // Getting this before setting waitStatus ensures staleness
        Node predNext = pred.next;

        // Can use unconditional write instead of CAS here
        node.waitStatus = Node.CANCELLED;

        // If we are the tail, remove ourselves
        if (node == tail && compareAndSetTail(node, pred)) {
            compareAndSetNext(pred, predNext, null);
        } else {
            // If "active" predecessor found...
            if (pred != head
                    && (pred.waitStatus == Node.SIGNAL
                    || compareAndSetWaitStatus(pred, 0, Node.SIGNAL))
                    && pred.thread != null) {

                // If successor is active, set predecessor's next link
                Node next = node.next;
                if (next != null && next.waitStatus <= 0)
                    compareAndSetNext(pred, predNext, next);
            } else {
                unparkSuccessor(node);
            }

            node.next = node; // help GC
        }
    }

    /**
     * Checks and updates status for a node that failed to acquire.     //当node获取竞争失败时,检查和更新waitStatus
     * Returns true if thread should block. This is the main signal     //如果线程需要阻塞就返回true. 所有的循环获取都会调用这个核心的signal控制方法
     * control in all acquire loops.  Requires that pred == node.prev   //需要 pred == node.prev
     *
     * @param pred node's predecessor holding status
     * @param node the node
     * @return true if thread should block
     */
    private static boolean shouldParkAfterFailedAcquire(Node pred, Node node) {
        int s = pred.waitStatus;    //node的前一个节点
        if (s < 0)
            /*
             * This node has already set status asking a release    //表示节点已经设置状态,要求release之后要通知node节点,所以可以阻塞他
             * to signal it, so it can safely park
             */
            return true;
        if (s > 0) {    //前一个节点被取消了,跳过他
            /*
             * Predecessor was cancelled. Skip over predecessors and
             * indicate retry.
             */
            do {
                node.prev = pred = pred.prev;
            } while (pred.waitStatus > 0);  //往前递归寻找,直到找到一个waitStatus<=0的节点
            pred.next = node;
        } else      //这种情况就是 s==0
            /*
             * Indicate that we need a signal, but don't park yet. Caller   //表示需要singal,但是还没有挂起
             * will need to retry to make sure it cannot acquire before     //调用者需要重试,确保他能在挂起前被获取
             * parking.
             */
            compareAndSetWaitStatus(pred, 0, Node.SIGNAL);                  //CAS修改前一个节点 status 从0到-1???还是没搞懂
        return false;   //不可以阻塞
    }

    /**
     * Convenience method to interrupt current thread.
     */
    private static void selfInterrupt() {
        Thread.currentThread().interrupt();
    }

    /**
     * Convenience method to park and then check if interrupted
     *
     * @return true if interrupted
     */
    private static boolean parkAndCheckInterrupt() {//获取许可,当获取不到许可时会挂起当前线程; 这个阻塞会响应中断的,但是不会抛出异常,直接中断当前线程,停止获取许可
        LockSupport.park();     // !!! 当前线程等待获取许可; 直到releaseShared 里 unpark 这个等待的线程,才能继续往下走
        return Thread.interrupted();
    }

    /*
     * Various flavors of acquire, varying in exclusive/shared and
     * control modes.  Each is mostly the same, but annoyingly
     * different.  Only a little bit of factoring is possible due to
     * interactions of exception mechanics (including ensuring that we
     * cancel if tryAcquire throws exception) and other control, at
     * least not without hurting performance too much. 
     */

    /**
     * Acquire in exclusive uninterruptible mode for thread already in
     * queue. Used by condition wait methods as well as acquire.    (用于condition wait和acquire)
     *
     * @param node the node
     * @param arg  the acquire argument
     * @return true if interrupted while waiting
     */
    final boolean acquireQueued(final Node node, int arg) {     //node 是addWaiter返回的新添加的node
        try {
            boolean interrupted = false;
            for (; ; ) {
                final Node p = node.predecessor();
                if (p == head && tryAcquire(arg)) { //node的前缀是第一个node,并且acquire成功
                    setHead(node);      //设置新的Head为node,并且把node前缀置空,线程置空,node现在就是头结点
                    p.next = null; // help GC --> p.next不指向任何节点
                    return interrupted;
                }
                if (shouldParkAfterFailedAcquire(p, node) &&
                        parkAndCheckInterrupt())    //获取许可时会阻塞
                    interrupted = true;
            }
        } catch (RuntimeException ex) {
            cancelAcquire(node);
            throw ex;
        }
    }

    /**
     * Acquire in exclusive interruptible mode
     *
     * @param arg the acquire argument
     */
    private void doAcquireInterruptibly(int arg)
            throws InterruptedException {
        final Node node = addWaiter(Node.EXCLUSIVE);
        try {
            for (; ; ) {
                final Node p = node.predecessor();
                if (p == head && tryAcquire(arg)) {
                    setHead(node);
                    p.next = null; // help GC
                    return;
                }
                if (shouldParkAfterFailedAcquire(p, node) &&
                        parkAndCheckInterrupt())
                    break;
            }
        } catch (RuntimeException ex) {
            cancelAcquire(node);
            throw ex;
        }
        // Arrive here only if interrupted  只有中断才会到这里
        cancelAcquire(node);
        throw new InterruptedException();
    }

    /**
     * Acquire in exclusive timed mode
     *
     * @param arg          the acquire argument
     * @param nanosTimeout max wait time
     * @return true if acquired
     */
    private boolean doAcquireNanos(int arg, long nanosTimeout)
            throws InterruptedException {
        long lastTime = System.nanoTime();
        final Node node = addWaiter(Node.EXCLUSIVE);
        try {
            for (; ; ) {
                final Node p = node.predecessor();
                if (p == head && tryAcquire(arg)) {
                    setHead(node);
                    p.next = null; // help GC
                    return true;
                }
                if (nanosTimeout <= 0) {
                    cancelAcquire(node);
                    return false;
                }
                if (shouldParkAfterFailedAcquire(p, node)) {
                    LockSupport.parkNanos(nanosTimeout);
                    if (Thread.interrupted())
                        break;
                    long now = System.nanoTime();
                    nanosTimeout -= now - lastTime;
                    lastTime = now;
                }
            }
        } catch (RuntimeException ex) {
            cancelAcquire(node);
            throw ex;
        }
        // Arrive here only if interrupted
        cancelAcquire(node);
        throw new InterruptedException();
    }

    /**
     * Acquire in shared uninterruptible mode
     *
     * @param arg the acquire argument
     */
    private void doAcquireShared(int arg) {     //获取共享锁 arg参数只在批量获取信号量许可的方法中有意义
        final Node node = addWaiter(Node.SHARED);   //增加等待的共享队列, 模式是共享mode : Node.SHARED ,会创建一个节点,添加到当前共享锁的等待队列里面(等待队列就是维护的一个链表)
        try {
            boolean interrupted = false;        //doAcquireShared 这个方法执行的时候,不响应中断
            for (; ; ) {
                final Node p = node.predecessor();  //返回node的前一个节点;
                if (p == head) {                    //如果前一个正好是头节点;这一步会一直重试,直到找到第一个头结点p
                    int r = tryAcquireShared(arg);  //调用子类的方法,根据返回结果正负,判断是否成功
                    if (r >= 0) {
                        setHeadAndPropagate(node, r);   //获取成功,就设置为头节点,并且方法里面会唤醒后续的节点
                        p.next = null; // help GC   --> p是node的前一个节点,前一个节点的next指向空;p已经是头节点了,pre也是指向空的,会被GC; 队列中p就被释放出去了
                        if (interrupted)        //相比较下面的doAcquireSharedInterruptibly方法,这个就是用来相应中断的;下面的方法不响应中断;
                            selfInterrupt();    //虽然此方法不响应中断,但是任然会调用Thread.currentThread().interrupt()方法, 防止当前线程其他方法响应中断?
                        return;
                    }
                }   //下面的情况就是p不是头节点的情况了
                //在p != head 或者 tryAcquireShared<0时会走到这里
                if (shouldParkAfterFailedAcquire(p, node) &&    //判断acquire失败是否需要挂起线程; 这个主要是为了判断是否需要挂起
                        parkAndCheckInterrupt())                //parkAndCheckInterrupt获取许可时会阻塞,但是阻塞的线程什么时候被唤醒呢???看看releaseShared
                    interrupted = true;                         //设置true后,上面的 selfInterrupt 会检查中断的
            }
        } catch (RuntimeException ex) {
            cancelAcquire(node);
            throw ex;
        }
    }

    /**
     * Acquire in shared interruptible mode
     *
     * @param arg the acquire argument
     */
    private void doAcquireSharedInterruptibly(int arg)  //同doAcquireShared,不同的是这个方法会检查中断
            throws InterruptedException {
        final Node node = addWaiter(Node.SHARED);
        try {
            for (; ; ) {
                final Node p = node.predecessor();
                if (p == head) {
                    int r = tryAcquireShared(arg);
                    if (r >= 0) {
                        setHeadAndPropagate(node, r);
                        p.next = null; // help GC
                        return;
                    }
                }
                if (shouldParkAfterFailedAcquire(p, node) &&
                        parkAndCheckInterrupt())            //parkAndCheckInterrupt 会检查中断状态,如果中断了就跳出循环 (用这种方法响应中断)
                    break;
            }
        } catch (RuntimeException ex) {
            cancelAcquire(node);
            throw ex;
        }
        // Arrive here only if interrupted
        cancelAcquire(node);
        throw new InterruptedException();
    }

    /**
     * Acquire in shared timed mode
     *
     * @param arg          the acquire argument
     * @param nanosTimeout max wait time
     * @return true if acquired
     */
    private boolean doAcquireSharedNanos(int arg, long nanosTimeout)
            throws InterruptedException {

        long lastTime = System.nanoTime();
        final Node node = addWaiter(Node.SHARED);
        try {
            for (; ; ) {
                final Node p = node.predecessor();
                if (p == head) {
                    int r = tryAcquireShared(arg);
                    if (r >= 0) {
                        setHeadAndPropagate(node, r);
                        p.next = null; // help GC
                        return true;
                    }
                }
                if (nanosTimeout <= 0) {
                    cancelAcquire(node);
                    return false;
                }
                if (shouldParkAfterFailedAcquire(p, node)) {
                    LockSupport.parkNanos(nanosTimeout);
                    if (Thread.interrupted())
                        break;
                    long now = System.nanoTime();
                    nanosTimeout -= now - lastTime;
                    lastTime = now;
                }
            }
        } catch (RuntimeException ex) {
            cancelAcquire(node);
            throw ex;
        }
        // Arrive here only if interrupted
        cancelAcquire(node);
        throw new InterruptedException();
    }

    // Main exported methods 

    /**
     * Attempts to acquire in exclusive mode. This method should query
     * if the state of the object permits it to be acquired in the
     * exclusive mode, and if so to acquire it.
     * <p/>
     * <p>This method is always invoked by the thread performing
     * acquire.  If this method reports failure, the acquire method
     * may queue the thread, if it is not already queued, until it is
     * signalled by a release from some other thread. This can be used
     * to implement method {@link Lock#tryLock()}.
     * <p/>
     * <p>The default
     * implementation throws {@link UnsupportedOperationException}
     *
     * @param arg the acquire argument. This value
     *            is always the one passed to an acquire method,
     *            or is the value saved on entry to a condition wait.
     *            The value is otherwise uninterpreted and can represent anything
     *            you like.
     * @return true if successful. Upon success, this object has been
     * acquired.
     * @throws IllegalMonitorStateException  if acquiring would place
     *                                       this synchronizer in an illegal state. This exception must be
     *                                       thrown in a consistent fashion for synchronization to work
     *                                       correctly.
     * @throws UnsupportedOperationException if exclusive mode is not supported
     */
    protected boolean tryAcquire(int arg) {
        throw new UnsupportedOperationException();
    }

    /**
     * Attempts to set the state to reflect a release in exclusive
     * mode.  <p>This method is always invoked by the thread
     * performing release.
     * <p/>
     * <p>The default implementation throws
     * {@link UnsupportedOperationException}
     *
     * @param arg the release argument. This value
     *            is always the one passed to a release method,
     *            or the current state value upon entry to a condition wait.
     *            The value is otherwise uninterpreted and can represent anything
     *            you like.
     * @return <tt>true</tt> if this object is now in a fully released state,
     * so that any waiting threads may attempt to acquire; and <tt>false</tt>
     * otherwise.
     * @throws IllegalMonitorStateException  if releasing would place
     *                                       this synchronizer in an illegal state. This exception must be
     *                                       thrown in a consistent fashion for synchronization to work
     *                                       correctly.
     * @throws UnsupportedOperationException if exclusive mode is not supported
     */
    protected boolean tryRelease(int arg) {
        throw new UnsupportedOperationException();
    }

    /**
     * Attempts to acquire in shared mode. This method should query if
     * the state of the object permits it to be acquired in the shared
     * mode, and if so to acquire it.
     * <p/>
     * <p>This method is always invoked by the thread performing
     * acquire.  If this method reports failure, the acquire method
     * may queue the thread, if it is not already queued, until it is
     * signalled by a release from some other thread.
     * <p/>
     * <p>The default implementation throws {@link
     * UnsupportedOperationException}
     *
     * @param arg the acquire argument. This value
     *            is always the one passed to an acquire method,
     *            or is the value saved on entry to a condition wait.
     *            The value is otherwise uninterpreted and can represent anything
     *            you like.
     * @return a negative value on failure, zero on exclusive success,
     * and a positive value if non-exclusively successful, in which
     * case a subsequent waiting thread must check
     * availability. (Support for three different return values
     * enables this method to be used in contexts where acquires only
     * sometimes act exclusively.)  Upon success, this object has been
     * acquired.
     * @throws IllegalMonitorStateException  if acquiring would place
     *                                       this synchronizer in an illegal state. This exception must be
     *                                       thrown in a consistent fashion for synchronization to work
     *                                       correctly.
     * @throws UnsupportedOperationException if shared mode is not supported
     */
    protected int tryAcquireShared(int arg) {
        throw new UnsupportedOperationException();
    }

    /**
     * Attempts to set the state to reflect a release in shared mode.
     * <p>This method is always invoked by the thread performing release.
     * <p> The default implementation throws
     * {@link UnsupportedOperationException}
     *
     * @param arg the release argument. This value
     *            is always the one passed to a release method,
     *            or the current state value upon entry to a condition wait.
     *            The value is otherwise uninterpreted and can represent anything
     *            you like.
     * @return <tt>true</tt> if this object is now in a fully released state,
     * so that any waiting threads may attempt to acquire; and <tt>false</tt>
     * otherwise.
     * @throws IllegalMonitorStateException  if releasing would place
     *                                       this synchronizer in an illegal state. This exception must be
     *                                       thrown in a consistent fashion for synchronization to work
     *                                       correctly.
     * @throws UnsupportedOperationException if shared mode is not supported
     */
    protected boolean tryReleaseShared(int arg) {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns true if synchronization is held exclusively with respect
     * to the current (calling) thread.  This method is invoked
     * upon each call to a non-waiting {@link ConditionObject} method.
     * (Waiting methods instead invoke {@link #release}.)
     * <p>The default implementation throws {@link
     * UnsupportedOperationException}. This method is invoked
     * internally only within {@link ConditionObject} methods, so need
     * not be defined if conditions are not used.
     *
     * @return true if synchronization is held exclusively;
     * else false
     * @throws UnsupportedOperationException if conditions are not supported
     */
    protected boolean isHeldExclusively() {
        throw new UnsupportedOperationException();
    }

    /**
     * Acquires in exclusive mode, ignoring interrupts.  Implemented    (获取独占锁)
     * by invoking at least once {@link #tryAcquire},
     * returning on success.  Otherwise the thread is queued, possibly  (获取成功就返回,否则进入等待队列)
     * repeatedly blocking and unblocking, invoking {@link
     * #tryAcquire} until success.  This method can be used
     * to implement method {@link Lock#lock}
     *
     * @param arg the acquire argument.
     *            This value is conveyed to {@link #tryAcquire} but is
     *            otherwise uninterpreted and can represent anything
     *            you like.
     */
    public final void acquire(int arg) {    //acquireShared 是获取共享锁,这个是获取独占锁
        if (!tryAcquire(arg) &&
                acquireQueued(addWaiter(Node.EXCLUSIVE), arg))  //acquireQueued() 里面会获取许可,获取不到会阻塞的
            selfInterrupt();
    }

    /**
     * Acquires in exclusive mode, aborting if interrupted.
     * Implemented by first checking interrupt status, then invoking
     * at least once {@link #tryAcquire}, returning on
     * success.  Otherwise the thread is queued, possibly repeatedly
     * blocking and unblocking, invoking {@link #tryAcquire}
     * until success or the thread is interrupted.  This method can be
     * used to implement method {@link Lock#lockInterruptibly}
     *
     * @param arg the acquire argument.
     *            This value is conveyed to {@link #tryAcquire} but is
     *            otherwise uninterpreted and can represent anything
     *            you like.
     * @throws InterruptedException if the current thread is interrupted
     */
    public final void acquireInterruptibly(int arg) throws InterruptedException {
        if (Thread.interrupted())
            throw new InterruptedException();
        if (!tryAcquire(arg))
            doAcquireInterruptibly(arg);
    }

    /**
     * Attempts to acquire in exclusive mode, aborting if interrupted,
     * and failing if the given timeout elapses.  Implemented by first
     * checking interrupt status, then invoking at least once {@link
     * #tryAcquire}, returning on success.  Otherwise, the thread is
     * queued, possibly repeatedly blocking and unblocking, invoking
     * {@link #tryAcquire} until success or the thread is interrupted
     * or the timeout elapses.  This method can be used to implement
     * method {@link Lock#tryLock(long, TimeUnit)}.
     *
     * @param arg          the acquire argument.
     *                     This value is conveyed to {@link #tryAcquire} but is
     *                     otherwise uninterpreted and can represent anything
     *                     you like.
     * @param nanosTimeout the maximum number of nanoseconds to wait
     * @return true if acquired; false if timed out
     * @throws InterruptedException if the current thread is interrupted
     */
    public final boolean tryAcquireNanos(int arg, long nanosTimeout) throws InterruptedException {
        if (Thread.interrupted())
            throw new InterruptedException();
        return tryAcquire(arg) ||
                doAcquireNanos(arg, nanosTimeout);
    }

    /**
     * Releases in exclusive mode.  Implemented by unblocking one or
     * more threads if {@link #tryRelease} returns true.
     * This method can be used to implement method {@link Lock#unlock}
     *
     * @param arg the release argument.
     *            This value is conveyed to {@link #tryRelease} but is
     *            otherwise uninterpreted and can represent anything
     *            you like.
     * @return the value returned from {@link #tryRelease}
     */
    public final boolean release(int arg) {
        if (tryRelease(arg)) {
            Node h = head;
            if (h != null && h.waitStatus != 0)
                unparkSuccessor(h);
            return true;
        }
        return false;
    }

    /**
     * Acquires in shared mode, ignoring interrupts.  Implemented by
     * first invoking at least once {@link #tryAcquireShared},
     * returning on success.  Otherwise the thread is queued, possibly
     * repeatedly blocking and unblocking, invoking {@link
     * #tryAcquireShared} until success.
     *
     * @param arg the acquire argument.
     *            This value is conveyed to {@link #tryAcquireShared} but is
     *            otherwise uninterpreted and can represent anything
     *            you like.
     */
    public final void acquireShared(int arg) {
        if (tryAcquireShared(arg) < 0)  //小于0表示没有许可,需要阻塞
            doAcquireShared(arg);
    }

    /**
     * Acquires in shared mode, aborting if interrupted.  Implemented       //获取共享锁,方法开始前检查中断标记
     * by first checking interrupt status, then invoking at least once
     * {@link #tryAcquireShared}, returning on success.  Otherwise the      //至少执行一次tryAcquireShared,成功就返回
     * thread is queued, possibly repeatedly blocking and unblocking,       //否则线程会被加入等待队列,可能多次阻塞/唤醒,直到获取共享锁成功或者线程被中断
     * invoking {@link #tryAcquireShared} until success or the thread
     * is interrupted.
     *
     * @param arg the acquire argument.
     *            This value is conveyed to {@link #tryAcquireShared} but is
     *            otherwise uninterpreted and can represent anything
     *            you like.
     * @throws InterruptedException if the current thread is interrupted
     */
    public final void acquireSharedInterruptibly(int arg) throws InterruptedException {
        if (Thread.interrupted())
            throw new InterruptedException();
        if (tryAcquireShared(arg) < 0)      //调用子类复写的方法
            doAcquireSharedInterruptibly(arg);
    }

    /**
     * Attempts to acquire in shared mode, aborting if interrupted, and
     * failing if the given timeout elapses.  Implemented by first
     * checking interrupt status, then invoking at least once {@link
     * #tryAcquireShared}, returning on success.  Otherwise, the
     * thread is queued, possibly repeatedly blocking and unblocking,
     * invoking {@link #tryAcquireShared} until success or the thread
     * is interrupted or the timeout elapses.
     *
     * @param arg          the acquire argument.
     *                     This value is conveyed to {@link #tryAcquireShared} but is
     *                     otherwise uninterpreted and can represent anything
     *                     you like.
     * @param nanosTimeout the maximum number of nanoseconds to wait
     * @return true if acquired; false if timed out
     * @throws InterruptedException if the current thread is interrupted
     */
    public final boolean tryAcquireSharedNanos(int arg, long nanosTimeout) throws InterruptedException {
        if (Thread.interrupted())
            throw new InterruptedException();
        return tryAcquireShared(arg) >= 0 ||
                doAcquireSharedNanos(arg, nanosTimeout);
    }

    /**
     * Releases in shared mode.  Implemented by unblocking one or more
     * threads if {@link #tryReleaseShared} returns true.   //tryReleaseShared方法执行成功会给一个或多个线程许可 (但是许可只有一个???) todo
     *
     * @param arg the release argument.
     *            This value is conveyed to {@link #tryReleaseShared} but is
     *            otherwise uninterpreted and can represent anything            //arg可以没有意义,也可表示任何意思
     *            you like.
     * @return the value returned from {@link #tryReleaseShared}
     */
    public final boolean releaseShared(int arg) {   //释放共享锁, arg只在semaphore里面有用吧; 共享锁的控制大概就是维护一个等待队列,唤醒的时候唤醒一个线程就行了.
        if (tryReleaseShared(arg)) {    //调用子类的方法释放成功后
            Node h = head;
            if (h != null && h.waitStatus != 0)     //头结点不为空,并且waitStatus不为0 waitStatus的真正意义? 维护一个队列,如果其中某个节点被取消了或者之类的操作,直接修改节点的状态就行了,后续会判断状态的
                unparkSuccessor(h);     //释放头节点, unpack释放一个许可, 线程就立马被唤醒了?
            return true;
        }
        return false;
    }

    // Queue inspection methods

    /**
     * Queries whether any threads are waiting to acquire. Note that
     * because cancellations due to interrupts and timeouts may occur
     * at any time, a <tt>true</tt> return does not guarantee that any
     * other thread will ever acquire.
     * <p/>
     * <p> In this implementation, this operation returns in
     * constant time.
     *
     * @return true if there may be other threads waiting to acquire
     * the lock.
     */
    public final boolean hasQueuedThreads() {
        return head != tail;
    }

    /**
     * Queries whether any threads have ever contended to acquire this
     * synchronizer; that is if an acquire method has ever blocked.
     * <p/>
     * <p> In this implementation, this operation returns in
     * constant time.
     *
     * @return true if there has ever been contention
     */
    public final boolean hasContended() {
        return head != null;
    }

    /**
     * Returns the first (longest-waiting) thread in the queue, or
     * <tt>null</tt> if no threads are currently queued.
     * <p/>
     * <p> In this implementation, this operation normally returns in
     * constant time, but may iterate upon contention if other threads are
     * concurrently modifying the queue.
     *
     * @return the first (longest-waiting) thread in the queue, or
     * <tt>null</tt> if no threads are currently queued.
     */
    public final Thread getFirstQueuedThread() {
        // handle only fast path, else relay
        return (head == tail) ? null : fullGetFirstQueuedThread();
    }

    /**
     * Version of getFirstQueuedThread called when fastpath fails
     */
    private Thread fullGetFirstQueuedThread() {
        Node h = head;
        if (h == null)                    // No queue
            return null;

            /*
             * The first node is normally h.next. Try to get its
             * thread field, ensuring consistent reads: If thread
             * field is nulled out or s.prev is no longer head, then
             * some other thread(s) concurrently performed setHead in
         * between some of our reads.
             */
        Node s = h.next;
        if (s != null) {
            Thread st = s.thread;
            Node sp = s.prev;
            if (st != null && sp == head)
                return st;
        }

            /*
         * Head's next field might not have been set yet, or may have
         * been unset after setHead. So we must check to see if tail
         * is actually first node. If not, we continue on, safely
         * traversing from tail back to head to find first,
         * guaranteeing termination.
             */

        Node t = tail;
        Thread firstThread = null;
        while (t != null && t != head) {
            Thread tt = t.thread;
            if (tt != null)
                firstThread = tt;
            t = t.prev;
        }
        return firstThread;
    }

    /**
     * Returns true if the given thread is currently queued.
     * <p/>
     * <p> This implementation traverses the queue to determine
     * presence of the given thread.
     *
     * @param thread the thread
     * @return true if the given thread in on the queue
     * @throws NullPointerException if thread null
     */
    public final boolean isQueued(Thread thread) {
        if (thread == null)
            throw new NullPointerException();
        for (Node p = tail; p != null; p = p.prev)
            if (p.thread == thread)
                return true;
        return false;
    }

    // Instrumentation and monitoring methods

    /**
     * Returns an estimate of the number of threads waiting to
     * acquire.  The value is only an estimate because the number of
     * threads may change dynamically while this method traverses
     * internal data structures.  This method is designed for use in
     * monitoring system state, not for synchronization
     * control.
     *
     * @return the estimated number of threads waiting for this lock
     */
    public final int getQueueLength() {
        int n = 0;
        for (Node p = tail; p != null; p = p.prev) {
            if (p.thread != null)
                ++n;
        }
        return n;
    }

    /**
     * Returns a collection containing threads that may be waiting to
     * acquire.  Because the actual set of threads may change
     * dynamically while constructing this result, the returned
     * collection is only a best-effort estimate.  The elements of the
     * returned collection are in no particular order.  This method is
     * designed to facilitate construction of subclasses that provide
     * more extensive monitoring facilities.
     *
     * @return the collection of threads
     */
    public final Collection<Thread> getQueuedThreads() {
        ArrayList<Thread> list = new ArrayList<Thread>();
        for (Node p = tail; p != null; p = p.prev) {
            Thread t = p.thread;
            if (t != null)
                list.add(t);
        }
        return list;
    }

    /**
     * Returns a collection containing threads that may be waiting to
     * acquire in exclusive mode. This has the same properties
     * as {@link #getQueuedThreads} except that it only returns
     * those threads waiting due to an exclusive acquire.
     *
     * @return the collection of threads
     */
    public final Collection<Thread> getExclusiveQueuedThreads() {
        ArrayList<Thread> list = new ArrayList<Thread>();
        for (Node p = tail; p != null; p = p.prev) {
            if (!p.isShared()) {
                Thread t = p.thread;
                if (t != null)
                    list.add(t);
            }
        }
        return list;
    }

    /**
     * Returns a collection containing threads that may be waiting to
     * acquire in shared mode. This has the same properties
     * as {@link #getQueuedThreads} except that it only returns
     * those threads waiting due to a shared acquire.
     *
     * @return the collection of threads
     */
    public final Collection<Thread> getSharedQueuedThreads() {
        ArrayList<Thread> list = new ArrayList<Thread>();
        for (Node p = tail; p != null; p = p.prev) {
            if (p.isShared()) {
                Thread t = p.thread;
                if (t != null)
                    list.add(t);
            }
        }
        return list;
    }

    /**
     * Returns a string identifying this synchronizer, as well as its
     * state.  The state, in brackets, includes the String &quot;State
     * =&quot; followed by the current value of {@link #getState}, and
     * either &quot;nonempty&quot; or &quot;empty&quot; depending on
     * whether the queue is empty.
     *
     * @return a string identifying this synchronizer, as well as its state.
     */
    public String toString() {
        int s = getState();
        String q = hasQueuedThreads() ? "non" : "";
        return super.toString() +
                "[State = " + s + ", " + q + "empty queue]";
    }


    // Internal support methods for Conditions

    /**
     * Returns true if a node, always one that was initially placed on
     * a condition queue, is now waiting to reacquire on sync queue.
     *
     * @param node the node
     * @return true if is reacquiring
     */
    final boolean isOnSyncQueue(Node node) {
        if (node.waitStatus == Node.CONDITION || node.prev == null)
            return false;
        if (node.next != null) // If has successor, it must be on queue
            return true;
        /*
         * node.prev can be non-null, but not yet on queue because
         * the CAS to place it on queue can fail. So we have to
         * traverse from tail to make sure it actually made it.  It
         * will always be near the tail in calls to this method, and
         * unless the CAS failed (which is unlikely), it will be
         * there, so we hardly ever traverse much.
         */
        return findNodeFromTail(node);
    }

    /**
     * Returns true if node is on sync queue by searching backwards from tail.
     * Called only when needed by isOnSyncQueue.
     *
     * @return true if present
     */
    private boolean findNodeFromTail(Node node) {
        Node t = tail;
        for (; ; ) {
            if (t == node)
                return true;
            if (t == null)
                return false;
            t = t.prev;
        }
    }

    /**
     * Transfers a node from a condition queue onto sync queue.
     * Returns true if successful.
     *
     * @param node the node
     * @return true if successfully transferred (else the node was
     * cancelled before signal).
     */
    final boolean transferForSignal(Node node) {
        /*
         * If cannot change waitStatus, the node has been cancelled.
         */
        if (!compareAndSetWaitStatus(node, Node.CONDITION, 0))
            return false;

        /*
         * Splice onto queue and try to set waitStatus of predecessor to
         * indicate that thread is (probably) waiting. If cancelled or
         * attempt to set waitStatus fails, wake up to resync (in which
         * case the waitStatus can be transiently and harmlessly wrong).
         */
        Node p = enq(node);
        int c = p.waitStatus;
        if (c > 0 || !compareAndSetWaitStatus(p, c, Node.SIGNAL))
            LockSupport.unpark(node.thread);
        return true;
    }

    /**
     * Transfers node, if necessary, to sync queue after a cancelled
     * wait. Returns true if thread was cancelled before being
     * signalled.
     *
     * @param node    its node
     * @return true if cancelled before the node was signalled.
     */
    final boolean transferAfterCancelledWait(Node node) {
        if (compareAndSetWaitStatus(node, Node.CONDITION, 0)) {
            enq(node);
            return true;
        }
        /*
         * If we lost out to a signal(), then we can't proceed
         * until it finishes its enq().  Cancelling during an
         * incomplete transfer is both rare and transient, so just
         * spin.
         */
        while (!isOnSyncQueue(node))
            Thread.yield();
        return false;
    }

    /**
     * Invoke release with current state value; return saved state.
     * Cancel node and throw exception on failure.
     *
     * @param node the condition node for this wait
     * @return previous sync state
     */
    final int fullyRelease(Node node) {
        try {
            int savedState = getState();
            if (release(savedState))
                return savedState;
        } catch (RuntimeException ex) {
            node.waitStatus = Node.CANCELLED;
            throw ex;
        }
        // reach here if release fails
        node.waitStatus = Node.CANCELLED;
        throw new IllegalMonitorStateException();
    }

    // Instrumentation methods for conditions

    /**
     * Queries whether the given ConditionObject
     * uses this synchronizer as its lock.
     *
     * @param condition the condition
     * @return <tt>true</tt> if owned
     * @throws NullPointerException if condition null
     */
    public final boolean owns(ConditionObject condition) {
        if (condition == null)
            throw new NullPointerException();
        return condition.isOwnedBy(this);
    }

    /**
     * Queries whether any threads are waiting on the given condition
     * associated with this synchronizer. Note that because timeouts
     * and interrupts may occur at any time, a <tt>true</tt> return
     * does not guarantee that a future <tt>signal</tt> will awaken
     * any threads.  This method is designed primarily for use in
     * monitoring of the system state.
     *
     * @param condition the condition
     * @return <tt>true</tt> if there are any waiting threads.
     * @throws IllegalMonitorStateException if exclusive synchronization
     *                                      is not held
     * @throws IllegalArgumentException     if the given condition is
     *                                      not associated with this synchronizer
     * @throws NullPointerException         if condition null
     */
    public final boolean hasWaiters(ConditionObject condition) {
        if (!owns(condition))
            throw new IllegalArgumentException("Not owner");
        return condition.hasWaiters();
    }

    /**
     * Returns an estimate of the number of threads waiting on the
     * given condition associated with this synchronizer. Note that
     * because timeouts and interrupts may occur at any time, the
     * estimate serves only as an upper bound on the actual number of
     * waiters.  This method is designed for use in monitoring of the
     * system state, not for synchronization control.
     *
     * @param condition the condition
     * @return the estimated number of waiting threads.
     * @throws IllegalMonitorStateException if exclusive synchronization
     *                                      is not held
     * @throws IllegalArgumentException     if the given condition is
     *                                      not associated with this synchronizer
     * @throws NullPointerException         if condition null
     */
    public final int getWaitQueueLength(ConditionObject condition) {
        if (!owns(condition))
            throw new IllegalArgumentException("Not owner");
        return condition.getWaitQueueLength();
    }

    /**
     * Returns a collection containing those threads that may be
     * waiting on the given condition associated with this
     * synchronizer.  Because the actual set of threads may change
     * dynamically while constructing this result, the returned
     * collection is only a best-effort estimate. The elements of the
     * returned collection are in no particular order.
     *
     * @param condition the condition
     * @return the collection of threads
     * @throws IllegalMonitorStateException if exclusive synchronization
     *                                      is not held
     * @throws IllegalArgumentException     if the given condition is
     *                                      not associated with this synchronizer
     * @throws NullPointerException         if condition null
     */
    public final Collection<Thread> getWaitingThreads(ConditionObject condition) {
        if (!owns(condition))
            throw new IllegalArgumentException("Not owner");
        return condition.getWaitingThreads();
    }

    /**
     * Condition implementation for a {@link
     * AbstractQueuedSynchronizer} serving as the basis of a {@link
     * Lock} implementation.
     * <p/>
     * <p> Method documentation for this class describes mechanics,
     * not behavioral specifications from the point of view of Lock
     * and Condition users. Exported versions of this class will in
     * general need to be accompanied by documentation describing
     * condition semantics that rely on those of the associated
     * <tt>AbstractQueuedSynchronizer</tt>.
     * <p/>
     * <p> This class is Serializable, but all fields are transient,
     * so deserialized conditions have no waiters.
     */
    public class ConditionObject implements Condition, java.io.Serializable {
        private static final long serialVersionUID = 1173984872572414699L;
        /**
         * First node of condition queue.
         */
        private transient Node firstWaiter;
        /**
         * Last node of condition queue.
         */
        private transient Node lastWaiter;

        /**
         * Creates a new <tt>ConditionObject</tt> instance.
         */
        public ConditionObject() {
        }

        // Internal methods

        /**
         * Add a new waiter to wait queue
         *
         * @return its new wait node
         */
        private Node addConditionWaiter() {
            Node t = lastWaiter;
            // If lastWaiter is cancelled, clean out.
            if (t != null && t.waitStatus != Node.CONDITION) {
                unlinkCancelledWaiters();
                t = lastWaiter;
            }
            Node node = new Node(Thread.currentThread(), Node.CONDITION);
            if (t == null)
                firstWaiter = node;
            else
                t.nextWaiter = node;
            lastWaiter = node;
            return node;
        }

        /**
         * Remove and transfer nodes until hit non-cancelled one or
         * null. Split out from signal in part to encourage compilers
         * to inline the case of no waiters.
         *
         * @param first (non-null) the first node on condition queue
         */
        private void doSignal(Node first) {
            do {
                if ((firstWaiter = first.nextWaiter) == null)
                    lastWaiter = null;
                first.nextWaiter = null;
            } while (!transferForSignal(first) &&
                    (first = firstWaiter) != null);
        }

        /**
         * Remove and transfer all nodes.
         *
         * @param first (non-null) the first node on condition queue
         */
        private void doSignalAll(Node first) {
            lastWaiter = firstWaiter = null;
            do {
                Node next = first.nextWaiter;
                first.nextWaiter = null;
                transferForSignal(first);
                first = next;
            } while (first != null);
        }

        // public methods

        /**
         * Moves the longest-waiting thread, if one exists, from the
         * wait queue for this condition to the wait queue for the
         * owning lock.
         *
         * @throws IllegalMonitorStateException if {@link #isHeldExclusively}
         *                                      returns false
         */
        public final void signal() {
            if (!isHeldExclusively())
                throw new IllegalMonitorStateException();
            Node first = firstWaiter;
            if (first != null)
                doSignal(first);
        }

        /**
         * Moves all threads from the wait queue for this condition to
         * the wait queue for the owning lock.
         *
         * @throws IllegalMonitorStateException if {@link #isHeldExclusively}
         *                                      returns false
         */
        public final void signalAll() {
            if (!isHeldExclusively())
                throw new IllegalMonitorStateException();
            Node first = firstWaiter;
            if (first != null)
                doSignalAll(first);
        }

        /**
         * Unlinks cancelled waiter nodes from condition queue.
         * Called only while holding lock. This is called when
         * cancellation occurred during condition wait, and upon
         * insertion of a new waiter when lastWaiter is seen to have
         * been cancelled. This method is needed to avoid garbage
         * retention in the absence of signals. So even though it may
         * require a full traversal, it comes into play only when
         * timeouts or cancellations occur in the absence of
         * signals. It traverses all nodes rather than stopping at a
         * particular target to unlink all pointers to garbage nodes
         * without requiring many re-traversals during cancellation
         * storms.
         */
        private void unlinkCancelledWaiters() {
            Node t = firstWaiter;
            Node trail = null;
            while (t != null) {
                Node next = t.nextWaiter;
                if (t.waitStatus != Node.CONDITION) {
                    t.nextWaiter = null;
                    if (trail == null)
                        firstWaiter = next;
                    else
                        trail.nextWaiter = next;
                    if (next == null)
                        lastWaiter = trail;
                } else
                    trail = t;
                t = next;
            }
        }

        /**
         * Implements uninterruptible condition wait.
         * <ol>
         * <li> Save lock state returned by {@link #getState}
         * <li> Invoke {@link #release} with
         * saved state as argument, throwing
         * IllegalMonitorStateException  if it fails.
         * <li> Block until signalled
         * <li> Reacquire by invoking specialized version of
         * {@link #acquire} with saved state as argument.
         * </ol>
         */
        public final void awaitUninterruptibly() {
            Node node = addConditionWaiter();
            int savedState = fullyRelease(node);
            boolean interrupted = false;
            while (!isOnSyncQueue(node)) {
                LockSupport.park();
                if (Thread.interrupted())
                    interrupted = true;
            }
            if (acquireQueued(node, savedState) || interrupted)
                selfInterrupt();
        }

        /*
         * For interruptible waits, we need to track whether to throw
         * InterruptedException, if interrupted while blocked on
         * condition, versus reinterrupt current thread, if
         * interrupted while blocked waiting to re-acquire.
         */

        /**
         * Mode meaning to reinterrupt on exit from wait
         */
        private static final int REINTERRUPT = 1;
        /**
         * Mode meaning to throw InterruptedException on exit from wait
         */
        private static final int THROW_IE = -1;

        /**
         * Check for interrupt, returning THROW_IE if interrupted
         * before signalled, REINTERRUPT if after signalled, or
         * 0 if not interrupted.
         */
        private int checkInterruptWhileWaiting(Node node) {
            return (Thread.interrupted()) ?
                    ((transferAfterCancelledWait(node)) ? THROW_IE : REINTERRUPT) :
                    0;
        }

        /**
         * Throw InterruptedException, reinterrupt current thread, or
         * do nothing, depending on mode.
         */
        private void reportInterruptAfterWait(int interruptMode)
                throws InterruptedException {
            if (interruptMode == THROW_IE)
                throw new InterruptedException();
            else if (interruptMode == REINTERRUPT)
                Thread.currentThread().interrupt();
        }

        /**
         * Implements interruptible condition wait.
         * <ol>
         * <li> If current thread is interrupted, throw InterruptedException
         * <li> Save lock state returned by {@link #getState}
         * <li> Invoke {@link #release} with
         * saved state as argument, throwing
         * IllegalMonitorStateException  if it fails.
         * <li> Block until signalled or interrupted
         * <li> Reacquire by invoking specialized version of
         * {@link #acquire} with saved state as argument.
         * <li> If interrupted while blocked in step 4, throw exception
         * </ol>
         */
        public final void await() throws InterruptedException {
            if (Thread.interrupted())
                throw new InterruptedException();
            Node node = addConditionWaiter();
            int savedState = fullyRelease(node);
            int interruptMode = 0;
            while (!isOnSyncQueue(node)) {
                LockSupport.park();
                if ((interruptMode = checkInterruptWhileWaiting(node)) != 0)
                    break;
            }
            if (acquireQueued(node, savedState) && interruptMode != THROW_IE)
                interruptMode = REINTERRUPT;
            if (node.nextWaiter != null)
                unlinkCancelledWaiters();
            if (interruptMode != 0)
                reportInterruptAfterWait(interruptMode);
        }

        /**
         * Implements timed condition wait.
         * <ol>
         * <li> If current thread is interrupted, throw InterruptedException
         * <li> Save lock state returned by {@link #getState}
         * <li> Invoke {@link #release} with
         * saved state as argument, throwing
         * IllegalMonitorStateException  if it fails.
         * <li> Block until signalled, interrupted, or timed out
         * <li> Reacquire by invoking specialized version of
         * {@link #acquire} with saved state as argument.
         * <li> If interrupted while blocked in step 4, throw InterruptedException
         * </ol>
         */
        public final long awaitNanos(long nanosTimeout) throws InterruptedException {
            if (Thread.interrupted())
                throw new InterruptedException();
            Node node = addConditionWaiter();
            int savedState = fullyRelease(node);
            long lastTime = System.nanoTime();
            int interruptMode = 0;
            while (!isOnSyncQueue(node)) {
                if (nanosTimeout <= 0L) {
                    transferAfterCancelledWait(node);
                    break;
                }
                LockSupport.parkNanos(nanosTimeout);
                if ((interruptMode = checkInterruptWhileWaiting(node)) != 0)
                    break;
                long now = System.nanoTime();
                nanosTimeout -= now - lastTime;
                lastTime = now;
            }
            if (acquireQueued(node, savedState) && interruptMode != THROW_IE)
                interruptMode = REINTERRUPT;
            if (node.nextWaiter != null)
                unlinkCancelledWaiters();
            if (interruptMode != 0)
                reportInterruptAfterWait(interruptMode);
            return nanosTimeout - (System.nanoTime() - lastTime);
        }

        /**
         * Implements absolute timed condition wait.
         * <ol>
         * <li> If current thread is interrupted, throw InterruptedException
         * <li> Save lock state returned by {@link #getState}
         * <li> Invoke {@link #release} with
         * saved state as argument, throwing
         * IllegalMonitorStateException  if it fails.
         * <li> Block until signalled, interrupted, or timed out
         * <li> Reacquire by invoking specialized version of
         * {@link #acquire} with saved state as argument.
         * <li> If interrupted while blocked in step 4, throw InterruptedException
         * <li> If timed out while blocked in step 4, return false, else true
         * </ol>
         */
        public final boolean awaitUntil(Date deadline) throws InterruptedException {
            if (deadline == null)
                throw new NullPointerException();
            long abstime = deadline.getTime();
            if (Thread.interrupted())
                throw new InterruptedException();
            Node node = addConditionWaiter();
            int savedState = fullyRelease(node);
            boolean timedout = false;
            int interruptMode = 0;
            while (!isOnSyncQueue(node)) {
                if (System.currentTimeMillis() > abstime) {
                    timedout = transferAfterCancelledWait(node);
                    break;
                }
                LockSupport.parkUntil(abstime);
                if ((interruptMode = checkInterruptWhileWaiting(node)) != 0)
                    break;
            }
            if (acquireQueued(node, savedState) && interruptMode != THROW_IE)
                interruptMode = REINTERRUPT;
            if (node.nextWaiter != null)
                unlinkCancelledWaiters();
            if (interruptMode != 0)
                reportInterruptAfterWait(interruptMode);
            return !timedout;
        }

        /**
         * Implements timed condition wait.
         * <ol>
         * <li> If current thread is interrupted, throw InterruptedException
         * <li> Save lock state returned by {@link #getState}
         * <li> Invoke {@link #release} with
         * saved state as argument, throwing
         * IllegalMonitorStateException  if it fails.
         * <li> Block until signalled, interrupted, or timed out
         * <li> Reacquire by invoking specialized version of
         * {@link #acquire} with saved state as argument.
         * <li> If interrupted while blocked in step 4, throw InterruptedException
         * <li> If timed out while blocked in step 4, return false, else true
         * </ol>
         */
        public final boolean await(long time, TimeUnit unit) throws InterruptedException {
            if (unit == null)
                throw new NullPointerException();
            long nanosTimeout = unit.toNanos(time);
            if (Thread.interrupted())
                throw new InterruptedException();
            Node node = addConditionWaiter();
            int savedState = fullyRelease(node);
            long lastTime = System.nanoTime();
            boolean timedout = false;
            int interruptMode = 0;
            while (!isOnSyncQueue(node)) {
                if (nanosTimeout <= 0L) {
                    timedout = transferAfterCancelledWait(node);
                    break;
                }
                LockSupport.parkNanos(nanosTimeout);
                if ((interruptMode = checkInterruptWhileWaiting(node)) != 0)
                    break;
                long now = System.nanoTime();
                nanosTimeout -= now - lastTime;
                lastTime = now;
            }
            if (acquireQueued(node, savedState) && interruptMode != THROW_IE)
                interruptMode = REINTERRUPT;
            if (node.nextWaiter != null)
                unlinkCancelledWaiters();
            if (interruptMode != 0)
                reportInterruptAfterWait(interruptMode);
            return !timedout;
        }

        //  support for instrumentation

        /**
         * Returns true if this condition was created by the given
         * synchronization object
         *
         * @return true if owned
         */
        final boolean isOwnedBy(AbstractQueuedSynchronizer sync) {
            return sync == AbstractQueuedSynchronizer.this;
        }

        /**
         * Queries whether any threads are waiting on this condition.
         * Implements {@link AbstractQueuedSynchronizer#hasWaiters}
         *
         * @return <tt>true</tt> if there are any waiting threads.
         * @throws IllegalMonitorStateException if {@link #isHeldExclusively}
         *                                      returns false
         */
        protected final boolean hasWaiters() {
            if (!isHeldExclusively())
                throw new IllegalMonitorStateException();
            for (Node w = firstWaiter; w != null; w = w.nextWaiter) {
                if (w.waitStatus == Node.CONDITION)
                    return true;
            }
            return false;
        }

        /**
         * Returns an estimate of the number of threads waiting on
         * this condition.
         * Implements {@link AbstractQueuedSynchronizer#getWaitQueueLength}
         *
         * @return the estimated number of waiting threads.
         * @throws IllegalMonitorStateException if {@link #isHeldExclusively}
         *                                      returns false
         */
        protected final int getWaitQueueLength() {
            if (!isHeldExclusively())
                throw new IllegalMonitorStateException();
            int n = 0;
            for (Node w = firstWaiter; w != null; w = w.nextWaiter) {
                if (w.waitStatus == Node.CONDITION)
                    ++n;
            }
            return n;
        }

        /**
         * Returns a collection containing those threads that may be
         * waiting on this Condition.
         * Implements {@link AbstractQueuedSynchronizer#getWaitingThreads}
         *
         * @return the collection of threads
         * @throws IllegalMonitorStateException if {@link #isHeldExclusively}
         *                                      returns false
         */
        protected final Collection<Thread> getWaitingThreads() {
            if (!isHeldExclusively())
                throw new IllegalMonitorStateException();
            ArrayList<Thread> list = new ArrayList<Thread>();
            for (Node w = firstWaiter; w != null; w = w.nextWaiter) {
                if (w.waitStatus == Node.CONDITION) {
                    Thread t = w.thread;
                    if (t != null)
                        list.add(t);
                }
            }
            return list;
        }
    }

    /**
     * Setup to support compareAndSet. We need to natively implement
     * this here: For the sake of permitting future enhancements, we
     * cannot explicitly subclass AtomicInteger, which would be
     * efficient and useful otherwise. So, as the lesser of evils, we
     * natively implement using hotspot intrinsics API. And while we
     * are at it, we do the same for other CASable fields (which could
     * otherwise be done with atomic field updaters).
     */
    private static final Unsafe unsafe = Unsafe.getUnsafe();
    private static final long stateOffset;
    private static final long headOffset;
    private static final long tailOffset;
    private static final long waitStatusOffset;
    private static final long nextOffset;

    static {
        try {
            stateOffset = unsafe.objectFieldOffset
                    (AbstractQueuedSynchronizer.class.getDeclaredField("state"));
            headOffset = unsafe.objectFieldOffset
                    (AbstractQueuedSynchronizer.class.getDeclaredField("head"));
            tailOffset = unsafe.objectFieldOffset
                    (AbstractQueuedSynchronizer.class.getDeclaredField("tail"));
            waitStatusOffset = unsafe.objectFieldOffset
                    (Node.class.getDeclaredField("waitStatus"));
            nextOffset = unsafe.objectFieldOffset
                    (Node.class.getDeclaredField("next"));

        } catch (Exception ex) {
            throw new Error(ex);
        }
    }

    /**
     * CAS head field. Used only by enq
     */
    private final boolean compareAndSetHead(Node update) {
        return unsafe.compareAndSwapObject(this, headOffset, null, update);
    }

    /**
     * CAS tail field. Used only by enq
     */
    private final boolean compareAndSetTail(Node expect, Node update) {
        return unsafe.compareAndSwapObject(this, tailOffset, expect, update);
    }

    /**
     * CAS waitStatus field of a node.
     */
    private final static boolean compareAndSetWaitStatus(Node node,
                                                         int expect,
                                                         int update) {
        return unsafe.compareAndSwapInt(node, waitStatusOffset,
                expect, update);
    }

    /**
     * CAS next field of a node.
     */
    private final static boolean compareAndSetNext(Node node,
                                                   Node expect,
                                                   Node update) {
        return unsafe.compareAndSwapObject(node, nextOffset, expect, update);
    }

}
