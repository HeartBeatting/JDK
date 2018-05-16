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
import java.util.concurrent.locks.LockSupport;

/**
 * A cancellable asynchronous computation.  This class provides a base  // 可取消的异步计算
 * implementation of {@link Future}, with methods to start and cancel   // 这个类提供了Future接口的基础实现, 有启动和取消的方法
 * a computation, query to see if the computation is complete, and      // 可以用来查询任务是否完成
 * retrieve the result of the computation.  The result can only be      // 可以用来检索任务的计算结果
 * retrieved when the computation has completed; the {@code get}        // 只有任务完成了,结果才能被检索到
 * methods will block if the computation has not yet completed.  Once   // get方法会一直阻塞,直到任务完成了
 * the computation has completed, the computation cannot be restarted   // 一旦任务完成了,不可重新启动或者取消
 * or cancelled (unless the computation is invoked using                // 除非调用了runAndReset方法
 * {@link #runAndReset}).
 *
 * <p>A {@code FutureTask} can be used to wrap a {@link Callable} or    // FutureTask可以用来包装一个Callable或Runnable实例
 * {@link Runnable} object.  Because {@code FutureTask} implements      // 因为FutureTask实现了Runnable接口
 * {@code Runnable}, a {@code FutureTask} can be submitted to an        // 所以可以被提交给一个Executor
 * {@link Executor} for execution.
 *
 * <p>In addition to serving as a standalone class, this class provides // 为了作为一个独立的类.
 * {@code protected} functionality that may be useful when creating     // 这个类提供了protected的方法,可以用于定制化任务类.
 * customized task classes.
 *
 * @since 1.5
 * @author Doug Lea
 * @param <V> The result type returned by this FutureTask's {@code get} methods
 */
public class FutureTask<V> implements RunnableFuture<V> {
    /*
     * Revision notes: This differs from previous versions of this      // 老的版本是依赖AQS的
     * class that relied on AbstractQueuedSynchronizer, mainly to
     * avoid surprising users about retaining interrupt status during
     * cancellation races. Sync control in the current design relies
     * on a "state" field updated via CAS to track completion, along    // 改进后的版本只依赖state表示任务是否完成
     * with a simple Treiber stack to hold waiting threads.             // Treiber stack, 一种栈结构吧
     *
     * Style note: As usual, we bypass overhead of using
     * AtomicXFieldUpdaters and instead directly use Unsafe intrinsics.
     */

    /**
     * The run state of this task, initially NEW.  The run state
     * transitions to a terminal state only in methods set,
     * setException, and cancel.  During completion, state may take on
     * transient values of COMPLETING (while outcome is being set) or
     * INTERRUPTING (only while interrupting the runner to satisfy a
     * cancel(true)). Transitions from these intermediate to final
     * states use cheaper ordered/lazy writes because values are unique
     * and cannot be further modified.
     *
     * Possible state transitions:
     * NEW -> COMPLETING -> NORMAL
     * NEW -> COMPLETING -> EXCEPTIONAL
     * NEW -> CANCELLED
     * NEW -> INTERRUPTING -> INTERRUPTED
     */
    private volatile int state;
    private static final int NEW          = 0;
    private static final int COMPLETING   = 1;
    private static final int NORMAL       = 2;
    private static final int EXCEPTIONAL  = 3;
    private static final int CANCELLED    = 4;
    private static final int INTERRUPTING = 5;
    private static final int INTERRUPTED  = 6;

    /** The underlying callable; nulled out after running */
    private Callable<V> callable;
    /** The result to return or exception to throw from get() */
    private Object outcome; // non-volatile, protected by state reads/writes
    /** The thread running the callable; CASed during run() */
    private volatile Thread runner;
    /** Treiber stack of waiting threads */
    private volatile WaitNode waiters;

    /**
     * Returns result or throws exception for completed task.
     *
     * @param s completed state value
     */
    @SuppressWarnings("unchecked")
    private V report(int s) throws ExecutionException {
        Object x = outcome;
        if (s == NORMAL)                        // 状态正常,直接返回结果
            return (V)x;
        if (s >= CANCELLED)                     // 任务被取消了,直接抛出异常
            throw new CancellationException();
        throw new ExecutionException((Throwable)x);
    }

    /**
     * Creates a {@code FutureTask} that will, upon running, execute the
     * given {@code Callable}.
     *
     * @param  callable the callable task
     * @throws NullPointerException if the callable is null
     */
    public FutureTask(Callable<V> callable) {                               // 传入一个Callable
        if (callable == null)
            throw new NullPointerException();
        this.callable = callable;                                           // 构造函数会初始化callable对象,任务完成后会设置设个对象的
        this.state = NEW;       // ensure visibility of callable
    }

    /**
     * Creates a {@code FutureTask} that will, upon running, execute the    // 创建一个FutureTask
     * given {@code Runnable}, and arrange that {@code get} will return the // 执行Runnable任务
     * given result on successful completion.                               // 任务成功完成, 会返回结果result
     *
     * @param runnable the runnable task
     * @param result the result to return on successful completion. If      // 返回完成后的结果result
     * you don't need a particular result, consider using                   // 如果你不需要结果对象,可以考虑使用 Future<?> f = new FutureTask<Void>(runnable, null)
     * constructions of the form:
     * {@code Future<?> f = new FutureTask<Void>(runnable, null)}
     * @throws NullPointerException if the runnable is null
     */
    public FutureTask(Runnable runnable, V result) {                        // 可以看到FutureTask只有两个构造方法,
        this.callable = Executors.callable(runnable, result);               // 两个构造方法都会构建一个callable
        this.state = NEW;       // ensure visibility of callable
    }

    public boolean isCancelled() {      // FutureTask比较方便的功能,就是它可以跟踪任务的状态,并提供了接口判断状态
        return state >= CANCELLED;      // 这里是判断任务是否被取消了
    }

    public boolean isDone() {           // 判断任务是否完成了
        return state != NEW;
    }

    public boolean cancel(boolean mayInterruptIfRunning) {  // 取消任务
        if (state != NEW)
            return false;
        if (mayInterruptIfRunning) {                                                    // 是否需要中断任务
            if (!UNSAFE.compareAndSwapInt(this, stateOffset, NEW, INTERRUPTING))    // 设置为中断中
                return false;
            Thread t = runner;
            if (t != null)
                t.interrupt();                                                          // 中断工作线程
            UNSAFE.putOrderedInt(this, stateOffset, INTERRUPTED); // final state    // 修改状态为被中断
        }
        else if (!UNSAFE.compareAndSwapInt(this, stateOffset, NEW, CANCELLED))      // 不需要中断,则直接修改任务状态
            return false;                                                               // 任务不是初始状态,则直接返回false
        finishCompletion();                                                             // 任务状态变化,通知所有等待的线程,任务状态变更都需要调用
        return true;
    }

    /**
     * @throws CancellationException {@inheritDoc}
     */
    public V get() throws InterruptedException, ExecutionException {    // 阻塞等待结果,但是会响应中断
        int s = state;
        if (s <= COMPLETING)
            s = awaitDone(false, 0L);
        return report(s);
    }

    /**
     * @throws CancellationException {@inheritDoc}
     */
    public V get(long timeout, TimeUnit unit)                           // 可超时等待结果
        throws InterruptedException, ExecutionException, TimeoutException {
        if (unit == null)
            throw new NullPointerException();
        int s = state;
        if (s <= COMPLETING &&
            (s = awaitDone(true, unit.toNanos(timeout))) <= COMPLETING)
            throw new TimeoutException();
        return report(s);
    }

    /**
     * Protected method invoked when this task transitions to state     // protected方法,用于任务转改转变为完成状态时,被调用
     * {@code isDone} (whether normally or via cancellation). The       // 无论是正常结束 或 被取消
     * default implementation does nothing.  Subclasses may override
     * this method to invoke completion callbacks or perform
     * bookkeeping. Note that you can query status inside the           // 注意: 你可以在此方法实现中查询任务状态,来判断任务是否被取消了.
     * implementation of this method to determine whether this task
     * has been cancelled.
     */
    protected void done() { }   // ExecutorCompletionService 就是覆盖了这个方法,用来跟踪任务执行状态的

    /**
     * Sets the result of this future to the given value unless         // 设置Future的结果result
     * this future has already been set or has been cancelled.          // 除非future已经被设置了或者被取消了
     *
     * <p>This method is invoked internally by the {@link #run} method  // 这个方法是用于在run方法内部,任务完成后调用的
     * upon successful completion of the computation.
     *
     * @param v the value
     */
    protected void set(V v) {
        if (UNSAFE.compareAndSwapInt(this, stateOffset, NEW, COMPLETING)) {
            outcome = v;
            UNSAFE.putOrderedInt(this, stateOffset, NORMAL); // final state
            // Oracle的JDK中提供了Unsafe.putOrderedObject，Unsafe.putOrderedInt，Unsafe.putOrderedLong这三个方法，
            // JDK会在执行UNSAFE这三个方法时插入 StoreStore 内存屏障，避免发生写操作重排序.
            finishCompletion();
        }
    }

    /**
     * Causes this future to report an {@link ExecutionException}
     * with the given throwable as its cause, unless this future has
     * already been set or has been cancelled.
     *
     * <p>This method is invoked internally by the {@link #run} method
     * upon failure of the computation.
     *
     * @param t the cause of failure
     */
    protected void setException(Throwable t) {
        if (UNSAFE.compareAndSwapInt(this, stateOffset, NEW, COMPLETING)) {
            outcome = t;
            UNSAFE.putOrderedInt(this, stateOffset, EXCEPTIONAL); // final state
            finishCompletion();
        }
    }

    public void run() {                         // 这里是FutureTask工作的核心
        if (state != NEW ||                     // 校验状态,校验通过后修改任务的runner为当前线程
            !UNSAFE.compareAndSwapObject(this, runnerOffset,
                                         null, Thread.currentThread()))
            return;
        try {
            Callable<V> c = callable;
            if (c != null && state == NEW) {
                V result;
                boolean ran;
                try {
                    result = c.call();          // c调用call方法, call方法一般都是开发自定义的匿名内部类, 这里就是调用自定义的线程运行逻辑, 并返回result结果.
                    ran = true;                 // run用于表示执行完成了.这种方式还是蛮好的,就是没有异常就表示执行成功了.
                } catch (Throwable ex) {
                    result = null;              // 将结果赋值null
                    ran = false;                // 抛出异常就标记为执行失败了
                    setException(ex);           // 设置抛出的异常
                }
                if (ran)
                    set(result);                // 设置返回的结果,这时候外部服务阻塞调用get方法,就会收到通知的. finishCompletion方法里会通知等待的线程的.
            }
        } finally {
            // runner must be non-null until state is settled to
            // prevent concurrent calls to run()
            runner = null;                      // 这里其实也是利用volatile的happen-before原则
            // state must be re-read after nulling runner to prevent
            // leaked interrupts
            int s = state;                      // 这里重新读取volatile变量,防止漏掉的
            if (s >= INTERRUPTING)
                handlePossibleCancellationInterrupt(s);
        }
    }

    /**
     * Executes the computation without setting its result, and then    // 执行任务,不需要设置任务结果
     * resets this future to initial state, failing to do so if the     // 然后重置future为初始状态
     * computation encounters an exception or is cancelled.  This is    // 如果任务抛出异常了或者被取消了,直接失败
     * designed for use with tasks that intrinsically execute more      // 这个被设计用来 内部不止一次的执行
     * than once.
     *
     * @return true if successfully run and reset
     */
    protected boolean runAndReset() {                                   // 这是一个protected方法,不允许外部类调用
        if (state != NEW ||
            !UNSAFE.compareAndSwapObject(this, runnerOffset,
                                         null, Thread.currentThread()))
            return false;
        boolean ran = false;
        int s = state;
        try {
            Callable<V> c = callable;
            if (c != null && s == NEW) {
                try {
                    c.call(); // don't set result                       // 这里只管执行任务,没有设置结果
                    ran = true;
                } catch (Throwable ex) {
                    setException(ex);
                }
            }
        } finally {
            // runner must be non-null until state is settled to
            // prevent concurrent calls to run()
            runner = null;
            // state must be re-read after nulling runner to prevent
            // leaked interrupts
            s = state;
            if (s >= INTERRUPTING)
                handlePossibleCancellationInterrupt(s);
        }
        return ran && s == NEW;
    }

    /**
     * Ensures that any interrupt from a possible cancel(true) is only  // 这里保证cancel取消导致的中断操作
     * delivered to a task while in run or runAndReset.                 // 只会被传递给一个执行中的或者runAndReset的任务
     */
    private void handlePossibleCancellationInterrupt(int s) {
        // It is possible for our interrupter to stall before getting a // 我们的中断操作可能会慢一点
        // chance to interrupt us.  Let's spin-wait patiently.          // 这里是耐心的循环等待
        if (s == INTERRUPTING)
            while (state == INTERRUPTING)
                Thread.yield(); // wait out pending interrupt

        // assert state == INTERRUPTED;

        // We want to clear any interrupt we may have received from
        // cancel(true).  However, it is permissible to use interrupts
        // as an independent mechanism for a task to communicate with
        // its caller, and there is no way to clear only the
        // cancellation interrupt.
        //
        // Thread.interrupted();
    }

    /**
     * Simple linked list nodes to record waiting threads in a Treiber  // 这是一个stack
     * stack.  See other classes such as Phaser and SynchronousQueue    // Phaser 和 SynchronousQueue有详细描述
     * for more detailed explanation.
     */
    static final class WaitNode {
        volatile Thread thread;
        volatile WaitNode next;
        WaitNode() { thread = Thread.currentThread(); }
    }

    /**
     * Removes and signals all waiting threads, invokes done(), and     // 删除并且通知所有等待的线程
     * nulls out callable.                                              // 调用done()方法,并清空callable
     */
    private void finishCompletion() {           // 这个方法一般在任务状态变化后调用,用来通知阻塞获取任务状态的线程.
        // assert state > COMPLETING;
        for (WaitNode q; (q = waiters) != null;) {  // 遍历等待当前任务的节点, 一直找到null位置, 依次唤醒所有的等待线程.
            if (UNSAFE.compareAndSwapObject(this, waitersOffset, q, null)) {
                for (;;) {
                    Thread t = q.thread;
                    if (t != null) {
                        q.thread = null;        // 置空等待节点上的线程
                        LockSupport.unpark(t);  // 唤醒等待的线程. FutureTask里面的阻塞和唤醒动作都靠LockSupport进行的.
                    }
                    WaitNode next = q.next;     // 继续遍历下一个等待节点.
                    if (next == null)           // 下一个为空,结束循环
                        break;
                    q.next = null; // unlink to help gc // 帮助GC
                    q = next;
                }
                break;
            }
        }

        done();                 // 这个是用来给子类扩展的方法

        callable = null;        // to reduce footprint  // 减少足迹
    }

    /**
     * Awaits completion or aborts on interrupt or timeout.     // 超时等待完成,但是会响应中断
     *
     * @param timed true if use timed waits
     * @param nanos time to wait, if timed
     * @return state upon completion
     */
    private int awaitDone(boolean timed, long nanos)
        throws InterruptedException {
        final long deadline = timed ? System.nanoTime() + nanos : 0L;
        WaitNode q = null;
        boolean queued = false;
        for (;;) {
            if (Thread.interrupted()) {
                removeWaiter(q);
                throw new InterruptedException();
            }

            int s = state;
            if (s > COMPLETING) {
                if (q != null)
                    q.thread = null;
                return s;
            }
            else if (s == COMPLETING) // cannot time out yet
                Thread.yield();     // 任务没有完成,当前线程循环yield.
            else if (q == null)
                q = new WaitNode();
            else if (!queued)
                queued = UNSAFE.compareAndSwapObject(this, waitersOffset,
                                                     q.next = waiters, q);
            else if (timed) {
                nanos = deadline - System.nanoTime();
                if (nanos <= 0L) {
                    removeWaiter(q);
                    return state;
                }
                LockSupport.parkNanos(this, nanos);
            }
            else
                LockSupport.park(this);
        }
    }

    /**
     * Tries to unlink a timed-out or interrupted wait node to avoid        // 尝试删除一个超时了的或者被中断了的等待节点
     * accumulating garbage.  Internal nodes are simply unspliced           // 避免累加的GC, 内部的节点简单的取消拼接
     * without CAS since it is harmless if they are traversed anyway        // 没有使用CAS,因为他们是无害的
     * by releasers.  To avoid effects of unsplicing from already           // 为了避免被已经删除的节点影响
     * removed nodes, the list is retraversed in case of an apparent        // list被重新传过,避免明显的竞争
     * race.  This is slow when there are a lot of nodes, but we don't      // 如果有很多node,这会比较慢
     * expect lists to be long enough to outweigh higher-overhead           // 但是我们不希望list长度会超过高开销方案
     * schemes.
     */
    private void removeWaiter(WaitNode node) {
        if (node != null) {
            node.thread = null;
            retry:
            for (;;) {          // restart on removeWaiter race
                for (WaitNode pred = null, q = waiters, s; q != null; q = s) {
                    s = q.next;
                    if (q.thread != null)
                        pred = q;
                    else if (pred != null) {
                        pred.next = s;
                        if (pred.thread == null) // check for race
                            continue retry;
                    }
                    else if (!UNSAFE.compareAndSwapObject(this, waitersOffset,
                                                          q, s))
                        continue retry;
                }
                break;
            }
        }
    }

    // Unsafe mechanics     // 下面都是用UNSAFE类获取对象属性域的偏移量, 上面调用cas方法时,有些是根据偏移量地址修改值的.
    private static final sun.misc.Unsafe UNSAFE;
    private static final long stateOffset;
    private static final long runnerOffset;
    private static final long waitersOffset;
    static {
        try {
            UNSAFE = sun.misc.Unsafe.getUnsafe();
            Class<?> k = FutureTask.class;
            stateOffset = UNSAFE.objectFieldOffset
                (k.getDeclaredField("state"));
            runnerOffset = UNSAFE.objectFieldOffset
                (k.getDeclaredField("runner"));
            waitersOffset = UNSAFE.objectFieldOffset
                (k.getDeclaredField("waiters"));
        } catch (Exception e) {
            throw new Error(e);
        }
    }

}
