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
import java.util.concurrent.locks.*;

/**
 * A synchronization aid that allows a set of threads to all wait for
 * each other to reach a common barrier point.  CyclicBarriers are
 * useful in programs involving a fixed sized party of threads that
 * must occasionally wait for each other. The barrier is called
 * <em>cyclic</em> because it can be re-used after the waiting threads
 * are released.
 *
 * <p>A <tt>CyclicBarrier</tt> supports an optional {@link Runnable} command
 * that is run once per barrier point, after the last thread in the party
 * arrives, but before any threads are released.
 * This <em>barrier action</em> is useful
 * for updating shared-state before any of the parties continue.
 *
 * <p><b>Sample usage:</b> Here is an example of
 *  using a barrier in a parallel decomposition design:
 * <pre>
 * class Solver {
 *   final int N;
 *   final float[][] data;
 *   final CyclicBarrier barrier;
 *
 *   class Worker implements Runnable {
 *     int myRow;
 *     Worker(int row) { myRow = row; }
 *     public void run() {
 *       while (!done()) {
 *         processRow(myRow);
 *
 *         try {
 *           barrier.await();
 *         } catch (InterruptedException ex) {
 *           return;
 *         } catch (BrokenBarrierException ex) {
 *           return;
 *         }
 *       }
 *     }
 *   }
 *
 *   public Solver(float[][] matrix) {
 *     data = matrix;
 *     N = matrix.length;
 *     barrier = new CyclicBarrier(N,
 *                                 new Runnable() {
 *                                   public void run() {
 *                                     mergeRows(...);
 *                                   }
 *                                 });
 *     for (int i = 0; i < N; ++i)
 *       new Thread(new Worker(i)).start();
 *
 *     waitUntilDone();
 *   }
 * }
 * </pre>
 * Here, each worker thread processes a row of the matrix then waits at the
 * barrier until all rows have been processed. When all rows are processed
 * the supplied {@link Runnable} barrier action is executed and merges the
 * rows. If the merger
 * determines that a solution has been found then <tt>done()</tt> will return
 * <tt>true</tt> and each worker will terminate.
 *
 * <p>If the barrier action does not rely on the parties being suspended when
 * it is executed, then any of the threads in the party could execute that
 * action when it is released. To facilitate this, each invocation of
 * {@link #await} returns the arrival index of that thread at the barrier.
 * You can then choose which thread should execute the barrier action, for
 * example:
 * <pre>  if (barrier.await() == 0) {
 *     // log the completion of this iteration
 *   }</pre>
 *
 * <p>The <tt>CyclicBarrier</tt> uses an all-or-none breakage model
 * for failed synchronization attempts: If a thread leaves a barrier
 * point prematurely because of interruption, failure, or timeout, all
 * other threads waiting at that barrier point will also leave
 * abnormally via {@link BrokenBarrierException} (or
 * {@link InterruptedException} if they too were interrupted at about
 * the same time).
 *
 * <p>Memory consistency effects: Actions in a thread prior to calling
 * {@code await()}
 * <a href="package-summary.html#MemoryVisibility"><i>happen-before</i></a>
 * actions that are part of the barrier action, which in turn
 * <i>happen-before</i> actions following a successful return from the
 * corresponding {@code await()} in other threads.
 *
 * @since 1.5
 * @see CountDownLatch
 *
 * @author Doug Lea
 */
public class CyclicBarrier {
    /**
     * Each use of the barrier is represented as a generation instance.     // 每个barrier都代表一个Generation实例
     * The generation changes whenever the barrier is tripped, or           // 当barrier(同步屏障)打开后,generation就变化了
     * is reset. There can be many generations associated with threads      // 可以有很多generations和同一个barrier关联
     * using the barrier - due to the non-deterministic way the lock        // 因为不确定性的方式,锁被用来等待线程
     * may be allocated to waiting threads - but only one of these          // 但是等待的线程中,只有一个能获取锁,
     * can be active at a time (the one to which <tt>count</tt> applies)
     * and all the rest are either broken or tripped.                       // 其他的被打断了或者已经通过屏障了
     * There need not be an active generation if there has been a break     // 不需要有active(激活的)generation,如果已经有中断了但还没有随后的重置.
     * but no subsequent reset.
     */
    private static class Generation {   // generation其实就一个作用, broken用于标记是否被打断了
        boolean broken = false;         // 只有两个地方用到Generation, 1.全部线程都通过了,重置Generation; 2.有一个线程被中断了,调用breakBarrier,broken被设置为true.
    }

    /** The lock for guarding barrier entry */
    private final ReentrantLock lock = new ReentrantLock(); //用于保证屏障的锁
    /** Condition to wait on until tripped */
    private final Condition trip = lock.newCondition();     //锁对应的condition
    /** The number of parties */
    private final int parties;                              //parties的数量
    /* The command to run when tripped */
    private final Runnable barrierCommand;                  //越过障碍后,需要执行的命令
    /** The current generation */
    private Generation generation = new Generation();       //当前的generation

    /**
     * Number of parties still waiting. Counts down from parties to 0   //仍然在等待的线程个数
     * on each generation.  It is reset to parties on each new          //每个generation从parties递减到0
     * generation or when broken.                                       //每次新建一个generation或者broken,count都会被重置
     */
    private int count;

    /**
     * Updates state on barrier trip and wakes up everyone.     //更新barrier的状态,唤醒所有的线程
     * Called only while holding lock.                          //必须在获取锁时调用
     */
    private void nextGeneration() {
        // signal completion of last generation     //通知上一个generation对应的所有等待线程
        trip.signalAll();
        // set up next generation                   //重置count
        count = parties;
        generation = new Generation();              //新创建一个Generation
    }

    /**
     * Sets current barrier generation as broken and wakes up everyone.     //设置当前generation的屏障broken,唤醒所有的线程
     * Called only while holding lock.
     */
    private void breakBarrier() {
        generation.broken = true;   //这里设置为true,下面再通知所有线程,所有线程都能往下走,所有等待的线程都会抛出BrokenBarrierException
        count = parties;
        trip.signalAll();
    }

    /**
     * Main barrier code, covering the various policies.
     */
    private int dowait(boolean timed, long nanos)
        throws InterruptedException, BrokenBarrierException,    //barrier的主要功能
               TimeoutException {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            final Generation g = generation;

            if (g.broken)
                throw new BrokenBarrierException();     //发现broken了,所有等待的线程都会抛出这个异常

            if (Thread.interrupted()) {                 //如果走到这里说明没有broken,但是这里会检测并响应中断
                breakBarrier();                         //这里是只要一个线程被中断了,就让所有的线程都抛出异常.
                throw new InterruptedException();
            }

           int index = --count;                         //走到这里说明线程通过屏障了, 将count-1, 这里已经加锁了,所以是线程安全的, 这里为什么加锁?因为
           if (index == 0) {  // tripped                //如果index==0,表示所有的都通过了; 如果不是最后一个到达的线程,会直接到下面的循环等待中
               boolean ranAction = false;               //所有的都通过了,调用指定的barrierCommand
               try {
                   final Runnable command = barrierCommand;
                   if (command != null)
                       command.run();                   //这里是直接同步调用command,并没有重新起线程
                   ranAction = true;
                   nextGeneration();                    //创建新的Generation,可以循环使用
                   return 0;
               } finally {
                   if (!ranAction)                      //这里其实是担心上面的command.run方法抛出异常了.
                       breakBarrier();                  //在这里判断下,如果ranAction仍然为false就强制中断barrier.
               }
           }

            // loop until tripped, broken, interrupted, or timed out            //这里就是循环,等待signal或者超时,或者被中断了
            for (;;) {
                try {
                    if (!timed)
                        trip.await();                   //这里调用await方法,就释放了可重入锁了,下一个线程也可以获取锁,调用这个方法了.
                    else if (nanos > 0L)
                        nanos = trip.awaitNanos(nanos);
                } catch (InterruptedException ie) {
                    if (g == generation && ! g.broken) {
                        breakBarrier();
                        throw ie;
                    } else {
                        // We're about to finish waiting even if we had not
                        // been interrupted, so this interrupt is deemed to
                        // "belong" to subsequent execution.
                        Thread.currentThread().interrupt();
                    }
                }

                if (g.broken)
                    throw new BrokenBarrierException();

                if (g != generation)
                    return index;

                if (timed && nanos <= 0L) {
                    breakBarrier();
                    throw new TimeoutException();
                }
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Creates a new <tt>CyclicBarrier</tt> that will trip when the
     * given number of parties (threads) are waiting upon it, and which
     * will execute the given barrier action when the barrier is tripped,
     * performed by the last thread entering the barrier.
     *
     * @param parties the number of threads that must invoke {@link #await}
     *        before the barrier is tripped
     * @param barrierAction the command to execute when the barrier is
     *        tripped, or {@code null} if there is no action
     * @throws IllegalArgumentException if {@code parties} is less than 1
     */
    public CyclicBarrier(int parties, Runnable barrierAction) {     // barrierAction是指定通过屏障后要做的任务
        if (parties <= 0) throw new IllegalArgumentException();
        this.parties = parties;
        this.count = parties;
        this.barrierCommand = barrierAction;
    }

    /**
     * Creates a new <tt>CyclicBarrier</tt> that will trip when the
     * given number of parties (threads) are waiting upon it, and
     * does not perform a predefined action when the barrier is tripped.
     *
     * @param parties the number of threads that must invoke {@link #await}
     *        before the barrier is tripped
     * @throws IllegalArgumentException if {@code parties} is less than 1
     */
    public CyclicBarrier(int parties) {
        this(parties, null);    // 这里没有指定barrierAction
    }

    /**
     * Returns the number of parties required to trip this barrier.
     *
     * @return the number of parties required to trip this barrier
     */
    public int getParties() {
        return parties;
    }

    /**
     * Waits until all {@linkplain #getParties parties} have invoked        //等待,直到所有的线程都已经调用await方法
     * <tt>await</tt> on this barrier.
     *
     * <p>If the current thread is not the last to arrive then it is        //如果当前线程不是最后一个到达的
     * disabled for thread scheduling purposes and lies dormant until       //线程调用会一直等待下面几种情况之一发生
     * one of the following things happens:
     * <ul>
     * <li>The last thread arrives; or                                      //1. 最后一个线程到达了
     * <li>Some other thread {@linkplain Thread#interrupt interrupts}       //2. 其他线程中断了当前线程
     * the current thread; or
     * <li>Some other thread {@linkplain Thread#interrupt interrupts}       //3. 某个线程中断了其他某个等待中的线程
     * one of the other waiting threads; or
     * <li>Some other thread times out while waiting for barrier; or        //4. 其他线程等待超时了
     * <li>Some other thread invokes {@link #reset} on this barrier.        //5. 其他线程调用了reset方法
     * </ul>
     *
     * <p>If the current thread:
     * <ul>
     * <li>has its interrupted status set on entry to this method; or
     * <li>is {@linkplain Thread#interrupt interrupted} while waiting
     * </ul>
     * then {@link InterruptedException} is thrown and the current thread's
     * interrupted status is cleared.
     *
     * <p>If the barrier is {@link #reset} while any thread is waiting,
     * or if the barrier {@linkplain #isBroken is broken} when
     * <tt>await</tt> is invoked, or while any thread is waiting, then
     * {@link BrokenBarrierException} is thrown.
     *
     * <p>If any thread is {@linkplain Thread#interrupt interrupted} while waiting,
     * then all other waiting threads will throw
     * {@link BrokenBarrierException} and the barrier is placed in the broken
     * state.
     *
     * <p>If the current thread is the last thread to arrive, and a             //如果当前线程是最后一个到达的线程
     * non-null barrier action was supplied in the constructor, then the        //并且指定了barrier action
     * current thread runs the action before allowing the other threads to      //当前线程在允许其他线程继续前,会先调用barrier action
     * continue.
     * If an exception occurs during the barrier action then that exception
     * will be propagated in the current thread and the barrier is placed in    //如果barrier action异常了,异常会传播
     * the broken state.
     *
     * @return the arrival index of the current thread, where index             //返回这是第几个到达的线程
     *         <tt>{@link #getParties()} - 1</tt> indicates the first           //等于getParties() - 1,表示第一个到达的线程
     *         to arrive and zero indicates the last to arrive                  //0表示最后到达的线程
     * @throws InterruptedException if the current thread was interrupted
     *         while waiting
     * @throws BrokenBarrierException if <em>another</em> thread was
     *         interrupted or timed out while the current thread was
     *         waiting, or the barrier was reset, or the barrier was
     *         broken when {@code await} was called, or the barrier
     *         action (if present) failed due an exception.
     */
    public int await() throws InterruptedException, BrokenBarrierException {
        try {
            return dowait(false, 0L);   //0表示永不超时,就是没有超时时间
        } catch (TimeoutException toe) {
            throw new Error(toe); // cannot happen;
        }
    }

    /**
     * Waits until all {@linkplain #getParties parties} have invoked
     * <tt>await</tt> on this barrier, or the specified waiting time elapses.
     *
     * <p>If the current thread is not the last to arrive then it is
     * disabled for thread scheduling purposes and lies dormant until
     * one of the following things happens:
     * <ul>
     * <li>The last thread arrives; or
     * <li>The specified timeout elapses; or
     * <li>Some other thread {@linkplain Thread#interrupt interrupts}
     * the current thread; or
     * <li>Some other thread {@linkplain Thread#interrupt interrupts}
     * one of the other waiting threads; or
     * <li>Some other thread times out while waiting for barrier; or
     * <li>Some other thread invokes {@link #reset} on this barrier.
     * </ul>
     *
     * <p>If the current thread:
     * <ul>
     * <li>has its interrupted status set on entry to this method; or
     * <li>is {@linkplain Thread#interrupt interrupted} while waiting
     * </ul>
     * then {@link InterruptedException} is thrown and the current thread's
     * interrupted status is cleared.
     *
     * <p>If the specified waiting time elapses then {@link TimeoutException}
     * is thrown. If the time is less than or equal to zero, the
     * method will not wait at all.
     *
     * <p>If the barrier is {@link #reset} while any thread is waiting,
     * or if the barrier {@linkplain #isBroken is broken} when
     * <tt>await</tt> is invoked, or while any thread is waiting, then
     * {@link BrokenBarrierException} is thrown.
     *
     * <p>If any thread is {@linkplain Thread#interrupt interrupted} while
     * waiting, then all other waiting threads will throw {@link
     * BrokenBarrierException} and the barrier is placed in the broken
     * state.
     *
     * <p>If the current thread is the last thread to arrive, and a
     * non-null barrier action was supplied in the constructor, then the
     * current thread runs the action before allowing the other threads to
     * continue.
     * If an exception occurs during the barrier action then that exception
     * will be propagated in the current thread and the barrier is placed in
     * the broken state.
     *
     * @param timeout the time to wait for the barrier
     * @param unit the time unit of the timeout parameter
     * @return the arrival index of the current thread, where index
     *         <tt>{@link #getParties()} - 1</tt> indicates the first
     *         to arrive and zero indicates the last to arrive
     * @throws InterruptedException if the current thread was interrupted
     *         while waiting
     * @throws TimeoutException if the specified timeout elapses
     * @throws BrokenBarrierException if <em>another</em> thread was
     *         interrupted or timed out while the current thread was
     *         waiting, or the barrier was reset, or the barrier was broken
     *         when {@code await} was called, or the barrier action (if
     *         present) failed due an exception
     */
    public int await(long timeout, TimeUnit unit)
        throws InterruptedException,
               BrokenBarrierException,
               TimeoutException {
        return dowait(true, unit.toNanos(timeout)); //这个方法是由超时时间的
    }

    /**
     * Queries if this barrier is in a broken state.
     *
     * @return {@code true} if one or more parties broke out of this
     *         barrier due to interruption or timeout since
     *         construction or the last reset, or a barrier action
     *         failed due to an exception; {@code false} otherwise.
     */
    public boolean isBroken() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return generation.broken;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Resets the barrier to its initial state.  If any parties are
     * currently waiting at the barrier, they will return with a
     * {@link BrokenBarrierException}. Note that resets <em>after</em>
     * a breakage has occurred for other reasons can be complicated to
     * carry out; threads need to re-synchronize in some other way,
     * and choose one to perform the reset.  It may be preferable to
     * instead create a new barrier for subsequent use.
     */
    public void reset() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            breakBarrier();   // break the current generation
            nextGeneration(); // start a new generation
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns the number of parties currently waiting at the barrier.
     * This method is primarily useful for debugging and assertions.
     *
     * @return the number of parties currently blocked in {@link #await}
     */
    public int getNumberWaiting() {
        final ReentrantLock lock = this.lock;
        lock.lock();
        try {
            return parties - count;
        } finally {
            lock.unlock();
        }
    }
}
