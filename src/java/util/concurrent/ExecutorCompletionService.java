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
 * A {@link CompletionService} that uses a supplied {@link Executor}    // CompletionService使用一个供应商的Executor执行任务,
 * to execute tasks.  This class arranges that submitted tasks are,     // 这个类安排提交了的任务,一旦完成了,
 * upon completion, placed on a queue accessible using {@code take}.    // 放到一个队列中,可以用take方法获取.
 * The class is lightweight enough to be suitable for transient use     // 这个类足够轻量的,适合运行多组任务时
 * when processing groups of tasks.
 *
 * <p>
 *
 * <b>Usage Examples.</b>                                               // 使用例子
 *
 * Suppose you have a set of solvers for a certain problem, each        // 假设你有一个解决者集合用于某个问题
 * returning a value of some type {@code Result}, and would like to     // 每个都返回一个结果类 Result
 * run them concurrently, processing the results of each of them that   // 希望他们并发返回
 * return a non-null value, in some method {@code use(Result r)}. You   // 处理每个result,返回一个非空值
 * could write this as:                                                 // 在方法中使用Result,如下
 *
 * <pre> {@code
 * void solve(Executor e,
 *            Collection<Callable<Result>> solvers)
 *     throws InterruptedException, ExecutionException {
 *     CompletionService<Result> ecs
 *         = new ExecutorCompletionService<Result>(e);
 *     for (Callable<Result> s : solvers)
 *         ecs.submit(s);
 *     int n = solvers.size();
 *     for (int i = 0; i < n; ++i) {
 *         Result r = ecs.take().get();
 *         if (r != null)
 *             use(r);
 *     }
 * }}</pre>
 *
 * Suppose instead that you would like to use the first non-null result // 假如你想使用第一个非空的结果
 * of the set of tasks, ignoring any that encounter exceptions,         // 这种就必须使用这个类了,因为这个类可以做到任务完成了,立马放入到完成任务队列中.
 * and cancelling all other tasks when the first one is ready:
 *
 * <pre> {@code
 * void solve(Executor e,
 *            Collection<Callable<Result>> solvers)
 *     throws InterruptedException {
 *     CompletionService<Result> ecs
 *         = new ExecutorCompletionService<Result>(e);
 *     int n = solvers.size();
 *     List<Future<Result>> futures
 *         = new ArrayList<Future<Result>>(n);
 *     Result result = null;
 *     try {
 *         for (Callable<Result> s : solvers)
 *             futures.add(ecs.submit(s));
 *         for (int i = 0; i < n; ++i) {
 *             try {
 *                 Result r = ecs.take().get();
 *                 if (r != null) {
 *                     result = r;
 *                     break;
 *                 }
 *             } catch (ExecutionException ignore) {}
 *         }
 *     }
 *     finally {
 *         for (Future<Result> f : futures)
 *             f.cancel(true);
 *     }
 *
 *     if (result != null)
 *         use(result);
 * }}</pre>
 */
public class ExecutorCompletionService<V> implements CompletionService<V> {
    private final Executor executor;                        // 用于执行任务的线程池
    private final AbstractExecutorService aes;              // 抽象的执行器
    private final BlockingQueue<Future<V>> completionQueue; // 阻塞队列

    /**
     * FutureTask extension to enqueue upon completion      // 用于完成后立马入队列的FutureTask
     */
    private class QueueingFuture extends FutureTask<Void> { // 这里其实是对FutureTask的扩展, 很多地方都是预留了很多扩展的, 扩展性很好
        QueueingFuture(RunnableFuture<V> task) {
            super(task, null);
            this.task = task;
        }
        protected void done() { completionQueue.add(task); }    // 任务完成后,立马加入到完成队列中, --> 这是这个类的核心功能.
        private final Future<V> task;               // 能做到批量任务完成了,都会把结果加入到队列中,这样就可以获取批量任务的结果了
    }   // 而且这个队列是线程安全的,并且在任务完成了才会添加到这个队列中,如果我们自己写个阻塞队列,Future放到队列中时,可能任务还没完成.

    private RunnableFuture<V> newTaskFor(Callable<V> task) {    // 新建一个RunnableFuture,这是私有方法.
        if (aes == null)
            return new FutureTask<V>(task); // 这里返回的是一个FutureTask
        else
            return aes.newTaskFor(task);    // 这里返回的是一个RunnableFuture
    }

    private RunnableFuture<V> newTaskFor(Runnable task, V result) {
        if (aes == null)
            return new FutureTask<V>(task, result); // 将task和result一起包装进去, 这种是能获取到执行结果result的.
        else
            return aes.newTaskFor(task, result);
    }
    // 这下面的几个方法都是public的, ExecutorCompletionService就是用来提交多个任务,然后获取批量结果的
    /**
     * Creates an ExecutorCompletionService using the supplied  // 使用executor执行任务
     * executor for base task execution and a
     * {@link LinkedBlockingQueue} as a completion queue.       // 使用一个LinkedBlockingQueue作为completion queue
     *
     * @param executor the executor to use
     * @throws NullPointerException if executor is {@code null}
     */
    public ExecutorCompletionService(Executor executor) {
        if (executor == null)
            throw new NullPointerException();
        this.executor = executor;
        this.aes = (executor instanceof AbstractExecutorService) ?
            (AbstractExecutorService) executor : null;
        this.completionQueue = new LinkedBlockingQueue<Future<V>>();
    }

    /**
     * Creates an ExecutorCompletionService using the supplied
     * executor for base task execution and the supplied queue as its
     * completion queue.
     *
     * @param executor the executor to use
     * @param completionQueue the queue to use as the completion queue      // 这个队列用于任务完成队列
     *        normally one dedicated for use by this service. This
     *        queue is treated as unbounded -- failed attempted             // 这个队列没有上限的
     *        {@code Queue.add} operations for completed taskes cause
     *        them not to be retrievable.
     * @throws NullPointerException if executor or completionQueue are {@code null}
     */
    public ExecutorCompletionService(Executor executor,
                                     BlockingQueue<Future<V>> completionQueue) {    // 这个相比较上个构造方法, 就是可以指定阻塞队列
        if (executor == null || completionQueue == null)
            throw new NullPointerException();
        this.executor = executor;
        this.aes = (executor instanceof AbstractExecutorService) ?
            (AbstractExecutorService) executor : null;
        this.completionQueue = completionQueue;
    }

    public Future<V> submit(Callable<V> task) {             // 提交一个带回调的task
        if (task == null) throw new NullPointerException();
        RunnableFuture<V> f = newTaskFor(task);             // 这里是使任务能返回执行结果的关键
        executor.execute(new QueueingFuture(f));            // 执行QueueingFuture
        return f;
    }

    public Future<V> submit(Runnable task, V result) {      // 这个其实和上面是一致的,因为是将task和result组装成了一个RunnableFuture
        if (task == null) throw new NullPointerException();
        RunnableFuture<V> f = newTaskFor(task, result);
        executor.execute(new QueueingFuture(f));
        return f;
    }

    public Future<V> take() throws InterruptedException {   // take方法其实就是从completionQueue里面,阻塞直到获取一个Future
        return completionQueue.take();
    }

    public Future<V> poll() {
        return completionQueue.poll();                      // poll方法是尝试获取一个,删除队列的头结点,立马返回; 如果队列为空, 也会立马返回null
    }

    public Future<V> poll(long timeout, TimeUnit unit)      // 有超时时间的获取
            throws InterruptedException {
        return completionQueue.poll(timeout, unit);
    }

}
